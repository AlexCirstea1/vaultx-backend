package ro.cloud.security.user.context.service.authentication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import ro.cloud.security.user.context.model.activity.ActivityType;
import ro.cloud.security.user.context.model.authentication.request.LoginDTO;
import ro.cloud.security.user.context.model.authentication.response.LoginResponseDTO;
import ro.cloud.security.user.context.model.authentication.response.UserResponseDTO;
import ro.cloud.security.user.context.model.user.User;
import ro.cloud.security.user.context.repository.UserRepository;
import ro.cloud.security.user.context.service.ActivityService;
import ro.cloud.security.user.context.utils.Utils;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginService {
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final ModelMapper mapper;
    private final ObjectMapper objectMapper;
    private final ActivityService activityService;

    public LoginResponseDTO loginUser(HttpServletRequest request, LoginDTO dto) {
        try {
            User user = userRepository
                    .findUserByUsername(dto.getUsername())
                    .orElseThrow(() -> new UsernameNotFoundException("Account not found"));

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

            user.setLastSeen(Instant.now());
            userRepository.save(user);

            // Log successful login activity
            activityService.logActivity(
                    user,
                    ActivityType.LOGIN,
                    "Successful login",
                    false,
                    "IP: " + Utils.getClientIpAddress(request) + ", Device: " + request.getHeader("User-Agent"));

            UserResponseDTO userResponseDTO = mapper.map(user, UserResponseDTO.class);
            userResponseDTO.setHasPin(user.getPin() != null);
            return new LoginResponseDTO(userResponseDTO, accessToken, refreshToken);

        } catch (AuthenticationException e) {
            if (userRepository.findUserByUsername(dto.getUsername()).isPresent()) {
                User user = userRepository.findUserByUsername(dto.getUsername()).get();

                // Check for multiple failures
                int recentFailures = activityService.countRecentActivities(
                        user.getId(), ActivityType.LOGIN, true, 30); // Last 30 minutes

                // Log suspicious activity if multiple failures
                if (recentFailures >= 3) {
                    activityService.logActivity(
                            user,
                            ActivityType.SECURITY,
                            "Suspicious login activity",
                            true,
                            "Multiple failed login attempts (" + (recentFailures + 1) + ") from IP: "
                                    + Utils.getClientIpAddress(request));
                } else {
                    // Regular failed login activity (already implemented)
                    activityService.logActivity(
                            user,
                            ActivityType.LOGIN,
                            "Failed login attempt",
                            true,
                            "IP: " + Utils.getClientIpAddress(request) + ", Device: " + request.getHeader("User-Agent"));
                }
            }
            throw new BadCredentialsException(e.getMessage());
        }
    }

    public LoginResponseDTO refreshToken(HttpServletRequest request, String refreshTokenJson) {
        try {
            JsonNode jsonNode = objectMapper.readTree(refreshTokenJson);
            String refreshToken = jsonNode.get("refresh_token").asText();

            User user = tokenService.validateRefreshToken(refreshToken);
            user.setLastSeen(Instant.now());
            userRepository.save(user);

            Authentication auth =
                    new UsernamePasswordAuthenticationToken(user.getUsername(), null, user.getAuthorities());

            String newAccessToken = tokenService.generateJwt(auth, user);
            String newRefreshToken = tokenService.generateRefreshToken(user);

            tokenService.storeUserSession(
                    user,
                    newAccessToken,
                    newRefreshToken,
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"),
                    Instant.now());

            activityService.logActivity(
                    user,
                    ActivityType.LOGIN,
                    "Access token refreshed",
                    false,
                    "IP: " + Utils.getClientIpAddress(request) + ", Device: " + request.getHeader("User-Agent"));

            UserResponseDTO userResponseDTO = mapper.map(user, UserResponseDTO.class);
            userResponseDTO.setHasPin(user.getPin() != null);
            return new LoginResponseDTO(userResponseDTO, newAccessToken, newRefreshToken);
        } catch (IOException e) {
            throw new RuntimeException("Invalid refresh token JSON", e);
        }
    }

    public void logout(HttpServletRequest request, UserService userService) {
        User user = userService.getSessionUser(request);
        user.setRefreshToken(null);
        userRepository.save(user);
        tokenService.removeUserSession(user.getId().toString());

        activityService.logActivity(
                user,
                ActivityType.LOGIN,
                "User logged out",
                false,
                "IP: " + Utils.getClientIpAddress(request) + ", Device: " + request.getHeader("User-Agent"));
    }

    public boolean verifyToken(String token) {
        return tokenService.verifyToken(token);
    }
}
