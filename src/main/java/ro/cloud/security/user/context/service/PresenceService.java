package ro.cloud.security.user.context.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import ro.cloud.security.user.context.model.user.User;
import ro.cloud.security.user.context.repository.UserRepository;

@Service
public class PresenceService {
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public PresenceService(UserRepository userRepository, SimpMessagingTemplate messagingTemplate) {
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public void markUserOnline(String userIdentifier) {
        try {
            // Try to parse as UUID first (for JWT subject)
            UUID userId = UUID.fromString(userIdentifier);
            markUserOnlineById(userId);
        } catch (IllegalArgumentException e) {
            // Not a UUID, try as username
            markUserOnlineByUsername(userIdentifier);
        }
    }

    public void markUserOffline(String userIdentifier) {
        try {
            // Try to parse as UUID first (for JWT subject)
            UUID userId = UUID.fromString(userIdentifier);
            markUserOfflineById(userId);
        } catch (IllegalArgumentException e) {
            // Not a UUID, try as username
            markUserOfflineByUsername(userIdentifier);
        }
    }

    private void markUserOnlineById(UUID userId) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User with ID " + userId + " not found"));
        user.setOnline(true);
        userRepository.save(user);
        messagingTemplate.convertAndSend("/topic/presence", getUserStatus(user));
    }

    private void markUserOfflineById(UUID userId) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User with ID " + userId + " not found"));
        user.setOnline(false);
        user.setLastSeen(Instant.now());
        userRepository.save(user);
        messagingTemplate.convertAndSend("/topic/presence", getUserStatus(user));
    }

    private void markUserOnlineByUsername(String username) {
        User user =
                userRepository.findUserByUsername(username).orElseThrow(() -> new UsernameNotFoundException(username));
        user.setOnline(true);
        userRepository.save(user);
        messagingTemplate.convertAndSend("/topic/presence", getUserStatus(user));
    }

    private void markUserOfflineByUsername(String username) {
        User user =
                userRepository.findUserByUsername(username).orElseThrow(() -> new UsernameNotFoundException(username));
        user.setOnline(false);
        user.setLastSeen(Instant.now());
        userRepository.save(user);
        messagingTemplate.convertAndSend("/topic/presence", getUserStatus(user));
    }

    public Map<String, Object> getUserStatus(String userIdentifier) {
        User user;
        try {
            UUID userId = UUID.fromString(userIdentifier);
            user = userRepository
                    .findById(userId)
                    .orElseThrow(() -> new UsernameNotFoundException("User with ID " + userId + " not found"));
        } catch (IllegalArgumentException e) {
            user = userRepository
                    .findUserByUsername(userIdentifier)
                    .orElseThrow(() -> new UsernameNotFoundException(userIdentifier));
        }
        return getUserStatus(user);
    }

    private Map<String, Object> getUserStatus(User user) {
        Map<String, Object> status = new HashMap<>();
        status.put("id", user.getId().toString());
        status.put("username", user.getUsername());
        status.put("isOnline", user.isOnline());
        status.put("lastSeen", user.getLastSeen());
        return status;
    }
}
