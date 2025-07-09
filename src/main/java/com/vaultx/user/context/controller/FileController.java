package com.vaultx.user.context.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultx.user.context.model.blockchain.DIDEvent;
import com.vaultx.user.context.model.file.*;
import com.vaultx.user.context.service.file.ChatFileService;
import com.vaultx.user.context.service.file.FileStorageService;
import com.vaultx.user.context.service.user.BlockchainService;
import com.vaultx.user.context.utils.CipherUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "FileTransfer", description = "Upload & download encrypted chat files")
public class FileController {

    private final FileStorageService storage;
    private final ChatFileService chatFileService;
    private final BlockchainService blockchainService;

    /* ───────────────  UPLOAD  ─────────────── */

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload encrypted blob + metadata")
    public ResponseEntity<FileUploadResponse> upload(
            @Valid @RequestPart("meta") FileUploadMeta meta,
            @RequestPart("file") MultipartFile file,
            Authentication authentication)
            throws IOException {

        log.info("File upload received - name: {}, size: {}, contentType: {}",
                file.getOriginalFilename(),
                file.getSize(),
                file.getContentType());
        log.info("Metadata received: {}", meta);

        String uploaderId = ((Jwt) authentication.getPrincipal()).getSubject();
        log.info("Uploader ID: {}", uploaderId);

        try {
            FileUploadResponse resp = chatFileService.registerAndLink(meta, uploaderId);
            log.info("File metadata registered with ID: {}", resp.getFileId());

            chatFileService.recordFileBlockchainTransaction(meta, file, uploaderId);

            byte[] fileBytes = file.getBytes();
            log.info("Read {} bytes from uploaded file", fileBytes.length);
            storage.saveEncryptedFile(resp.getFileId(), fileBytes);
            log.info("File successfully saved to storage");

            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("Error processing file upload", e);
            throw e;
        }
    }

    /* ───────────────  DOWNLOAD  ─────────────── */

    @GetMapping("/{fileId}")
    @Operation(summary = "Download encrypted blob (authorised)")
    public ResponseEntity<Resource> download(@PathVariable UUID fileId, Authentication authentication)
            throws IOException {

        String requesterId = ((Jwt) authentication.getPrincipal()).getSubject();

        ChatFile meta = chatFileService.authoriseAndGet(fileId, requesterId);
        Resource stream = storage.loadAsResource(fileId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(meta.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + meta.getFileName() + "\"")
                .body(stream);
    }

    @PostMapping("/{fileId}/validate")
    @Operation(summary = "Validate file integrity against blockchain record")
    public ResponseEntity<FileValidationResponse> validateFile(
            @PathVariable UUID fileId,
            @RequestPart("file") MultipartFile file,
            Authentication authentication) throws IOException {

        String requesterId = ((Jwt) authentication.getPrincipal()).getSubject();

        // Authorize access to the file
        ChatFile fileMetadata = chatFileService.authoriseAndGet(fileId, requesterId);

        var otherUserId = fileMetadata.getMessage().getRecipient().getId();

        // Get blockchain record for this file
        DIDEvent blockchainEvent = blockchainService.getFileEvent(UUID.fromString(requesterId), fileId);

        // Try recipient’s blockchain record if none for requester
        if (blockchainEvent == null) {
            blockchainEvent = blockchainService.getFileEvent(otherUserId, fileId);
        }
        if (blockchainEvent == null) {
            return ResponseEntity.ok(FileValidationResponse.builder()
                    .fileId(fileId)
                    .isValid(false)
                    .message("No blockchain record found for this file")
                    .build());
        }

        try {
            // Parse blockchain metadata
            ObjectMapper mapper = new ObjectMapper();
            FileBlockchainMeta blockchainMeta = mapper.readValue(blockchainEvent.getPayload(), FileBlockchainMeta.class);

            // Calculate hash of the uploaded file
            byte[] fileBytes = file.getBytes();
            String currentFileHash = CipherUtils.getHash(new String(fileBytes, StandardCharsets.ISO_8859_1));

            // Compare hashes
            boolean isValid = currentFileHash.equals(blockchainMeta.getFileHash());

            String message = isValid ?
                    "File integrity verified successfully" :
                    "File has been modified - hash mismatch";

            log.info("File {} validation result: {} (blockchain: {}, current: {})",
                    fileId, isValid, blockchainMeta.getFileHash(), currentFileHash);

            return ResponseEntity.ok(FileValidationResponse.builder()
                    .fileId(fileId)
                    .isValid(isValid)
                    .message(message)
                    .blockchainHash(blockchainMeta.getFileHash())
                    .currentHash(currentFileHash)
                    .uploadTimestamp(blockchainMeta.getUploadTimestamp())
                    .build());

        } catch (Exception e) {
            log.error("Error validating file {}", fileId, e);
            return ResponseEntity.ok(FileValidationResponse.builder()
                    .fileId(fileId)
                    .isValid(false)
                    .message("Error processing validation: " + e.getMessage())
                    .build());
        }
    }
}
