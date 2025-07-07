package com.vaultx.user.context.model.messaging.dto;

import com.vaultx.user.context.model.file.FileInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {
    private UUID id;
    private String sender;
    private String recipient;
    private String ciphertext;
    private String iv;
    private String encryptedKeyForSender;
    private String encryptedKeyForRecipient;
    private String senderKeyVersion;
    private String recipientKeyVersion;
    private FileInfo file;
    private LocalDateTime timestamp;
    private boolean isRead;
    private LocalDateTime readTimestamp;
    private String clientTempId;
    private String type;   // "INCOMING_MESSAGE", "SENT_MESSAGE", "FILE_INCOMING", "FILE_SENT"
    private boolean oneTime;
}
