package com.vaultx.user.context.service.file;

import com.vaultx.user.context.model.file.ChatFile;
import com.vaultx.user.context.model.file.FileBlockchainMeta;
import com.vaultx.user.context.model.file.FileUploadMeta;
import com.vaultx.user.context.model.file.FileUploadResponse;
import com.vaultx.user.context.model.messaging.ChatMessage;
import com.vaultx.user.context.model.messaging.MessageType;
import com.vaultx.user.context.model.user.User;
import com.vaultx.user.context.repository.ChatFileRepository;
import com.vaultx.user.context.repository.ChatMessageRepository;
import com.vaultx.user.context.service.user.BlockchainService;
import com.vaultx.user.context.service.user.UserService;
import com.vaultx.user.context.utils.CipherUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static com.vaultx.user.context.model.blockchain.EventType.FILE_UPLOAD;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatFileService {

    private final ChatFileRepository chatFileRepo;
    private final ChatMessageRepository chatMsgRepo;
    private final UserService userService;
    private final BlockchainService blockchainService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FileUploadResponse registerAndLink(FileUploadMeta meta, String uploaderId) {
        UUID messageId = meta.getMessageId();
        UUID fileId = meta.getFileId();

        // Wait for the message to be available with retry logic
        ChatMessage message = waitForMessage(messageId);

        // Verify message is a file message
        if (message.getMessageType() != MessageType.FILE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is not a file message");
        }

        // Verify uploader is authorized (sender or recipient)
        String senderId = message.getSender().getId().toString();
        String recipientId = message.getRecipient().getId().toString();

        if (!uploaderId.equals(senderId) && !uploaderId.equals(recipientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to upload for this message");
        }

        return FileUploadResponse.builder()
                .fileId(fileId)
                .messageId(messageId)
                .build();
    }

    public ChatFile authoriseAndGet(UUID fileId, String requesterId) {
        ChatFile file = chatFileRepo
                .findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));

        String senderId = file.getMessage().getSender().getId().toString();
        String recipientId = file.getMessage().getRecipient().getId().toString();

        if (!requesterId.equals(senderId) && !requesterId.equals(recipientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a participant in this conversation");
        }
        return file;
    }

    private ChatMessage waitForMessage(UUID messageId) {
        final int maxAttempts = 20; // Increased from what appears to be fewer attempts
        final long delayMs = 100; // Reduced delay for faster retries

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Optional<ChatMessage> message = chatMsgRepo.findById(messageId);
            if (message.isPresent()) {
                return message.get();
            }

            log.debug("Message {} not found on attempt {}/{}", messageId, attempt, maxAttempts);

            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Interrupted while waiting for message");
            }
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Message not found after waiting");
    }

    public void recordFileBlockchainTransaction(FileUploadMeta meta, MultipartFile file, String uploaderId) throws IOException {
        User user = userService.getUserById(UUID.fromString(uploaderId));

        // Calculate hash of the encrypted file for authenticity verification using CipherUtils
        byte[] fileBytes = file.getBytes();
        String fileHash = CipherUtils.getHash(fileBytes);

        // Create enhanced metadata including file hash and size
        FileBlockchainMeta blockchainMeta = FileBlockchainMeta.builder()
                .fileId(meta.getFileId())
                .messageId(meta.getMessageId())
                .fileName(meta.getFileName())
                .mimeType(meta.getMimeType())
                .fileSize(file.getSize())
                .fileHash(fileHash)
                .uploadTimestamp(System.currentTimeMillis())
                .build();

        blockchainService.recordDIDEvent(user, FILE_UPLOAD, blockchainMeta);
    }
}
