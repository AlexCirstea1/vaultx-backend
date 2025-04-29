package com.vaultx.user.context.service.user;

import com.vaultx.user.context.model.activity.ActivityType;
import com.vaultx.user.context.model.user.User;
import com.vaultx.user.context.repository.UserRepository;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BlockService {

    private final UserRepository userRepository;
    private final ActivityService activityService;

    @Transactional
    public void blockUser(UUID blockerId, UUID blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new IllegalArgumentException("Cannot block yourself");
        }

        User blocker = findUser(blockerId);
        User blocked = findUser(blockedId);

        // Create a new HashSet with the existing blocked users
        Set<User> updatedBlockedUsers = new HashSet<>(blocker.getBlockedUsers());

        // Add the new blocked user to this copy
        boolean added = updatedBlockedUsers.add(blocked);

        if (added) {
            // Update the reference in the entity
            blocker.setBlockedUsers(updatedBlockedUsers);

            activityService.logActivity(
                    blocker,
                    ActivityType.USER_ACTION,
                    "Blocked a user",
                    false,
                    "Blocked user: " + blocked.getUsername());

            userRepository.save(blocker);
        }
    }

    @Transactional
    public void unblockUser(UUID blockerId, UUID blockedId) {
        User blocker = findUser(blockerId);
        if (blocker.getBlockedUsers().remove(findUser(blockedId))) {
            userRepository.save(blocker);
        }
    }

    @Transactional(readOnly = true)
    public boolean isUserBlocked(UUID blockerId, UUID blockedId) {
        return findUser(blockerId).getBlockedUsers().contains(findUser(blockedId));
    }

    /*----------  helper  ----------*/
    private User findUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
