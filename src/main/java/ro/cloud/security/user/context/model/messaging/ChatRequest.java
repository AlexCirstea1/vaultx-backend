package ro.cloud.security.user.context.model.messaging;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ro.cloud.security.user.context.model.user.User;

@Entity
@Table(
        name = "chat_requests",
        // JPA cannot create a *partial* unique index (status = 'PENDING'),
        // so we create it in Flyway (see below). This composite index prevents
        // two concurrent PENDING requests between the same pair.
        indexes = {
                @Index(name = "idx_chat_requests_requester_recipient", columnList = "requester_id, recipient_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    // ---- encrypted preview message (same fields as ChatMessage) ----
    @Column(name = "cipher_text", columnDefinition = "TEXT", nullable = false)
    private String ciphertext;

    @Column(name = "iv", columnDefinition = "TEXT")
    private String iv;

    @Column(name = "encrypted_key_for_sender", columnDefinition = "TEXT")
    private String encryptedKeyForSender;

    @Column(name = "encrypted_key_for_recipient", columnDefinition = "TEXT")
    private String encryptedKeyForRecipient;

    @Column(name = "sender_key_version")
    private String senderKeyVersion;

    @Column(name = "recipient_key_version")
    private String recipientKeyVersion;

    // --------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ChatRequestStatus status = ChatRequestStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
