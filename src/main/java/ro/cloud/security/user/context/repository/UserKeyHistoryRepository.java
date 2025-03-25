package ro.cloud.security.user.context.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ro.cloud.security.user.context.model.user.UserKeyHistory;

import java.util.UUID;

@Repository
public interface UserKeyHistoryRepository extends JpaRepository<UserKeyHistory, UUID> { }
