package com.vaultx.user.context.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.vaultx.user.context.model.activity.Activity;
import com.vaultx.user.context.model.activity.ActivityType;
import com.vaultx.user.context.model.user.User;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, String> {
    List<Activity> findByUserOrderByTimestampDesc(User user);

    int countByUserIdAndTypeAndIsUnusualAndTimestampAfter(
            UUID userId, ActivityType type, boolean isUnusual, Instant timestamp);
}
