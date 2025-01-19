package ro.cloud.security.user.context.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ro.cloud.security.user.context.model.ChatMessage;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    // Fetch messages between two users, ordered by timestamp
    @Query("SELECT m FROM ChatMessage m WHERE (m.sender = :user1 AND m.recipient = :user2) "
            + "OR (m.sender = :user2 AND m.recipient = :user1) ORDER BY m.timestamp ASC")
    List<ChatMessage> findConversation(String user1, String user2);

    // Fetch all messages where sender is user1 or recipient is user1
    List<ChatMessage> findBySenderOrRecipient(String sender, String recipient);

    // Fetch unread messages sent by sender to recipient
    @Query("SELECT m FROM ChatMessage m WHERE m.sender = :senderId AND m.recipient = :recipientId AND m.isRead = false")
    List<ChatMessage> findUnreadMessages(String senderId, String recipientId);

    // Bulk update messages to mark them as read
    @Modifying
    @Query("UPDATE ChatMessage m SET m.isRead = true, m.readTimestamp = CURRENT_TIMESTAMP WHERE m.id IN :messageIds")
    void markMessagesAsRead(List<UUID> messageIds);
}
