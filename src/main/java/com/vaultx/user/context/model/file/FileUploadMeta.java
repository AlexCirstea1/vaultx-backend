package com.vaultx.user.context.model.file;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadMeta {
    @NotNull
    private UUID messageId;  // Links back to the ChatMessage
    @NotNull
    private UUID fileId;     // Same as in the WebSocket message
    @NotBlank
    private String fileName;
    @NotBlank
    private String mimeType;
    @Positive
    private long sizeBytes;
}
