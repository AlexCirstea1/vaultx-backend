package com.vaultx.user.context.service.user;

import com.vaultx.user.context.model.PublicKeyResponse;
import com.vaultx.user.context.model.activity.ActivityType;
import com.vaultx.user.context.model.didEvent.EventType;
import com.vaultx.user.context.model.user.User;
import com.vaultx.user.context.model.user.UserKeyHistory;
import com.vaultx.user.context.repository.UserKeyHistoryRepository;
import com.vaultx.user.context.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeyManagementService {

    private final UserRepository userRepository;
    private final UserKeyHistoryRepository userKeyHistoryRepository;
    private final SessionService sessionService;
    private final ActivityService activityService;
    private final BlockchainService blockchainService;

    public ResponseEntity<PublicKeyResponse> getUserPublicKey(UUID id) {
        try {
            User user = userRepository.findById(id).orElseThrow(() -> new UsernameNotFoundException("User not found"));

            PublicKeyResponse response = PublicKeyResponse.builder()
                    .publicKey(user.getPublicKey())
                    .version(user.getCurrentKeyVersion())
                    .build();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    public ResponseEntity<String> savePublicKey(HttpServletRequest request, String publicKey) {
        if (publicKey == null || publicKey.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Public key is required.");
        }

        var version = saveUserPublicKey(request, publicKey.trim());
        return ResponseEntity.ok(version);
    }

    private String saveUserPublicKey(HttpServletRequest request, String publicKey) {
        User user = sessionService.getSessionUser(request);
        boolean isRotation =
                (user.getPublicKey() != null && !user.getPublicKey().trim().isEmpty());

        String oldVersion = user.getCurrentKeyVersion();

        if (isRotation) {
            // Save the old key into history
            UserKeyHistory keyHistory = new UserKeyHistory();
            keyHistory.setUserId(user.getId().toString());
            keyHistory.setKeyVersion(user.getCurrentKeyVersion());
            keyHistory.setPublicKey(user.getPublicKey());
            userKeyHistoryRepository.save(keyHistory);

            // Generate the new key version
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

    private String generateNextKeyVersion(String currentVersion) {
        int nextVersionNumber = 1;
        try {
            if (currentVersion != null && currentVersion.startsWith("v")) {
                int current = Integer.parseInt(currentVersion.substring(1));
                nextVersionNumber = current + 1;
            }
        } catch (NumberFormatException e) {
            // fallback to v1 if the current version is not parsable
        }
        return "v" + nextVersionNumber;
    }
}
