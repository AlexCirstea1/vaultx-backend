package ro.cloud.security.user.context.service.authentication;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
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
import ro.cloud.security.user.context.model.authentication.response.UserResponseDTO;
import ro.cloud.security.user.context.model.authentication.response.UserSearchDTO;
import ro.cloud.security.user.context.model.user.User;
import ro.cloud.security.user.context.model.user.UserKeyHistory;
import ro.cloud.security.user.context.repository.UserKeyHistoryRepository;
import ro.cloud.security.user.context.repository.UserRepository;

@Slf4j
@Service
@AllArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final ModelMapper mapper;
    private final JwtDecoder jwtDecoder;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserKeyHistoryRepository userKeyHistoryRepository;

    public User getUserById(UUID id) {
        return userRepository.findById(id).orElseThrow();
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

    public String getUserPublicKey(UUID userId) {
        User user = getUserById(userId);
        return user.getPublicKey();
    }

    public String saveUserPublicKey(HttpServletRequest request, String publicKey) {
        User user = getSessionUser(request);
        boolean isRotation = (user.getPublicKey() != null && !user.getPublicKey().trim().isEmpty());

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
        } else {
            // First-time registration of the public key
            user.setPublicKey(publicKey.trim());
            user.setCurrentKeyVersion("v1");
        }
        userRepository.save(user);

        String message = isRotation ? "Public key rotated successfully." : "Public key registered successfully.";
        log.info(message);

        return user.getPublicKey();
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
