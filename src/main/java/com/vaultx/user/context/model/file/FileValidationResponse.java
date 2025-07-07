package com.vaultx.user.context.model.file;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class FileValidationResponse {
    private UUID fileId;
    private boolean isValid;
    private String message;
    private String blockchainHash;
    private String currentHash;
    private Long uploadTimestamp;
}