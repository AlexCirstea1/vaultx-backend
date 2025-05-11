package com.vaultx.user.context.model.file;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileUploadMeta {

    @NotNull
    private UUID messageId;

    @NotNull
    private UUID fileId;

    @NotBlank
    private String sender;

    @NotBlank
    private String recipient;

    @NotBlank
    private String fileName;

    @NotBlank
    private String mimeType;

    @Positive
    private long sizeBytes;

    /* crypto */
    @NotBlank
    private String iv;

    @NotBlank
    private String encryptedKeyForSender;

    @NotBlank
    private String encryptedKeyForRecipient;

    @NotBlank
    private String senderKeyVersion;

    @NotBlank
    private String recipientKeyVersion;
}
