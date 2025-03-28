package ro.cloud.security.user.context.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ro.cloud.security.user.context.model.activity.Activity;
import ro.cloud.security.user.context.model.user.User;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, String> {
    List<Activity> findByUserOrderByTimestampDesc(User user);
}
