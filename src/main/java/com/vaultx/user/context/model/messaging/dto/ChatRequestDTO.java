package com.vaultx.user.context.model.messaging.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequestDTO {
    private UUID id;
    private String requester; // requester user ID (as string)
    private String recipient; // recipient user ID (as string)
    private String ciphertext;
    private String iv;
    private String encryptedKeyForSender;
    private String encryptedKeyForRecipient;
    private String senderKeyVersion;
    private String recipientKeyVersion;
    private LocalDateTime timestamp;
    private String status; // "PENDING", etc.
}
