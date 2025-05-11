package com.vaultx.user.context.model.file;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FileUploadResponse {
    private UUID fileId;
    private UUID messageId;
}
