package com.vaultx.user.context.service.file;

import com.vaultx.user.context.model.file.ChatFile;
import com.vaultx.user.context.model.file.FileUploadMeta;
import com.vaultx.user.context.model.file.FileUploadResponse;
import com.vaultx.user.context.model.messaging.ChatMessage;
import com.vaultx.user.context.repository.ChatFileRepository;
import com.vaultx.user.context.repository.ChatMessageRepository;
import com.vaultx.user.context.service.user.UserService;
import jakarta.transaction.Transactional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatFileService {

    private final ChatFileRepository chatFileRepo;
    private final ChatMessageRepository chatMsgRepo;
    private final UserService userService;

    /* ──────────────────  upload metadata  ────────────────── */

    @Transactional
    public FileUploadResponse registerAndLink(FileUploadMeta meta, String uploaderId) {

        if (!uploaderId.equals(meta.getSender())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Uploader id does not match sender field");
        }

        UUID msgId = meta.getMessageId();
        UUID fileId = meta.getFileId();

        /* placeholder comes from WS - if absent we build it */
        ChatMessage message = chatMsgRepo.findById(msgId).orElseGet(() -> {
            log.warn("WS placeholder missing – creating ChatMessage on upload");
            return buildAndSaveMessage(meta);
        });

        /* only original sender may upload */
        if (!message.getSender().getId().toString().equals(uploaderId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        ChatFile file = chatFileRepo
                .findById(fileId)
                .orElse(ChatFile.builder().id(fileId).message(message).build());

        file.setFileName(meta.getFileName());
        file.setMimeType(meta.getMimeType());
        file.setSizeBytes(meta.getSizeBytes());
        file.setIv(meta.getIv());
        file.setEncryptedKeySender(meta.getEncryptedKeyForSender());
        file.setEncryptedKeyRecipient(meta.getEncryptedKeyForRecipient());
        file.setSenderKeyVersion(meta.getSenderKeyVersion());
        file.setRecipientKeyVersion(meta.getRecipientKeyVersion());

        chatFileRepo.save(file);

        return new FileUploadResponse(file.getId(), message.getId());
    }

    /* ──────────────────  download authorisation  ────────────────── */

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

    /* ──────────────────  helper  ────────────────── */

    private ChatMessage buildAndSaveMessage(FileUploadMeta meta) {
        ChatMessage msg = ChatMessage.builder()
                .sender(userService.getUserById(UUID.fromString(meta.getSender())))
                .recipient(userService.getUserById(UUID.fromString(meta.getRecipient())))
                .ciphertext("__FILE__")
                .iv(meta.getIv())
                .encryptedKeyForSender(meta.getEncryptedKeyForSender())
                .encryptedKeyForRecipient(meta.getEncryptedKeyForRecipient())
                .senderKeyVersion(meta.getSenderKeyVersion())
                .recipientKeyVersion(meta.getRecipientKeyVersion())
                .oneTime(false)
                .build();
        return chatMsgRepo.save(msg);
    }
}
