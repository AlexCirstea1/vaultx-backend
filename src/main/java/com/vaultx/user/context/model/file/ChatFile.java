package com.vaultx.user.context.model.file;

import com.vaultx.user.context.model.messaging.ChatMessage;
import jakarta.persistence.*;

import java.util.UUID;

import lombok.*;

@Entity
@Table(name = "chat_files")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatFile {
    @Id
    private UUID id;

    @OneToOne
    @JoinColumn(name = "message_id")
    private ChatMessage message;

    @Column(columnDefinition = "TEXT")
    private String fileName;
    @Column(columnDefinition = "TEXT")
    private String mimeType;
    private long sizeBytes;

    @Column(name = "iv", columnDefinition = "TEXT")
    private String iv;
    @Column(name = "encrypted_key_sender", columnDefinition = "TEXT")
    private String encryptedKeySender;
    @Column(name = "encrypted_key_recipient", columnDefinition = "TEXT")
    private String encryptedKeyRecipient;
    private String senderKeyVersion;
    private String recipientKeyVersion;
}