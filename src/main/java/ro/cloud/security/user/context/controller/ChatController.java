package ro.cloud.security.user.context.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import ro.cloud.security.user.context.model.ChatMessage;
import ro.cloud.security.user.context.model.User;
import ro.cloud.security.user.context.model.dto.*;
import ro.cloud.security.user.context.repository.ChatMessageRepository;
import ro.cloud.security.user.context.service.UserService;

@RestController
@Controller
@RequestMapping
@AllArgsConstructor
@Slf4j
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageRepository chatMessageRepository;
    private final UserService userService;
    private final ModelMapper modelMapper;
    private final ObjectMapper objectMapper;

    /**
     * STOMP endpoint to send a private message.
     * - Saves the message in DB first.
     * - Broadcasts to BOTH the recipient (/queue/messages) and the sender (/queue/sent).
     *   so the sender can update their local state with the final ID/timestamp from the DB.
     */
    @MessageMapping("/sendPrivateMessage")
    public void sendPrivateMessage(ChatMessage chatMessage, Authentication authentication) {
        if (chatMessage == null || chatMessage.getRecipient() == null || chatMessage.getContent() == null) {
            throw new IllegalArgumentException("Invalid chat message format");
        }

        // Extract sender from JWT
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String senderId = jwt.getSubject();
        chatMessage.setSender(senderId);

        // Build the entity to save
        ChatMessage entity = ChatMessage.builder()
                .sender(senderId)
                .recipient(chatMessage.getRecipient())
                .content(chatMessage.getContent())
                .timestamp(LocalDateTime.now())
                .isRead(false)
                .build();

        // Save to DB
        entity = chatMessageRepository.save(entity);

        log.info("Message [id={}] from {} to {} saved. Content length={}",
                entity.getId(), senderId, chatMessage.getRecipient(), entity.getContent().length());

        // Create the DTO to broadcast
        ChatMessageDTO outgoing = ChatMessageDTO.builder()
                .id(entity.getId())
                .sender(entity.getSender())
                .recipient(entity.getRecipient())
                .content(entity.getContent())
                .timestamp(entity.getTimestamp())
                .isRead(entity.isRead())
                .readTimestamp(entity.getReadTimestamp())
                // Echo the clientTempId from inbound chatMessage
                .clientTempId(chatMessage.getClientTempId())
                .build();

        // 1) Send to recipient
        messagingTemplate.convertAndSendToUser(entity.getRecipient(), "/queue/messages", outgoing);

        // 2) Send "SENT_MESSAGE" to sender (the same payload, but your client can interpret differently)
        messagingTemplate.convertAndSendToUser(entity.getSender(), "/queue/sent", outgoing);
    }

    /**
     * REST endpoint to fetch message history between the authenticated user and a specific participant.
     */
    @GetMapping("/api/messages")
    public ResponseEntity<List<ChatMessageDTO>> getMessages(
            @RequestParam("recipientId") String participantId, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String currentUserId = jwt.getSubject();

        // fetch conversation from DB
        List<ChatMessage> conversation = chatMessageRepository.findConversation(currentUserId, participantId);

        // map to DTO
        List<ChatMessageDTO> dtos = conversation.stream()
                .map(msg -> modelMapper.map(msg, ChatMessageDTO.class))
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * REST endpoint to fetch ALL chat histories for the current user, grouped by participant.
     */
    @GetMapping("/api/chats")
    public ResponseEntity<?> getChatHistory(HttpServletRequest request) {
        try {
            User currentUser = userService.getSessionUser(request);
            String currentUserId = currentUser.getId().toString();

            // fetch all messages (sent or received by currentUserId)
            List<ChatMessage> messages = chatMessageRepository.findBySenderOrRecipient(currentUserId, currentUserId);

            // group by the "other participant"
            Map<String, List<ChatMessage>> grouped = messages.stream().collect(Collectors.groupingBy(m -> {
                return m.getSender().equals(currentUserId) ? m.getRecipient() : m.getSender();
            }));

            List<ChatHistoryDTO> chatHistories = new ArrayList<>();
            for (Map.Entry<String, List<ChatMessage>> entry : grouped.entrySet()) {
                String participantId = entry.getKey();
                List<ChatMessage> participantMessages = entry.getValue();

                // unread count
                int unreadCount = (int) participantMessages.stream()
                        .filter(m -> m.getRecipient().equals(currentUserId) && !m.isRead())
                        .count();

                // map to ChatMessageDTO
                List<ChatMessageDTO> messageDTOs = participantMessages.stream()
                        .map(m -> modelMapper.map(m, ChatMessageDTO.class))
                        .collect(Collectors.toList());

                // fetch participant's username or fallback
                String participantUsername = "Unknown";
                try {
                    participantUsername = userService
                            .getUserById(UUID.fromString(participantId))
                            .getUsername();
                } catch (Exception ex) {
                    log.warn("Participant with ID {} not found.", participantId);
                }

                ChatHistoryDTO historyDTO = ChatHistoryDTO.builder()
                        .participant(participantId)
                        .participantUsername(participantUsername)
                        .messages(messageDTOs)
                        .unreadCount(unreadCount)
                        .build();

                chatHistories.add(historyDTO);
            }

            return ResponseEntity.ok(chatHistories);
        } catch (Exception e) {
            log.error("Error fetching chat history", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while fetching chat history.");
        }
    }

    /**
     * (A) REST API to mark messages as read and notify senders
     */
    @PostMapping("/api/chats/mark-as-read")
    @Transactional
    public ResponseEntity<?> markMessagesAsRead(
            @RequestBody MarkReadRequest markReadRequest, Authentication authentication) {
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String currentUserId = jwt.getSubject();

            if (markReadRequest.getMessageIds() == null
                    || markReadRequest.getMessageIds().isEmpty()) {
                return ResponseEntity.badRequest().body("No message IDs provided.");
            }

            List<UUID> messageIds = markReadRequest.getMessageIds();

            // Get the relevant messages
            List<ChatMessage> unreadMessages = chatMessageRepository.findAllById(messageIds).stream()
                    .filter(m -> m.getRecipient().equals(currentUserId) && !m.isRead())
                    .collect(Collectors.toList());

            if (unreadMessages.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No unread messages found for those IDs.");
            }

            // Mark them as read
            unreadMessages.forEach(m -> {
                m.setRead(true);
                m.setReadTimestamp(LocalDateTime.now());
            });
            chatMessageRepository.saveAll(unreadMessages);

            // Notify the senders
            notifyReadReceipts(currentUserId, unreadMessages);

            return ResponseEntity.ok("Messages marked as read.");
        } catch (Exception e) {
            log.error("Error marking messages as read", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while marking messages as read.");
        }
    }

    /**
     * (B) STOMP endpoint (optional) to mark messages as read
     *     - The frontend can call this for instant read receipts without a separate REST call.
     */
    @MessageMapping("/markAsRead")
    @Transactional
    public void markAsReadViaStomp(String payload, Authentication authentication) {
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String currentUserId = jwt.getSubject();

            // Expect a JSON like {"messageIds": ["id1", "id2", ...]}
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

            List<ChatMessage> unreadMessages = chatMessageRepository.findAllById(messageIds).stream()
                    .filter(m -> m.getRecipient().equals(currentUserId) && !m.isRead())
                    .collect(Collectors.toList());

            if (unreadMessages.isEmpty()) {
                return;
            }

            unreadMessages.forEach(m -> {
                m.setRead(true);
                m.setReadTimestamp(LocalDateTime.now());
            });
            chatMessageRepository.saveAll(unreadMessages);

            // Notify senders
            notifyReadReceipts(currentUserId, unreadMessages);

        } catch (Exception e) {
            log.error("Error marking messages as read via STOMP", e);
        }
    }

    /**
     * Helper to notify senders that messages were read.
     */
    private void notifyReadReceipts(String currentUserId, List<ChatMessage> justRead) {
        // Group messages by sender
        Map<String, List<ChatMessage>> bySender =
                justRead.stream().collect(Collectors.groupingBy(ChatMessage::getSender));

        // For each sender, send a read-receipt event
        bySender.forEach((senderId, messages) -> {
            List<UUID> ids = messages.stream().map(ChatMessage::getId).toList();
            LocalDateTime readTime = LocalDateTime.now();

            ReadReceiptNotification notification = new ReadReceiptNotification(currentUserId, ids, readTime);

            // The sender receives the read-receipt on /queue/read-receipts
            messagingTemplate.convertAndSendToUser(senderId, "/queue/read-receipts", notification);

            log.info("Notified sender={} that user={} read message IDs={}", senderId, currentUserId, ids);
        });
    }

    /**
     * STOMP endpoint for user search, e.g., "/app/userSearch"
     */
    @MessageMapping("/userSearch")
    public void handleUserSearch(String payload, Authentication authentication) {
        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            String type = jsonNode.get("type").asText();
            if (!"USER_SEARCH".equals(type)) return;

            String query = jsonNode.get("payload").get("query").asText();
            if (query == null || query.trim().isEmpty()) return;

            Jwt jwt = (Jwt) authentication.getPrincipal();
            String currentUserId = jwt.getSubject();

            List<UserSearchDTO> users = userService.searchUsers(query, currentUserId);

            // Return results
            messagingTemplate.convertAndSendToUser(
                    currentUserId, "/queue/userSearchResults", new MessageResponse("USER_SEARCH_RESULTS", users));
        } catch (Exception e) {
            log.error("Error handling user search: ", e);
        }
    }
}
