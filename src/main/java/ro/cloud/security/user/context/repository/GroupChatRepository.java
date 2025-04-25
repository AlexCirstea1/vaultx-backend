package ro.cloud.security.user.context.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ro.cloud.security.user.context.model.messaging.GroupChat;

@Repository
public interface GroupChatRepository extends JpaRepository<GroupChat, UUID> {}
