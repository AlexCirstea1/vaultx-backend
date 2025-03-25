package ro.cloud.security.user.context.model.messaging.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    private LocalDateTime timestamp;
    private boolean isRead;
    private LocalDateTime readTimestamp;
    private String clientTempId;
    private String type; // "INCOMING_MESSAGE" or "SENT_MESSAGE"
}

