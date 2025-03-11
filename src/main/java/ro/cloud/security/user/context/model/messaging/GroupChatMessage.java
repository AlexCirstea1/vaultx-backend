package ro.cloud.security.user.context.model.messaging;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "group_chat_message")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    // References the group chat
    @Column(nullable = false)
    private UUID groupId;

    // The sender's user ID (e.g. JWT subject)
    @Column(nullable = false)
    private String sender;

    // The message content (plaintext or ciphertext)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // Timestamp of message creation
    @Column(nullable = false)
    private Instant timestamp;
}
