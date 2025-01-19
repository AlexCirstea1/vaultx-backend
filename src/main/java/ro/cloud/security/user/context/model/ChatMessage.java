package ro.cloud.security.user.context.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_message")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "sender", nullable = false)
    private String sender; // e.g., JWT subject

    @Column(name = "recipient", nullable = false)
    private String recipient; // e.g., JWT subject

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content; // The plaintext message body (or ciphertext, depending on your design)

    @Column(name = "timestamp", nullable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now(); // Server-side creation time

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean isRead = false;

    @Column(name = "read_timestamp")
    private LocalDateTime readTimestamp;
}
