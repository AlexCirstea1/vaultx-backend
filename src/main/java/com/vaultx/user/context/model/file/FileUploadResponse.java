package com.vaultx.user.context.model.file;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class FileUploadResponse {
    private UUID fileId;
    private UUID messageId;
}
