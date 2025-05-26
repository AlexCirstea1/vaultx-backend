package com.vaultx.user.context.model.file;

import com.vaultx.user.context.model.messaging.ChatMessage;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_files")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatFile {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id")
    private ChatMessage message;

    @Column(length = 1000)
    private String fileName;
    private String mimeType;
    private long sizeBytes;

    /* crypto */
    @Column(name = "iv", columnDefinition = "TEXT")
    private String iv;
    @Column(name = "encrypted_key_sender", columnDefinition = "TEXT")
    private String encryptedKeySender;
    @Column(name = "encrypted_key_recipient", columnDefinition = "TEXT")
    private String encryptedKeyRecipient;
    private String senderKeyVersion;
    private String recipientKeyVersion;
}
