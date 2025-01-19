package ro.cloud.security.user.context.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javafaker.Faker;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.cloud.security.user.context.exception.CustomBadCredentialsException;
import ro.cloud.security.user.context.exception.UserAlreadyExistsException;
import ro.cloud.security.user.context.model.Role;
import ro.cloud.security.user.context.model.User;
import ro.cloud.security.user.context.model.dto.LoginDTO;
import ro.cloud.security.user.context.model.dto.LoginResponseDTO;
import ro.cloud.security.user.context.model.dto.RegistrationDTO;
import ro.cloud.security.user.context.model.dto.UserResponseDTO;
import ro.cloud.security.user.context.repository.RoleRepository;
import ro.cloud.security.user.context.repository.UserRepository;
import ro.cloud.security.user.context.utils.CipherUtils;
import ro.cloud.security.user.context.utils.KeyGeneratorUtility;

@Service
@Transactional
@AllArgsConstructor
public class AuthenticationService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final ModelMapper mapper;
    private final JwtDecoder jwtDecoder;
    private final RedisTemplate<String, Object> redisTemplate;

    public boolean verifyToken(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            return !Objects.requireNonNull(Objects.requireNonNull(jwt.getExpiresAt()))
                    .isBefore(Instant.now());
        } catch (Exception e) {
            return false;
        }
    }

    public UserResponseDTO registerUser(RegistrationDTO dto) {
        var encodedPassword = passwordEncoder.encode(dto.getPassword());

        if (userRepository.findUserByUsername(dto.getUsername()).isPresent()
                || userRepository.findUserByEmail(dto.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("Try a different username or password");
        }

        try {
            return createUser(dto.getUsername(), dto.getEmail(), encodedPassword);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public UserResponseDTO registerRandomUser(String password) {
        Faker faker = new Faker();
        try {
            return createUser(
                    generateUsername(),
                    "%s@vaultx.net".formatted(faker.number().digits(8)),
                    passwordEncoder.encode(password));

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String generateUsername() {
        Faker faker = new Faker();
        String adjective = faker.color().name(); // Example: "Misty"
        String animal = faker.animal().name(); // Example: "Phoenix"

        // Combine components
        return capitalize(adjective) + capitalize(animal) + faker.number().digits(4);
    }

    private static String capitalize(String word) {
        return word.substring(0, 1).toUpperCase() + word.substring(1);
    }

    private void sendToBlockchainService(String username, PublicKey publicKey) {
        // Mock sending to blockchain microservice (use Kafka, REST, or another protocol)
        System.out.println("Sending to blockchain service: " + username + ", Public Key: " + publicKey);
    }

    private UserResponseDTO createUser(String username, String email, String password) throws NoSuchAlgorithmException {
        var userRole = roleRepository.findByAuthority("USER").orElseThrow();

        KeyPair keyPair = KeyGeneratorUtility.generateRSAKey();
        Set<Role> roles = new HashSet<>();
        roles.add(userRole);

        var user = userRepository.save(User.builder()
                .username(username)
                .email(email)
                .password(password)
                .authorities(roles)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .publicKey(
                        Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()))
                .build());

        sendToBlockchainService(username, keyPair.getPublic());

        UserResponseDTO userResponseDTO = mapper.map(user, UserResponseDTO.class);
        userResponseDTO.setHasPin(user.getPin() != null);
        return userResponseDTO;
    }

    public LoginResponseDTO loginUser(HttpServletRequest request, LoginDTO dto) {
        try {
            var user = userRepository
                    .findUserByUsername(dto.getUsername())
                    .orElseThrow(() -> new UsernameNotFoundException("Account not found"));

            //            if (redisTemplate.hasKey(user.getId().toString())) {
            //                throw new CustomBadCredentialsException("User is already logged in");
            //            }

            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.getUsername(), dto.getPassword()));
            String accessToken = tokenService.generateJwt(auth, user);
            String refreshToken = tokenService.generateRefreshToken(user);

            tokenService.storeUserSession(
                    user,
                    accessToken,
                    refreshToken,
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"),
                    Instant.now());

            user.setLastAccessAt(Instant.now());
            userRepository.save(user);

            UserResponseDTO userResponseDTO = mapper.map(user, UserResponseDTO.class);
            userResponseDTO.setHasPin(user.getPin() != null);
            return new LoginResponseDTO(userResponseDTO, accessToken, refreshToken);

        } catch (AuthenticationException e) {
            throw new BadCredentialsException(e.getMessage());
        }
    }

    public LoginResponseDTO refreshToken(HttpServletRequest request, String refreshTokenJson) {
        try {
            JsonNode jsonNode = objectMapper.readTree(refreshTokenJson);
            String refreshToken = jsonNode.get("refresh_token").asText();

            var user = tokenService.validateRefreshToken(refreshToken);
            user.setLastAccessAt(Instant.now());
            userRepository.save(user);

            Authentication auth =
                    new UsernamePasswordAuthenticationToken(user.getUsername(), null, user.getAuthorities());

            var newAccessToken = tokenService.generateJwt(auth, user);
            var newRefreshToken = tokenService.generateRefreshToken(user);

            tokenService.storeUserSession(
                    user,
                    newAccessToken,
                    newRefreshToken,
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"),
                    Instant.now());

            UserResponseDTO userResponseDTO = mapper.map(user, UserResponseDTO.class);
            userResponseDTO.setHasPin(user.getPin() != null);
            return new LoginResponseDTO(userResponseDTO, newAccessToken, newRefreshToken);
        } catch (IOException e) {
            throw new RuntimeException("Invalid refresh token JSON", e);
        }
    }

    public void logout(HttpServletRequest request) {
        var user = userService.getSessionUser(request);
        user.setRefreshToken(null);
        userRepository.save(user);

        redisTemplate.delete(user.getId().toString());
    }

    public void savePin(HttpServletRequest request, String pin) {
        if (pin.length() != 6) {
            throw new CustomBadCredentialsException("PIN must be exactly 6 digits");
        }

        var user = userService.getSessionUser(request);
        var hashedPin = CipherUtils.getHash(pin);

        user.setPin(hashedPin);
        userRepository.save(user);
    }

    public Boolean verifyPin(HttpServletRequest request, String pin) {
        if (pin.length() != 6) {
            throw new CustomBadCredentialsException("PIN must be exactly 6 digits");
        }

        var user = userService.getSessionUser(request);
        var hashedPin = CipherUtils.getHash(pin);

        if (Objects.equals(hashedPin, user.getPin())) {
            return Boolean.TRUE;
        } else {
            throw new CustomBadCredentialsException("Invalid PIN");
        }
    }
}
