package ro.cloud.security.user.context.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ro.cloud.security.user.context.model.messaging.*;
import ro.cloud.security.user.context.model.messaging.dto.ChatMessageDTO;
import ro.cloud.security.user.context.model.messaging.dto.ChatRequestDTO;
import ro.cloud.security.user.context.model.user.User;
import ro.cloud.security.user.context.repository.ChatMessageRepository;
import ro.cloud.security.user.context.repository.ChatRequestRepository;
import ro.cloud.security.user.context.service.authentication.UserService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatRequestService {

    private final ChatRequestRepository chatRequestRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;
    private final BlockService blockService;

    /* ───────────────────────── PUBLIC API ─────────────────────────── */

    @Transactional
    public ChatRequestDTO sendRequest(ChatMessageDTO chatRequestDto, String rawSenderId) {
        UUID senderId    = UUID.fromString(rawSenderId);
        UUID recipientId = UUID.fromString(chatRequestDto.getRecipient());

        // 1. Block checks
        if (blockService.isUserBlocked(senderId, recipientId)
                || blockService.isUserBlocked(recipientId, senderId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Cannot send chat request – one user blocked the other");
        }

        // 2. De-duplication
        chatRequestRepository
                .findByRequesterAndRecipientAndStatus(senderId, recipientId, ChatRequestStatus.PENDING)
                .ifPresent(x -> { throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "A pending request already exists"); });

        User sender    = userService.getUserById(senderId);
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
                .build();

        try {
            entity = chatRequestRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "A pending request already exists");
        }

        ChatRequestDTO dto = toDto(entity);

        // 3. Push WS notification to recipient
        messagingTemplate.convertAndSendToUser(
                recipientId.toString(), "/queue/chatRequests", dto);

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

        ChatMessageDTO toRecipient = toMessageDto(msg, "INCOMING_MESSAGE");
        ChatMessageDTO toSender    = toMessageDto(msg, "SENT_MESSAGE");

        messagingTemplate.convertAndSendToUser(
                request.getRecipient().getId().toString(), "/queue/messages", toRecipient);
        messagingTemplate.convertAndSendToUser(
                request.getRequester().getId().toString(), "/queue/sent",    toSender);
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
        return chatRequestRepository
                .findByRecipient_IdAndStatus(uid, ChatRequestStatus.PENDING)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /* ───────────────────────── Helpers ─────────────────────────── */

    private void validateRecipient(ChatRequest r, String rawRecipientId) {
        if (!r.getRecipient().getId().toString().equals(rawRecipientId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorised for this request");
    }

    private ChatRequest getRequestOrThrow(UUID id) {
        return chatRequestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Chat request not found"));
    }

    /** Manual mapping avoids ModelMapper’s User → String issue. */
    private ChatRequestDTO toDto(ChatRequest e) {
        return ChatRequestDTO.builder()
                .id(e.getId())
                .requester(e.getRequester().getId().toString())
                .recipient(e.getRecipient().getId().toString())
                .ciphertext(e.getCiphertext())
                .iv(e.getIv())
                .encryptedKeyForSender(e.getEncryptedKeyForSender())
                .encryptedKeyForRecipient(e.getEncryptedKeyForRecipient())
                .senderKeyVersion(e.getSenderKeyVersion())
                .recipientKeyVersion(e.getRecipientKeyVersion())
                .status(e.getStatus().name())
                .timestamp(e.getCreatedAt())   // ← fixed line
                .build();
    }

    private ChatMessageDTO toMessageDto(ChatMessage e, String type) {
        return ChatMessageDTO.builder()
                .id(e.getId())
                .sender(e.getSender().getId().toString())
                .recipient(e.getRecipient().getId().toString())
                .ciphertext(e.getCiphertext())
                .iv(e.getIv())
                .encryptedKeyForSender(e.getEncryptedKeyForSender())
                .encryptedKeyForRecipient(e.getEncryptedKeyForRecipient())
                .senderKeyVersion(e.getSenderKeyVersion())
                .recipientKeyVersion(e.getRecipientKeyVersion())
                .timestamp(e.getTimestamp())
                .type(type)
                .build();
    }
}
