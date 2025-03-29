package ro.cloud.security.user.context.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ro.cloud.security.user.context.model.messaging.ChatRequest;

public interface ChatRequestRepository extends JpaRepository<ChatRequest, UUID> {
    List<ChatRequest> findByRecipient_IdAndStatus(UUID recipientId, String status);
}
