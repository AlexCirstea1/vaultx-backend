package ro.cloud.security.user.context.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ro.cloud.security.user.context.model.user.UserKeyHistory;

@Repository
public interface UserKeyHistoryRepository extends JpaRepository<UserKeyHistory, UUID> {}
