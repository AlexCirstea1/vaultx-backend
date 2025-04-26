package com.vaultx.user.context.service.authentication;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import com.vaultx.user.context.model.EventType;
import com.vaultx.user.context.model.PublicKeyResponse;
import com.vaultx.user.context.model.activity.ActivityType;
import com.vaultx.user.context.model.authentication.response.UserResponseDTO;
import com.vaultx.user.context.model.authentication.response.UserSearchDTO;
import com.vaultx.user.context.model.user.User;
import com.vaultx.user.context.model.user.UserKeyHistory;
import com.vaultx.user.context.repository.UserKeyHistoryRepository;
import com.vaultx.user.context.repository.UserRepository;
import com.vaultx.user.context.service.ActivityService;
import com.vaultx.user.context.service.BlockchainService;

@Slf4j
@Service
@AllArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final ModelMapper mapper;
    private final JwtDecoder jwtDecoder;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserKeyHistoryRepository userKeyHistoryRepository;
    private final ActivityService activityService;
    private final BlockchainService blockchainService;
    private final ModelMapper modelMapper;

    public User getUserById(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    public User getSessionUser(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }

        String tokenValue = authHeader.substring(7);
        Jwt access_token = jwtDecoder.decode(tokenValue);
        return userRepository
                .findById(UUID.fromString(access_token.getSubject()))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository
                .findUserByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    public UserResponseDTO getUser(HttpServletRequest request) {
        var user = getSessionUser(request);
        var userDto = mapper.map(user, UserResponseDTO.class);
        userDto.setHasPin(user.getPin() != null);
        return userDto;
    }

    public UserResponseDTO getUserData(String id) {
        var user = getUserById(UUID.fromString(id));
        var userDto = mapper.map(user, UserResponseDTO.class);
        userDto.setHasPin(user.getPin() != null);
        return userDto;
    }

    public String deleteUser(HttpServletRequest request) {
        var user = getSessionUser(request);
        userRepository.delete(user);
        redisTemplate.delete(user.getId().toString());
        return "User and associated data deleted successfully";
    }

    /**
     * Search users by username containing the query string, case-insensitive, excluding the current user.
     *
     * @param query The search query string.
     * @param currentUserId The ID of the current user to exclude from results.
     * @return List of UserSearchDTO matching the query.
     */
    public List<UserSearchDTO> searchUsers(String query, String currentUserId) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        // Limit the number of results to prevent abuse
        List<User> users = userRepository.findTop10ByUsernameContainingIgnoreCase(query.trim());

        // Exclude the current user
        return users.stream()
                .filter(user -> !user.getId().toString().equals(currentUserId))
                .map(user -> new UserSearchDTO(user.getId(), user.getUsername()))
                .collect(Collectors.toList());
    }

    public PublicKeyResponse getUserPublicKey(UUID userId) {
        User user = getUserById(userId);
        return PublicKeyResponse.builder()
                .publicKey(user.getPublicKey())
                .version(user.getCurrentKeyVersion())
                .build();
    }

    public String saveUserPublicKey(HttpServletRequest request, String publicKey) {
        User user = getSessionUser(request);
        boolean isRotation =
                (user.getPublicKey() != null && !user.getPublicKey().trim().isEmpty());

        String oldVersion = user.getCurrentKeyVersion(); // Store old version for payload

        if (isRotation) {
            // Save the old key into history
            UserKeyHistory keyHistory = new UserKeyHistory();
            keyHistory.setUserId(user.getId().toString());
            keyHistory.setKeyVersion(user.getCurrentKeyVersion());
            keyHistory.setPublicKey(user.getPublicKey());
            userKeyHistoryRepository.save(keyHistory);

            // Generate the new key version (e.g., increment a version number)
            String newVersion = generateNextKeyVersion(user.getCurrentKeyVersion());
            user.setPublicKey(publicKey.trim());
            user.setCurrentKeyVersion(newVersion);

            // Log key rotation activity
            activityService.logActivity(
                    user,
                    ActivityType.KEY,
                    "Encryption keys rotated",
                    false,
                    "From version: " + oldVersion + " to version: " + newVersion);

            // Record key rotation to blockchain via Kafka
            Map<String, Object> rotationInfo = new HashMap<>();
            rotationInfo.put("userId", user.getId().toString());
            rotationInfo.put("oldVersion", oldVersion);
            rotationInfo.put("newVersion", user.getCurrentKeyVersion());
            rotationInfo.put("timestamp", LocalDateTime.now().toString());

            if (user.isBlockchainConsent()) {
                blockchainService.recordDIDEvent(user, EventType.USER_KEY_ROTATED, rotationInfo);
            }
        } else {
            // First-time registration of the public key
            user.setPublicKey(publicKey.trim());
            user.setCurrentKeyVersion("v1");

            // Log initial key setup
            activityService.logActivity(user, ActivityType.KEY, "Initial encryption key setup", false, "Version: v1");
        }
        userRepository.save(user);

        String message = isRotation ? "Public key rotated successfully." : "Public key registered successfully.";
        log.info(message);

        return user.getCurrentKeyVersion();
    }

    public void setConsent(boolean consent, User user) {
        boolean previousConsent = user.isBlockchainConsent();
        user.setBlockchainConsent(consent);
        userRepository.save(user);

        // Log event on blockchain
        blockchainService.recordDIDEvent(user, EventType.USER_REGISTERED, modelMapper.map(user, UserResponseDTO.class));

        // Log consent change
        activityService.logActivity(
                user,
                ActivityType.CONSENT,
                "Data sharing preferences updated",
                false,
                "Blockchain consent changed from " + previousConsent + " to " + consent);
    }

    private String generateNextKeyVersion(String currentVersion) {
        // For example, assume versions are "v1", "v2", etc.
        int nextVersionNumber = 1;
        try {
            if (currentVersion != null && currentVersion.startsWith("v")) {
                int current = Integer.parseInt(currentVersion.substring(1));
                nextVersionNumber = current + 1;
            }
        } catch (NumberFormatException e) {
            // fallback to v1 if current version is not parsable
        }
        return "v" + nextVersionNumber;
    }
}
