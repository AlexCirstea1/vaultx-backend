package com.vaultx.user.context.service.user;

import com.vaultx.user.context.model.activity.ActivityType;
import com.vaultx.user.context.model.user.User;
import com.vaultx.user.context.model.user.UserBlock;
import com.vaultx.user.context.repository.UserBlockRepository;
import com.vaultx.user.context.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BlockService {

    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;
    private final ActivityService activityService;

    @Transactional
    public void blockUser(UUID blockerId, UUID blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new IllegalArgumentException("Cannot block yourself");
        }

        User blocker = findUser(blockerId);
        User blocked = findUser(blockedId);

        // Check if already blocked
        if (userBlockRepository.existsByBlockerAndBlocked(blocker, blocked)) {
            return; // Already blocked, nothing to do
        }

        // Create a new block relationship
        UserBlock userBlock = UserBlock.builder()
                .blocker(blocker)
                .blocked(blocked)
                .createdAt(Instant.now())
                .build();

        userBlockRepository.save(userBlock);

        activityService.logActivity(
                blocker, ActivityType.USER_ACTION, "Blocked a user", false, "Blocked user: " + blocked.getUsername());
    }

    @Transactional
    public void unblockUser(UUID blockerId, UUID blockedId) {
        User blocker = findUser(blockerId);
        User blocked = findUser(blockedId);

        userBlockRepository.findByBlockerAndBlocked(blocker, blocked).ifPresent(userBlockRepository::delete);

        activityService.logActivity(
                blocker,
                ActivityType.USER_ACTION,
                "Unblocked a user",
                false,
                "Unblocked user: " + blocked.getUsername());
    }

    @Transactional(readOnly = true)
    public boolean isUserBlocked(UUID blockerId, UUID blockedId) {
        User blocker = findUser(blockerId);
        User blocked = findUser(blockedId);

        return userBlockRepository.existsByBlockerAndBlocked(blocker, blocked);
    }

    /*----------  helper  ----------*/
    private User findUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
