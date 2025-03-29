package ro.cloud.security.user.context.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ro.cloud.security.user.context.model.activity.Activity;
import ro.cloud.security.user.context.model.activity.ActivityResponseDTO;
import ro.cloud.security.user.context.model.activity.ActivityType;
import ro.cloud.security.user.context.model.user.User;
import ro.cloud.security.user.context.repository.ActivityRepository;

@Service
@AllArgsConstructor
@Slf4j
public class ActivityService {
    private final ActivityRepository activityRepository;

    public void logActivity(User user, ActivityType type, String description, boolean isUnusual, String details) {
        Activity activity = Activity.builder()
                .id("act_" + UUID.randomUUID().toString().replace("-", ""))
                .user(user)
                .type(type)
                .description(description)
                .timestamp(Instant.now())
                .isUnusual(isUnusual)
                .details(details)
                .build();

        activityRepository.save(activity);
        log.info("Activity logged: {} for user {}", type, user.getId());
    }

    public List<ActivityResponseDTO> getUserActivities(String type, User user) {
        return activityRepository.findByUserOrderByTimestampDesc(user).stream()
                .map(this::mapToDto)
                .filter(activity -> "all".equalsIgnoreCase(type) || type.equalsIgnoreCase(activity.getType()))
                .collect(Collectors.toList());
    }

    private ActivityResponseDTO mapToDto(Activity activity) {
        return ActivityResponseDTO.builder()
                .id(activity.getId())
                .type(activity.getType().toString().toLowerCase())
                .description(activity.getDescription())
                .timestamp(activity.getTimestamp())
                .isUnusual(activity.isUnusual())
                .details(activity.getDetails())
                .build();
    }
}
