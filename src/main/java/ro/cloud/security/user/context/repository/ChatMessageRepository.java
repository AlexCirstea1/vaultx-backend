package ro.cloud.security.user.context.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ro.cloud.security.user.context.model.messaging.ChatMessage;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /**
     * Returns all messages where the given user is either the sender or the recipient.
     */
    @Query(
            """
        SELECT m
        FROM ChatMessage m
        WHERE m.sender.id = :userId
           OR m.recipient.id = :userId
    """)
    List<ChatMessage> findBySenderOrRecipient(@Param("userId") UUID userId);

    /**
     * Returns messages in a conversation between userA and userB.
     */
    @Query(
            """
        SELECT m
        FROM ChatMessage m
        WHERE (m.sender.id = :userA AND m.recipient.id = :userB)
           OR (m.sender.id = :userB AND m.recipient.id = :userA)
    """)
    List<ChatMessage> findConversation(@Param("userA") UUID userA, @Param("userB") UUID userB);

    // If you need a 'findAllById' or other standard methods,
    // you can rely on JpaRepository's built-in methods.
}
