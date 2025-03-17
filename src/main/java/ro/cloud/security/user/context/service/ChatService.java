package ro.cloud.security.user.context.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import ro.cloud.security.user.context.model.EventType;
import ro.cloud.security.user.context.model.authentication.request.MarkReadRequest;
import ro.cloud.security.user.context.model.authentication.response.ReadReceiptNotification;
import ro.cloud.security.user.context.model.messaging.ChatMessage;
import ro.cloud.security.user.context.model.messaging.dto.ChatHistoryDTO;
import ro.cloud.security.user.context.model.messaging.dto.ChatMessageDTO;
import ro.cloud.security.user.context.model.user.User;
import ro.cloud.security.user.context.repository.ChatMessageRepository;
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

    /**
     * Send a private message from senderId to chatMessage.getRecipient().
     * Now that ChatMessage expects a User object for sender/recipient,
     * we fetch them from userService by UUID.
     */
    @Transactional
    public void sendPrivateMessage(ChatMessageDTO chatMessage, String senderId) {
        // 1) Convert senderId + chatMessage.getRecipient() to UUID
        UUID senderUuid = UUID.fromString(senderId);
        UUID recipientUuid = UUID.fromString(chatMessage.getRecipient());

        // 2) Fetch User objects
        User senderUser = userService.getUserById(senderUuid);
        User recipientUser = userService.getUserById(recipientUuid);

        // 3) Check if new conversation
        List<ChatMessage> existingConversation = chatMessageRepository.findConversation(senderUuid, recipientUuid);
        boolean isNewConversation = existingConversation.isEmpty();
        if (isNewConversation) {
            blockchainService.recordDIDEvent(senderUser.getId(), senderUser.getPublicDid(), EventType.PAIRING);
            blockchainService.recordDIDEvent(recipientUser.getId(), recipientUser.getPublicDid(), EventType.PAIRING);
            log.info("Recorded pairing event on blockchain for users: {} and {}", senderUuid, recipientUuid);
        }

        // 4) Build and save ChatMessage entity
        ChatMessage entity = ChatMessage.builder()
                .sender(senderUser)
                .recipient(recipientUser)
                .content(chatMessage.getContent())
                .timestamp(LocalDateTime.now())
                .isRead(false)
                .build();
        entity = chatMessageRepository.save(entity);

        // 5) Convert the DB entity to a base ChatMessageDTO
        ChatMessageDTO baseDto = modelMapper.map(entity, ChatMessageDTO.class);
        // Overwrite with actual IDs, not entire user objects
        baseDto.setSender(entity.getSender().getId().toString());
        baseDto.setRecipient(entity.getRecipient().getId().toString());
        // Pass along clientTempId if present
        baseDto.setClientTempId(chatMessage.getClientTempId());

        // 6) Create two versions: one for recipient, one for sender
        ChatMessageDTO toRecipient = cloneMessageDTO(baseDto);
        toRecipient.setType("INCOMING_MESSAGE"); // label it
        // (sender, recipient) remain the same: (me -> them)

        ChatMessageDTO toSender = cloneMessageDTO(baseDto);
        toSender.setType("SENT_MESSAGE");        // label it
        // (sender, recipient) remain the same: (me -> them)

        // 7) Broadcast
        messagingTemplate.convertAndSendToUser(
                recipientUuid.toString(), "/queue/messages", toRecipient);
        messagingTemplate.convertAndSendToUser(
                senderUuid.toString(), "/queue/messages", toSender);
    }

    private ChatMessageDTO cloneMessageDTO(ChatMessageDTO original) {
        ChatMessageDTO copy = new ChatMessageDTO();
        copy.setId(original.getId());
        copy.setSender(original.getSender());
        copy.setRecipient(original.getRecipient());
        copy.setContent(original.getContent());
        copy.setTimestamp(original.getTimestamp());
        copy.setRead(original.isRead());
        copy.setReadTimestamp(original.getReadTimestamp());
        copy.setClientTempId(original.getClientTempId());
        // We'll set 'type' externally
        return copy;
    }


    /**
     * Returns the conversation (messages) between currentUserId and participantId.
     */
    public List<ChatMessageDTO> getConversation(String currentUserId, String participantId) {
        // Convert to UUID
        UUID currentUserUuid = UUID.fromString(currentUserId);
        UUID participantUuid = UUID.fromString(participantId);

        // Get conversation messages
        List<ChatMessage> conversation = chatMessageRepository.findConversation(currentUserUuid, participantUuid);

        // Convert each ChatMessage to DTO with explicit mapping for user IDs
        return conversation.stream()
                .map(msg -> {
                    ChatMessageDTO dto = modelMapper.map(msg, ChatMessageDTO.class);
                    // Set user IDs as strings instead of using full user objects
                    dto.setSender(msg.getSender().getId().toString());
                    dto.setRecipient(msg.getRecipient().getId().toString());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns all private chats for the given user, grouped by "other participant."
     */
    public List<ChatHistoryDTO> getChatHistory(String currentUserId) {
        UUID currentUserUuid = UUID.fromString(currentUserId);

        // Find all messages where user is sender or recipient
        List<ChatMessage> messages = chatMessageRepository.findBySenderOrRecipient(currentUserUuid);

        // We'll group them by the "other participant" user ID
        // so we can produce a ChatHistoryDTO for each distinct conversation
        Map<UUID, List<ChatMessage>> grouped = new HashMap<>();

        for (ChatMessage m : messages) {
            UUID senderUuid = m.getSender().getId();
            UUID recipientUuid = m.getRecipient().getId();

            // If current user is sender, "other" is recipient, else "other" is sender
            UUID otherId = senderUuid.equals(currentUserUuid) ? recipientUuid : senderUuid;

            grouped.putIfAbsent(otherId, new ArrayList<>());
            grouped.get(otherId).add(m);
        }

        // Build ChatHistoryDTO for each distinct "other"
        List<ChatHistoryDTO> chatHistories = new ArrayList<>();

        for (Map.Entry<UUID, List<ChatMessage>> entry : grouped.entrySet()) {
            UUID participantUuid = entry.getKey();
            List<ChatMessage> participantMessages = entry.getValue();

            // Count unread
            int unreadCount = (int) participantMessages.stream()
                    .filter(m -> m.getRecipient().getId().equals(currentUserUuid) && !m.isRead())
                    .count();

            // Convert messages to DTO
            List<ChatMessageDTO> messageDTOs = participantMessages.stream()
                    .map(m -> modelMapper.map(m, ChatMessageDTO.class))
                    .collect(Collectors.toList());

            // Attempt to load participant's username
            String participantUsername = "Unknown";
            try {
                participantUsername = userService.getUserById(participantUuid).getUsername();
            } catch (Exception ex) {
                log.warn("Participant not found: {}", participantUuid);
            }

            // Build the chat history
            chatHistories.add(ChatHistoryDTO.builder()
                    .participant(participantUuid.toString()) // Keep as string for the DTO
                    .participantUsername(participantUsername)
                    .messages(messageDTOs)
                    .unreadCount(unreadCount)
                    .build());
        }

        return chatHistories;
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
    public void markAsReadViaStomp(String payload, String currentUserId) {
        try {
            UUID currentUserUuid = UUID.fromString(currentUserId);

            JsonNode node = objectMapper.readTree(payload);
            List<UUID> messageIds = new ArrayList<>();
            if (node.has("messageIds")) {
                for (JsonNode idNode : node.get("messageIds")) {
                    messageIds.add(UUID.fromString(idNode.asText()));
                }
            }
            if (messageIds.isEmpty()) {
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
}
