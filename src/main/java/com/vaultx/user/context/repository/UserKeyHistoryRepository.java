package com.vaultx.user.context.repository;

import com.vaultx.user.context.model.user.UserKeyHistory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserKeyHistoryRepository extends JpaRepository<UserKeyHistory, UUID> {}
