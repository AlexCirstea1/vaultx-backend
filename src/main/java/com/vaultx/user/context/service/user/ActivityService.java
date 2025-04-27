package com.vaultx.user.context.service.user;

import com.vaultx.user.context.model.activity.*;
import com.vaultx.user.context.model.user.User;
import com.vaultx.user.context.repository.ActivityRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityService {

    private final ActivityRepository repository;

    public void logActivity(User user, ActivityType type, String desc, boolean unusual, String details) {

        repository.save(Activity.builder()
                .id("act_" + UUID.randomUUID().toString().replace("-", ""))
                .user(user)
                .type(type)
                .description(desc)
                .timestamp(Instant.now())
                .isUnusual(unusual)
                .details(details)
                .build());

        log.info("Activity logged: {} for user {}", type, user.getId());
    }

    @Transactional(readOnly = true)
    public List<ActivityResponseDTO> getUserActivities(String type, User user) {
        return repository.findByUserOrderByTimestampDesc(user).stream()
                .map(this::toDto)
                .filter(dto -> "all".equalsIgnoreCase(type) || type.equalsIgnoreCase(dto.getType()))
                .collect(Collectors.toList());
    }

    public int countRecentActivities(UUID userId, ActivityType type, boolean unusual, Duration within) {
        Instant cutoff = Instant.now().minus(within);
        return repository.countByUserIdAndTypeAndIsUnusualAndTimestampAfter(userId, type, unusual, cutoff);
    }

    /*----------  mapper  ----------*/
    private ActivityResponseDTO toDto(Activity act) {
        return ActivityResponseDTO.builder()
                .id(act.getId())
                .type(act.getType().name().toLowerCase())
                .description(act.getDescription())
                .timestamp(act.getTimestamp())
                .isUnusual(act.isUnusual())
                .details(act.getDetails())
                .build();
    }
}
