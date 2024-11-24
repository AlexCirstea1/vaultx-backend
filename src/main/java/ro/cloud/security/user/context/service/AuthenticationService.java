package ro.cloud.security.user.context.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
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

import java.io.IOException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

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

            if (Objects.requireNonNull(Objects.requireNonNull(jwt.getExpiresAt())).isBefore(Instant.now())) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public UserResponseDTO registerUser(RegistrationDTO dto) {
        var encodedPassword = passwordEncoder.encode(dto.getPassword());
        var userRole = roleRepository.findByAuthority("USER").orElseThrow();

        if(userRepository.findUserByUsername(dto.getUsername()).isPresent()
            || userRepository.findUserByEmail(dto.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("Try a different username or password");
        }

        Set<Role> roles = new HashSet<>();
        roles.add(userRole);

        var user = userRepository.save(
                User.builder()
                        .username(dto.getUsername())
                        .email(dto.getEmail())
                        .password(encodedPassword)
                        .authorities(roles)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build()
        );
        UserResponseDTO userResponseDTO = mapper.map(user, UserResponseDTO.class);
        userResponseDTO.setHasPin(user.getPin() != null);
        return userResponseDTO;
    }

    public LoginResponseDTO loginUser(HttpServletRequest request, LoginDTO dto) {
        try {
            var user = userRepository.findUserByUsername(dto.getUsername()).orElseThrow(()-> new UsernameNotFoundException("Account not found"));
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.getUsername(), dto.getPassword())
            );
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

    public LoginResponseDTO refreshToken(HttpServletRequest request,String refreshTokenJson) {
        try {
            JsonNode jsonNode = objectMapper.readTree(refreshTokenJson);
            String refreshToken = jsonNode.get("refresh_token").asText();

            var user = tokenService.validateRefreshToken(refreshToken);
            user.setLastAccessAt(Instant.now());
            userRepository.save(user);

            Authentication auth = new UsernamePasswordAuthenticationToken(
                    user.getUsername(),
                    null,
                    user.getAuthorities()
            );

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
            return true;
        } else {
            throw new CustomBadCredentialsException("Invalid PIN");
        }
    }
}