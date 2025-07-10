package com.vaultx.user.context.model.messaging;

import com.vaultx.user.context.model.file.ChatFile;
import com.vaultx.user.context.model.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Column(name = "sender_key_version")
    private String senderKeyVersion;

    @Column(name = "recipient_key_version")
    private String recipientKeyVersion;

    @Column(name = "cipher_text", columnDefinition = "TEXT", nullable = false)
    private String ciphertext;

    @Column(name = "iv", columnDefinition = "TEXT")
    private String iv;

    @Column(name = "encrypted_key_for_sender", columnDefinition = "TEXT")
    private String encryptedKeyForSender;

    @Column(name = "encrypted_key_for_recipient", columnDefinition = "TEXT")
    private String encryptedKeyForRecipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    @Builder.Default
    private MessageType messageType = MessageType.NORMAL;

    @OneToOne(mappedBy = "message", cascade = CascadeType.ALL, fetch = FetchType.EAGER, optional = true)
    private ChatFile file;

    @Column(name = "timestamp", nullable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean isRead = false;

    @Column(name = "one_time", nullable = false)
    @Builder.Default
    private boolean oneTime = false;

    @Column(name = "read_timestamp")
    private LocalDateTime readTimestamp;

    @Transient
    private String clientTempId;
}