package ro.cloud.security.user.context.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ro.cloud.security.user.context.model.messaging.GroupChatMessage;

public interface GroupChatMessageRepository extends JpaRepository<GroupChatMessage, UUID> {
    List<GroupChatMessage> findByGroupIdOrderByTimestampAsc(UUID groupId);
}
