package ro.cloud.security.user.context.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.cloud.security.user.context.model.messaging.GroupChat;

import java.util.UUID;

public interface GroupChatRepository extends JpaRepository<GroupChat, UUID> {
}
