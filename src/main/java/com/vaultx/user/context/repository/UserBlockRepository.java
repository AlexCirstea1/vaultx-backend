package com.vaultx.user.context.repository;

import com.vaultx.user.context.model.user.User;
import com.vaultx.user.context.model.user.UserBlock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserBlockRepository extends JpaRepository<UserBlock, UUID> {
    Optional<UserBlock> findByBlockerAndBlocked(User blocker, User blocked);

    boolean existsByBlockerAndBlocked(User blocker, User blocked);
}
