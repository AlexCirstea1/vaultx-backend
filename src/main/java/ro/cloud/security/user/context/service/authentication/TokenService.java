package ro.cloud.security.user.context.service.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import ro.cloud.security.user.context.model.authentication.response.LoginResponseDTO;
import ro.cloud.security.user.context.model.user.User;
import ro.cloud.security.user.context.model.user.UserSession;
import ro.cloud.security.user.context.repository.UserRepository;
import ro.cloud.security.user.context.utils.RSAKeyProperties;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final RSAKeyProperties rsaKeyProperties;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ModelMapper modelMapper;
    private final ObjectMapper objectMapper;

    @Value("${jwt.ttlInMinutes}")
    private int ttlInMinutes;

    @Value("${jwt.refreshTtlInMinutes}")
    private int refreshTtlInMinutes;

    public String generateJwt(Authentication auth, User user) {
        Instant now = Instant.now();
        var expirationDateTime =
                Date.from(ZonedDateTime.now().plusMinutes(ttlInMinutes).toInstant());

        String scope = "";
        if (auth != null) {
            scope = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.joining(" "));
        }

        var publicKey = rsaKeyProperties.getPublicKey();

        // Convert RSA public key to string representation
        String publicKeyString = Base64.getEncoder().encodeToString(publicKey.getEncoded());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("self")
                .issuedAt(now)
                .expiresAt(expirationDateTime.toInstant())
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("role", scope)
                .claim("publicKey", publicKeyString)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        var expirationDateTime =
                Date.from(ZonedDateTime.now().plusMinutes(refreshTtlInMinutes).toInstant());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("self")
                .issuedAt(now)
                .expiresAt(expirationDateTime.toInstant())
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .build();

        String refreshToken =
                jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        user.setRefreshToken(refreshToken);
        userRepository.save(user);
        return refreshToken;
    }

    public User validateRefreshToken(String token) {
        try {
            log.info("Validating refresh token: {}", token);
            Jwt jwt = jwtDecoder.decode(token);
            String userId = jwt.getSubject();
            UserSession userSession = getUserSession(userId);

            if (userSession == null || !token.equals(userSession.getUser().getRefreshToken())) {
                throw new RuntimeException("Invalid refresh token: Token does not match the stored token");
            }

            return userRepository
                    .findById(userSession.getUser().getUser().getId())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        } catch (JwtException e) {
            log.error("Invalid refresh token: {}", e.getMessage());
            throw new RuntimeException("Invalid refresh token", e);
        }
    }

    public void storeUserSession(
            User user, String accessToken, String refreshToken, String clientIp, String userAgent, Instant updatedAt) {
        var userdto = modelMapper.map(user, LoginResponseDTO.class);
        userdto.setAccessToken(accessToken);
        userdto.setRefreshToken(refreshToken);

        // Retrieve existing UserSession from Redis
        UserSession existingSession = getUserSession(user.getId().toString());

        // Retain the existing created_at value if the session exists
        Instant createdAt = (existingSession != null) ? existingSession.getCreatedAt() : Instant.now();

        var userSession = UserSession.builder()
                .user(userdto)
                .userAgent(userAgent)
                .clientIp(clientIp)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();

        redisTemplate.opsForValue().set(user.getId().toString(), userSession, ttlInMinutes, TimeUnit.DAYS);
    }

    public UserSession getUserSession(String userId) {
        Object sessionObject = redisTemplate.opsForValue().get(userId);
        switch (sessionObject) {
            case null -> {
                log.warn("No session found for user ID: {} --> Skipping...", userId);
                return null;
            }
                // Explicitly cast or deserialize the object into UserSession
            case LinkedHashMap linkedHashMap -> {
                // Manually map LinkedHashMap to UserSession if needed
                return objectMapper.convertValue(sessionObject, UserSession.class);
                // Manually map LinkedHashMap to UserSession if needed
            }
            case UserSession userSession -> {
                return userSession;
            }
            default -> {
                log.error(
                        "Invalid session type for user ID: {}. Expected UserSession but found: {}",
                        userId,
                        sessionObject.getClass());
                return null;
            }
        }
    }

    public boolean verifyToken(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            return !jwt.getExpiresAt().isBefore(Instant.now());
        } catch (Exception e) {
            return false;
        }
    }

    public void removeUserSession(String userId) {
        redisTemplate.delete(userId);
    }
}
