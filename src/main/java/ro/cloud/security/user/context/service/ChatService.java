package ro.cloud.security.user.context.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ro.cloud.security.user.context.model.authentication.request.MarkReadRequest;
import ro.cloud.security.user.context.model.authentication.response.ReadReceiptNotification;
import ro.cloud.security.user.context.model.messaging.ChatMessage;
import ro.cloud.security.user.context.model.messaging.ChatRequest;
import ro.cloud.security.user.context.model.messaging.dto.ChatHistoryDTO;
import ro.cloud.security.user.context.model.messaging.dto.ChatMessageDTO;
import ro.cloud.security.user.context.model.messaging.dto.ChatRequestDTO;
import ro.cloud.security.user.context.model.user.User;
import ro.cloud.security.user.context.repository.ChatMessageRepository;
import ro.cloud.security.user.context.repository.ChatRequestRepository;
import ro.cloud.security.user.context.service.authentication.UserService;

@Service
@AllArgsConstructor
@Slf4j
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ModelMapper modelMapper;
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final BlockchainService blockchainService;
    private final BlockService blockService;
    private final ChatRequestRepository chatRequestRepository;

    /**
     * Send a private message from senderId to chatMessage.getRecipient().
     * Now that ChatMessage expects a User object for sender/recipient,
     * we fetch them from userService by UUID.
     */
    @Transactional
    public void sendPrivateMessage(ChatMessageDTO chatMessageDto, String senderId) {
        UUID senderUuid = UUID.fromString(senderId);
        UUID recipientUuid = UUID.fromString(chatMessageDto.getRecipient());

        if (blockService.isUserBlocked(senderUuid, recipientUuid)
                || blockService.isUserBlocked(recipientUuid, senderUuid)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Cannot send message. One user has blocked the other.");
        }

        User senderUser = userService.getUserById(senderUuid);
        User recipientUser = userService.getUserById(recipientUuid);

        // Check if new conversation, etc.
        // (omitted for brevity, see your existing code)

        // Build & save entity
        ChatMessage entity = ChatMessage.builder()
                .sender(senderUser)
                .recipient(recipientUser)
                // Store ciphertext + IV
                .ciphertext(chatMessageDto.getCiphertext())
                .encryptedKeyForRecipient(chatMessageDto.getEncryptedKeyForRecipient())
                .encryptedKeyForSender(chatMessageDto.getEncryptedKeyForSender())
                .iv(chatMessageDto.getIv())
                .senderKeyVersion(chatMessageDto.getSenderKeyVersion())
                .recipientKeyVersion(chatMessageDto.getRecipientKeyVersion())
                .timestamp(LocalDateTime.now())
                .isRead(false)
                .build();
        entity = chatMessageRepository.save(entity);

        // Convert to base DTO
        ChatMessageDTO baseDto = modelMapper.map(entity, ChatMessageDTO.class);
        baseDto.setSender(entity.getSender().getId().toString());
        baseDto.setRecipient(entity.getRecipient().getId().toString());
        baseDto.setClientTempId(chatMessageDto.getClientTempId());

        baseDto.setCiphertext(entity.getCiphertext());
        baseDto.setEncryptedKeyForRecipient(entity.getEncryptedKeyForRecipient());
        baseDto.setEncryptedKeyForSender(entity.getEncryptedKeyForSender());
        baseDto.setIv(entity.getIv());

        baseDto.setSenderKeyVersion(entity.getSenderKeyVersion());
        baseDto.setRecipientKeyVersion(entity.getRecipientKeyVersion());

        baseDto.setRead(entity.isRead());
        baseDto.setReadTimestamp(entity.getReadTimestamp());
        baseDto.setClientTempId(chatMessageDto.getClientTempId());

        // Make "INCOMING_MESSAGE" + "SENT_MESSAGE"
        ChatMessageDTO toRecipient = cloneMessageDTO(baseDto);
        toRecipient.setType("INCOMING_MESSAGE");

        ChatMessageDTO toSender = cloneMessageDTO(baseDto);
        toSender.setType("SENT_MESSAGE");

        // Broadcast
        messagingTemplate.convertAndSendToUser(
                recipientUuid.toString(), "/queue/messages", toRecipient // type: "INCOMING_MESSAGE"
                );

        messagingTemplate.convertAndSendToUser(
                senderUuid.toString(), "/queue/sent", toSender // type: "SENT_MESSAGE"
                );
    }

    private ChatMessageDTO cloneMessageDTO(ChatMessageDTO original) {
        ChatMessageDTO copy = new ChatMessageDTO();
        copy.setId(original.getId());
        copy.setSender(original.getSender());
        copy.setRecipient(original.getRecipient());
        copy.setRecipientKeyVersion(original.getRecipientKeyVersion());
        copy.setEncryptedKeyForRecipient(original.getEncryptedKeyForRecipient());
        copy.setCiphertext(original.getCiphertext());
        copy.setEncryptedKeyForRecipient(original.getEncryptedKeyForRecipient());
        copy.setEncryptedKeyForSender(original.getEncryptedKeyForSender());
        copy.setIv(original.getIv());
        copy.setTimestamp(original.getTimestamp());
        copy.setRead(original.isRead());
        copy.setReadTimestamp(original.getReadTimestamp());
        copy.setClientTempId(original.getClientTempId());
        return copy;
    }

    /**
     * Returns the conversation (messages) between currentUserId and participantId.
     */
    public List<ChatMessageDTO> getConversation(String currentUserId, String participantId) {
        UUID currentUserUuid = UUID.fromString(currentUserId);
        UUID participantUuid = UUID.fromString(participantId);

        List<ChatMessage> conversation = chatMessageRepository.findConversation(currentUserUuid, participantUuid);

        return conversation.stream()
                .map(msg -> {
                    ChatMessageDTO dto = modelMapper.map(msg, ChatMessageDTO.class);
                    dto.setCiphertext(msg.getCiphertext());
                    dto.setEncryptedKeyForRecipient(msg.getEncryptedKeyForRecipient());
                    dto.setEncryptedKeyForSender(msg.getEncryptedKeyForSender());
                    dto.setIv(msg.getIv());
                    dto.setSender(msg.getSender().getId().toString());
                    dto.setRecipient(msg.getRecipient().getId().toString());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns chat summaries for the user, containing only the latest message per conversation.
     */
    public List<ChatHistoryDTO> getChatSummaries(String currentUserId) {
        UUID currentUserUuid = UUID.fromString(currentUserId);

        // Find the latest message for each conversation
        List<ChatMessage> latestMessages = chatMessageRepository.findLatestMessagesByUser(currentUserUuid);

        // We'll use this to build our chat summaries
        List<ChatHistoryDTO> chatSummaries = new ArrayList<>();

        for (ChatMessage message : latestMessages) {
            UUID senderUuid = message.getSender().getId();
            UUID recipientUuid = message.getRecipient().getId();

            // Determine the other participant
            UUID participantUuid = senderUuid.equals(currentUserUuid) ? recipientUuid : senderUuid;

            // Count unread messages for this conversation
            int unreadCount = (int) chatMessageRepository.findConversation(currentUserUuid, participantUuid).stream()
                    .filter(m -> m.getRecipient().getId().equals(currentUserUuid) && !m.isRead())
                    .count();

            // Convert message to DTO
            ChatMessageDTO messageDTO = modelMapper.map(message, ChatMessageDTO.class);
            messageDTO.setSender(message.getSender().getId().toString());
            messageDTO.setRecipient(message.getRecipient().getId().toString());

            // Get participant's username
            String participantUsername = "Unknown";
            try {
                participantUsername = userService.getUserById(participantUuid).getUsername();
            } catch (Exception ex) {
                log.warn("Participant not found: {}", participantUuid);
            }

            // Build the chat summary with just the latest message
            chatSummaries.add(ChatHistoryDTO.builder()
                    .participant(participantUuid.toString())
                    .participantUsername(participantUsername)
                    .messages(List.of(messageDTO)) // Only include the latest message
                    .unreadCount(unreadCount)
                    .build());
        }

        return chatSummaries;
    }

    /**
     * Mark the specified messages as read if they belong to currentUserId (recipient).
     */
    @Transactional
    public ResponseEntity<?> markMessagesAsRead(MarkReadRequest markReadRequest, String currentUserId) {
        UUID currentUserUuid = UUID.fromString(currentUserId);

        List<UUID> messageIds = markReadRequest.getMessageIds();
        if (messageIds == null || messageIds.isEmpty()) {
            return ResponseEntity.badRequest().body("No message IDs provided.");
        }

        // Fetch all by IDs
        List<ChatMessage> unread = chatMessageRepository.findAllById(messageIds).stream()
                // Filter only those where currentUser is the recipient and is not read
                .filter(m -> m.getRecipient().getId().equals(currentUserUuid) && !m.isRead())
                .toList();

        if (unread.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No unread messages found.");
        }

        // Mark them read
        unread.forEach(m -> {
            m.setRead(true);
            m.setReadTimestamp(LocalDateTime.now());
        });
        chatMessageRepository.saveAll(unread);

        // Notify the senders
        notifyReadReceipts(currentUserUuid, unread);

        return ResponseEntity.ok("Messages marked as read.");
    }

    /**
     * STOMP-based version of marking messages as read.
     */
    @Transactional
    public void markAsReadViaStomp(MarkReadRequest markReadRequest, String currentUserId) {
        try {
            UUID currentUserUuid = UUID.fromString(currentUserId);

            List<UUID> messageIds = markReadRequest.getMessageIds();
            if (messageIds == null || messageIds.isEmpty()) {
                return;
            }

            // Same logic as above
            List<ChatMessage> unread = chatMessageRepository.findAllById(messageIds).stream()
                    .filter(m -> m.getRecipient().getId().equals(currentUserUuid) && !m.isRead())
                    .toList();
            if (unread.isEmpty()) {
                return;
            }
            unread.forEach(m -> {
                m.setRead(true);
                m.setReadTimestamp(LocalDateTime.now());
            });
            chatMessageRepository.saveAll(unread);

            notifyReadReceipts(currentUserUuid, unread);

        } catch (Exception e) {
            log.error("Error marking messages as read via STOMP", e);
        }
    }

    /**
     * Sends a read-receipt notification back to each sender, listing which messages were read.
     */
    private void notifyReadReceipts(UUID currentUserUuid, List<ChatMessage> justRead) {
        // Group the just-read messages by their sender
        Map<UUID, List<ChatMessage>> bySender = new HashMap<>();
        for (ChatMessage msg : justRead) {
            UUID senderUuid = msg.getSender().getId();
            bySender.putIfAbsent(senderUuid, new ArrayList<>());
            bySender.get(senderUuid).add(msg);
        }

        // For each sender, build a ReadReceiptNotification and send via STOMP
        for (Map.Entry<UUID, List<ChatMessage>> entry : bySender.entrySet()) {
            UUID senderUuid = entry.getKey();
            List<UUID> ids = entry.getValue().stream().map(ChatMessage::getId).toList();

            ReadReceiptNotification note = new ReadReceiptNotification(
                    currentUserUuid.toString(), // who read them
                    ids,
                    LocalDateTime.now());
            // Send to the sender's user queue
            messagingTemplate.convertAndSendToUser(senderUuid.toString(), "/queue/read-receipts", note);
        }
    }

    @Transactional
    public ResponseEntity<?> deleteConversation(HttpServletRequest request, String participantId) {
        try {
            UUID currentUserUuid = userService.getSessionUser(request).getId();
            UUID participantUuid = UUID.fromString(participantId);

            // Find all messages between the users
            List<ChatMessage> conversation = chatMessageRepository.findConversation(currentUserUuid, participantUuid);

            if (conversation.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No messages found between these users.");
            }

            // Delete the messages
            chatMessageRepository.deleteAll(conversation);

            return ResponseEntity.ok("Conversation deleted successfully.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid user ID format.");
        } catch (Exception e) {
            log.error("Error deleting conversation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while deleting the conversation.");
        }
    }

    // -----------------------------
    // NEW: Send Chat Request
    // -----------------------------
    @Transactional
    public void sendChatRequest(ChatMessageDTO chatRequestDto, String senderId) {
        UUID senderUuid = UUID.fromString(senderId);
        UUID recipientUuid = UUID.fromString(chatRequestDto.getRecipient());

        if (blockService.isUserBlocked(senderUuid, recipientUuid)
                || blockService.isUserBlocked(recipientUuid, senderUuid)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Cannot send chat request. One user has blocked the other.");
        }

        User senderUser = userService.getUserById(senderUuid);
        User recipientUser = userService.getUserById(recipientUuid);

        // Build a ChatRequest entity with status "PENDING"
        ChatRequest request = ChatRequest.builder()
                .requester(senderUser)
                .recipient(recipientUser)
                .ciphertext(chatRequestDto.getCiphertext())
                .encryptedKeyForRecipient(chatRequestDto.getEncryptedKeyForRecipient())
                .encryptedKeyForSender(chatRequestDto.getEncryptedKeyForSender())
                .iv(chatRequestDto.getIv())
                .senderKeyVersion(chatRequestDto.getSenderKeyVersion())
                .recipientKeyVersion(chatRequestDto.getRecipientKeyVersion())
                .timestamp(LocalDateTime.now())
                .status("PENDING")
                .build();
        request = chatRequestRepository.save(request);

        ChatRequestDTO dto = modelMapper.map(request, ChatRequestDTO.class);
        dto.setRequester(senderUser.getId().toString());
        dto.setRecipient(recipientUser.getId().toString());

        // Notify the recipient on their chat request queue
        messagingTemplate.convertAndSendToUser(
                recipientUuid.toString(), "/queue/chatRequests", dto);
    }

    // -----------------------------
    // NEW: Get Pending Chat Requests
    // -----------------------------
    public List<ChatRequestDTO> getPendingChatRequests(String currentUserId) {
        UUID currentUserUuid = UUID.fromString(currentUserId);
        List<ChatRequest> requests = chatRequestRepository.findByRecipient_IdAndStatus(currentUserUuid, "PENDING");
        return requests.stream()
                .map(req -> {
                    ChatRequestDTO dto = modelMapper.map(req, ChatRequestDTO.class);
                    dto.setRequester(req.getRequester().getId().toString());
                    dto.setRecipient(req.getRecipient().getId().toString());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    // -----------------------------
    // NEW: Accept Chat Request
    // -----------------------------
    @Transactional
    public void acceptChatRequest(UUID requestId, String currentUserId) {
        ChatRequest request = chatRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat request not found."));
        if (!request.getRecipient().getId().toString().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to accept this chat request.");
        }
        request.setStatus("ACCEPTED");
        chatRequestRepository.save(request);

        // Optionally, create a ChatMessage from the accepted request so that
        // the conversation appears on the main chat page.
        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setSender(request.getRequester().getId().toString());
        dto.setRecipient(request.getRecipient().getId().toString());
        dto.setCiphertext(request.getCiphertext());
        dto.setEncryptedKeyForRecipient(request.getEncryptedKeyForRecipient());
        dto.setEncryptedKeyForSender(request.getEncryptedKeyForSender());
        dto.setIv(request.getIv());
        dto.setSenderKeyVersion(request.getSenderKeyVersion());
        dto.setRecipientKeyVersion(request.getRecipientKeyVersion());
        dto.setTimestamp(request.getTimestamp());
        dto.setRead(false);
        dto.setClientTempId(null);
        ChatMessage message = modelMapper.map(dto, ChatMessage.class);
        message = chatMessageRepository.save(message);

        // Notify both parties
        ChatMessageDTO toRecipient = modelMapper.map(message, ChatMessageDTO.class);
        toRecipient.setType("INCOMING_MESSAGE");
        ChatMessageDTO toSender = modelMapper.map(message, ChatMessageDTO.class);
        toSender.setType("SENT_MESSAGE");

        messagingTemplate.convertAndSendToUser(
                request.getRecipient().getId().toString(), "/queue/messages", toRecipient);
        messagingTemplate.convertAndSendToUser(
                request.getRequester().getId().toString(), "/queue/sent", toSender);
    }

    // -----------------------------
    // NEW: Reject Chat Request
    // -----------------------------
    @Transactional
    public void rejectChatRequest(UUID requestId, String currentUserId) {
        ChatRequest request = chatRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat request not found."));
        if (!request.getRecipient().getId().toString().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to reject this chat request.");
        }
        request.setStatus("REJECTED");
        chatRequestRepository.save(request);
        log.info("Chat request {} rejected by user {}", requestId, currentUserId);
    }
}
