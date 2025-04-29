package com.vaultx.user.context.service.chat;

import com.vaultx.user.context.mapper.ChatMessageMapper;
import com.vaultx.user.context.mapper.ChatRequestMapper;
import com.vaultx.user.context.model.blockchain.EventType;
import com.vaultx.user.context.model.messaging.ChatMessage;
import com.vaultx.user.context.model.messaging.ChatRequest;
import com.vaultx.user.context.model.messaging.ChatRequestStatus;
import com.vaultx.user.context.model.messaging.dto.ChatMessageDTO;
import com.vaultx.user.context.model.messaging.dto.ChatRequestDTO;
import com.vaultx.user.context.model.user.User;
import com.vaultx.user.context.repository.ChatMessageRepository;
import com.vaultx.user.context.repository.ChatRequestRepository;
import com.vaultx.user.context.service.user.BlockService;
import com.vaultx.user.context.service.user.BlockchainService;
import com.vaultx.user.context.service.user.UserService;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatRequestService {

    private final ChatRequestRepository chatRequestRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;
    private final BlockService blockService;
    private final ChatRequestMapper chatRequestMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final BlockchainService blockchainService;

    /* ───────────────────────── PUBLIC API ─────────────────────────── */

    @Transactional
    public ChatRequestDTO sendRequest(ChatMessageDTO chatRequestDto, String rawSenderId) {
        UUID senderId = UUID.fromString(rawSenderId);
        UUID recipientId = UUID.fromString(chatRequestDto.getRecipient());

        // 1. Block checks
        if (blockService.isUserBlocked(senderId, recipientId) || blockService.isUserBlocked(recipientId, senderId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Cannot send chat request – one user blocked the other");
        }

        // 2. De-duplication
        chatRequestRepository
                .findByRequesterAndRecipientAndStatus(senderId, recipientId, ChatRequestStatus.PENDING)
                .ifPresent(x -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "A pending request already exists");
                });

        User sender = userService.getUserById(senderId);
        User recipient = userService.getUserById(recipientId);

        ChatRequest entity = ChatRequest.builder()
                .requester(sender)
                .recipient(recipient)
                .ciphertext(chatRequestDto.getCiphertext())
                .encryptedKeyForRecipient(chatRequestDto.getEncryptedKeyForRecipient())
                .encryptedKeyForSender(chatRequestDto.getEncryptedKeyForSender())
                .iv(chatRequestDto.getIv())
                .senderKeyVersion(chatRequestDto.getSenderKeyVersion())
                .recipientKeyVersion(chatRequestDto.getRecipientKeyVersion())
                .status(ChatRequestStatus.PENDING)
                .timestamp(LocalDateTime.now())
                .build();

        try {
            entity = chatRequestRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A pending request already exists");
        }

        ChatRequestDTO dto = chatRequestMapper.toDto(entity);

        // 3. Push WS notification to recipient
        messagingTemplate.convertAndSendToUser(recipientId.toString(), "/queue/chatRequests", dto);

        return dto;
    }

    @Transactional
    public void accept(UUID requestId, String rawRecipientId) {
        ChatRequest request = getRequestOrThrow(requestId);
        validateRecipient(request, rawRecipientId);

        if (request.getStatus() != ChatRequestStatus.PENDING)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request already processed");

        request.setStatus(ChatRequestStatus.ACCEPTED);
        chatRequestRepository.save(request);

        // upgrade to first ChatMessage
        ChatMessage msg = ChatMessage.builder()
                .sender(request.getRequester())
                .recipient(request.getRecipient())
                .ciphertext(request.getCiphertext())
                .encryptedKeyForRecipient(request.getEncryptedKeyForRecipient())
                .encryptedKeyForSender(request.getEncryptedKeyForSender())
                .iv(request.getIv())
                .senderKeyVersion(request.getSenderKeyVersion())
                .recipientKeyVersion(request.getRecipientKeyVersion())
                .timestamp(LocalDateTime.now())
                .isRead(false)
                .oneTime(false)
                .build();
        msg = chatMessageRepository.save(msg);

        ChatMessageDTO toRecipient = chatMessageMapper.toDtoWithType(msg, "INCOMING_MESSAGE");
        ChatMessageDTO toSender = chatMessageMapper.toDtoWithType(msg, "SENT_MESSAGE");

        messagingTemplate.convertAndSendToUser(
                request.getRecipient().getId().toString(), "/queue/messages", toRecipient);
        messagingTemplate.convertAndSendToUser(request.getRequester().getId().toString(), "/queue/sent", toSender);

        blockchainService.recordDIDEvent(request.getRequester(), EventType.CHAT_CREATED, request.getRecipient());
    }

    @Transactional
    public void reject(UUID requestId, String rawRecipientId) {
        ChatRequest request = getRequestOrThrow(requestId);
        validateRecipient(request, rawRecipientId);

        if (request.getStatus() != ChatRequestStatus.PENDING) return;
        request.setStatus(ChatRequestStatus.REJECTED);
        chatRequestRepository.save(request);
    }

    @Transactional
    public void cancel(UUID requestId, String rawSenderId) {
        ChatRequest request = getRequestOrThrow(requestId);
        if (!request.getRequester().getId().toString().equals(rawSenderId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You did not create this request");

        if (request.getStatus() != ChatRequestStatus.PENDING) return;
        request.setStatus(ChatRequestStatus.CANCELLED);
        chatRequestRepository.save(request);
    }

    public List<ChatRequestDTO> pendingForUser(String rawUserId) {
        UUID uid = UUID.fromString(rawUserId);
        return chatRequestRepository.findByRecipient_IdAndStatus(uid, ChatRequestStatus.PENDING).stream()
                .map(chatRequestMapper::toDto)
                .collect(Collectors.toList());
    }

    /* ───────────────────────── Helpers ─────────────────────────── */

    private void validateRecipient(ChatRequest r, String rawRecipientId) {
        if (!r.getRecipient().getId().toString().equals(rawRecipientId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorised for this request");
    }

    private ChatRequest getRequestOrThrow(UUID id) {
        return chatRequestRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat request not found"));
    }
}
