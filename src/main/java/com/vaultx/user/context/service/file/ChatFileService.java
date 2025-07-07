package com.vaultx.user.context.service.file;

import com.vaultx.user.context.model.blockchain.EventType;
import com.vaultx.user.context.model.file.ChatFile;
import com.vaultx.user.context.model.file.FileUploadMeta;
import com.vaultx.user.context.model.file.FileUploadResponse;
import com.vaultx.user.context.model.messaging.ChatMessage;
import com.vaultx.user.context.model.messaging.MessageType;
import com.vaultx.user.context.model.user.User;
import com.vaultx.user.context.repository.ChatFileRepository;
import com.vaultx.user.context.repository.ChatMessageRepository;
import com.vaultx.user.context.service.user.BlockchainService;
import com.vaultx.user.context.service.user.UserService;
import jakarta.transaction.Transactional;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static com.vaultx.user.context.model.blockchain.EventType.FILE_UPLOAD;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatFileService {

    private final ChatFileRepository chatFileRepo;
    private final ChatMessageRepository chatMsgRepo;
    private final UserService userService;
    private final BlockchainService blockchainService;

    @Transactional
    public FileUploadResponse registerAndLink(FileUploadMeta meta, String uploaderId) {
        UUID msgId = meta.getMessageId();
        UUID fileId = meta.getFileId();

        // Find the message that was created via WebSocket
        ChatMessage message = chatMsgRepo.findById(msgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        // Verify uploader is the sender
        if (!message.getSender().getId().toString().equals(uploaderId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only sender can upload file");
        }

        // Verify message is a file message
        if (message.getMessageType() != MessageType.FILE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is not a file message");
        }

        // Get the existing ChatFile (created during WebSocket message)
        ChatFile file = chatFileRepo.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File record not found"));

        // Update any missing metadata (shouldn't be necessary but safety check)
        if (file.getFileName() == null) file.setFileName(meta.getFileName());
        if (file.getMimeType() == null) file.setMimeType(meta.getMimeType());
        if (file.getSizeBytes() == 0) file.setSizeBytes(meta.getSizeBytes());

        chatFileRepo.save(file);

        // Record blockchain event
        User user = userService.getUserById(UUID.fromString(uploaderId));
        blockchainService.recordDIDEvent(user, FILE_UPLOAD, meta.getFileName());

        return new FileUploadResponse(file.getId(), message.getId());
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
}
