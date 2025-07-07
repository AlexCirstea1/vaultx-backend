package com.vaultx.user.context.controller;

import com.vaultx.user.context.model.file.ChatFile;
import com.vaultx.user.context.model.file.FileUploadMeta;
import com.vaultx.user.context.model.file.FileUploadResponse;
import com.vaultx.user.context.service.file.ChatFileService;
import com.vaultx.user.context.service.file.FileStorageService;
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
}
