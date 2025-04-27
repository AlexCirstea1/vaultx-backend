package com.vaultx.user.context.repository;

import com.vaultx.user.context.model.messaging.GroupChatMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupChatMessageRepository extends JpaRepository<GroupChatMessage, UUID> {
    List<GroupChatMessage> findByGroupIdOrderByTimestampAsc(UUID groupId);
}
