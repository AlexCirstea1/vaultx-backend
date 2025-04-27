package com.vaultx.user.context.repository;

import com.vaultx.user.context.model.messaging.ChatMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /**
     * Returns all messages where the given user is either the sender or the recipient.
     */
    @Query(
            """
    SELECT m FROM ChatMessage m
    WHERE m.timestamp IN (
        SELECT MAX(m2.timestamp) FROM ChatMessage m2
        WHERE m2.sender.id = :userId OR m2.recipient.id = :userId
        GROUP BY CASE
            WHEN m2.sender.id = :userId THEN m2.recipient.id
            ELSE m2.sender.id
        END
    )
""")
    List<ChatMessage> findLatestMessagesByUser(@Param("userId") UUID userId);

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

    int countByRecipientIdAndSenderIdAndReadFalse(UUID userId, UUID otherUserId);

    List<ChatMessage> findByIdInAndRecipientIdAndReadFalse(List<UUID> messageIds, UUID recipientId);
}
