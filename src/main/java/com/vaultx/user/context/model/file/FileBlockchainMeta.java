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
public class FileBlockchainMeta {
    private UUID fileId;
    private UUID messageId;
    private String fileName;
    private String mimeType;
    private long fileSize;
    private String fileHash;
    private long uploadTimestamp;
}