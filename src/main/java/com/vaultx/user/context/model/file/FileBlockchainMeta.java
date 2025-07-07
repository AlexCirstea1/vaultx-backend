package com.vaultx.user.context.model.file;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class FileBlockchainMeta {
    private UUID fileId;
    private UUID messageId;
    private String fileName;
    private String mimeType;
    private long fileSize;
    private String fileHash;
    private long uploadTimestamp;
}