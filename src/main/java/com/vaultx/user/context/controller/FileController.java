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
    private final ObjectMapper objectMapper;

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
    @Operation(summary = "Validate file integrity against blockchain records")
    public ResponseEntity<FileValidationResponse> validateFile(
            @PathVariable UUID fileId,
            @RequestParam(value = "validateSource", defaultValue = "storage") String validateSource,
            @RequestPart(value = "file", required = false) MultipartFile file,
            Authentication authentication) throws IOException {

        String requesterId = ((Jwt) authentication.getPrincipal()).getSubject();

        // 1) Authorize and fetch file metadata
        ChatFile fileMeta = chatFileService.authoriseAndGet(fileId, requesterId);

        // Determine both participants
        UUID senderId = fileMeta.getMessage().getSender().getId();
        UUID recipientId = fileMeta.getMessage().getRecipient().getId();

        // 2) Try to fetch matching FILE_UPLOAD event from requester, then from the other user
        DIDEvent event = blockchainService.getFileEvent(UUID.fromString(requesterId), fileId);
        if (event == null) {
            UUID otherUserId = requesterId.equals(senderId.toString()) ? recipientId : senderId;
            event = blockchainService.getFileEvent(otherUserId, fileId);
        }

        if (event == null) {
            return ResponseEntity.ok(
                    FileValidationResponse.builder()
                            .fileId(fileId)
                            .isValid(false)
                            .message("No blockchain record found for this file")
                            .build()
            );
        }

        // 3) Parse stored metadata
        FileBlockchainMeta blockchainMeta;
        try {
            blockchainMeta = objectMapper.readValue(event.getPayload(), FileBlockchainMeta.class);
        } catch (Exception ex) {
            log.error("Failed to parse FileBlockchainMeta from event {}", event.getEventId(), ex);
            return ResponseEntity.ok(
                    FileValidationResponse.builder()
                            .fileId(fileId)
                            .isValid(false)
                            .message("Error parsing blockchain metadata")
                            .build()
            );
        }

        // 4) Compute current hash based on requested source
        byte[] fileBytes;
        String source;

        if ("uploaded".equals(validateSource) && file != null) {
            // Use the uploaded file for validation
            fileBytes = file.getBytes();
            source = "uploaded file";
        } else {
            // Default: use storage file for validation
            Resource fileResource = storage.loadAsResource(fileId);
            fileBytes = fileResource.getInputStream().readAllBytes();
            source = "storage";
        }

        String currentHash = CipherUtils.getHash(fileBytes);
        boolean isValid = currentHash.equals(blockchainMeta.getFileHash());

        String msg = isValid
                ? "File integrity verified successfully"
                : "File has been modified - hash mismatch";

        log.info("Validation for {} (source: {}): {} (blockchain={}, current={})",
                fileId, source, isValid, blockchainMeta.getFileHash(), currentHash);

        // 5) Return structured response
        return ResponseEntity.ok(
                FileValidationResponse.builder()
                        .fileId(fileId)
                        .isValid(isValid)
                        .message(msg)
                        .blockchainHash(blockchainMeta.getFileHash())
                        .currentHash(currentHash)
                        .uploadTimestamp(blockchainMeta.getUploadTimestamp())
                        .build()
        );
    }
}
