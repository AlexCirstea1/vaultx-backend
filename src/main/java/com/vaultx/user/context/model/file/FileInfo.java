package com.vaultx.user.context.model.file;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInfo {
    private UUID fileId;
    private String fileName;
    private String mimeType;
    private long sizeBytes;
}
