package ro.cloud.security.user.context.model.messaging;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ro.cloud.security.user.context.model.user.User;

@Entity
@Table(name = "chat_requests")
@Data
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

    // The encrypted initial message (if any) – same encryption fields as ChatMessage.
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

    @Column(name = "timestamp", nullable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    // Request status: "PENDING", "ACCEPTED", "REJECTED", "BLOCKED"
    @Column(name = "status", nullable = false)
    private String status;
}
