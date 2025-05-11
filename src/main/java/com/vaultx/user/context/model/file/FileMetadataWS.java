package com.vaultx.user.context.model.file;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileMetadataWS {
    private UUID messageId; // to match with the eventual /upload result
    private UUID fileId;
    private String recipient; // UUID string
    private String sender; // "
    private String fileName;
    private String mimeType;
    private long sizeBytes;
    private String iv;
    private String encryptedKeyForSender;
    private String encryptedKeyForRecipient;
    private String senderKeyVersion;
    private String recipientKeyVersion;
    private LocalDateTime timestamp;
    private String clientTempId; // same echo trick
    private String type; // "FILE_SENT" / "FILE_INCOMING"
}
