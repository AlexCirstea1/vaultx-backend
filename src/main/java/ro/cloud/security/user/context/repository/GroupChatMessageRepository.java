package ro.cloud.security.user.context.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.cloud.security.user.context.model.messaging.GroupChatMessage;

import java.util.List;
import java.util.UUID;

public interface GroupChatMessageRepository extends JpaRepository<GroupChatMessage, UUID> {
    List<GroupChatMessage> findByGroupIdOrderByTimestampAsc(UUID groupId);
}
