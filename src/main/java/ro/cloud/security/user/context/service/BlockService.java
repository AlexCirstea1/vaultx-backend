package ro.cloud.security.user.context.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import ro.cloud.security.user.context.model.user.User;
import ro.cloud.security.user.context.repository.UserRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BlockService {

    private final UserRepository userRepository;

    public void blockUser(UUID blockerId, UUID blockedId) {
        User blocker = userRepository.findById(blockerId)
                .orElseThrow(() -> new UsernameNotFoundException("Blocker not found"));
        User blocked = userRepository.findById(blockedId)
                .orElseThrow(() -> new UsernameNotFoundException("Blocked user not found"));
        if (!blocker.getBlockedUsers().contains(blocked)) {
            blocker.getBlockedUsers().add(blocked);
            userRepository.save(blocker);
        }
    }

    public void unblockUser(UUID blockerId, UUID blockedId) {
        User blocker = userRepository.findById(blockerId)
                .orElseThrow(() -> new UsernameNotFoundException("Blocker not found"));
        User blocked = userRepository.findById(blockedId)
                .orElseThrow(() -> new UsernameNotFoundException("Blocked user not found"));
        if (blocker.getBlockedUsers().contains(blocked)) {
            blocker.getBlockedUsers().remove(blocked);
            userRepository.save(blocker);
        }
    }

    public boolean isUserBlocked(UUID blockerId, UUID blockedId) {
        User blocker = userRepository.findById(blockerId)
                .orElseThrow(() -> new UsernameNotFoundException("Blocker not found"));
        User blocked = userRepository.findById(blockedId)
                .orElseThrow(() -> new UsernameNotFoundException("Blocked user not found"));
        return blocker.getBlockedUsers().contains(blocked);
    }
}

