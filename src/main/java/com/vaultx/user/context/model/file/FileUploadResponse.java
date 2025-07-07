package com.vaultx.user.context.model.file;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class FileUploadResponse {
    private UUID fileId;
    private UUID messageId;
}
