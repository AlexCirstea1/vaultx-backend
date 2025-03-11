package ro.cloud.security.user.context.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import ro.cloud.security.user.context.model.authentication.request.MarkReadRequest;
import ro.cloud.security.user.context.model.authentication.response.ReadReceiptNotification;
import ro.cloud.security.user.context.model.messaging.dto.ChatHistoryDTO;
import ro.cloud.security.user.context.model.messaging.ChatMessage;
import ro.cloud.security.user.context.model.messaging.dto.ChatMessageDTO;
import ro.cloud.security.user.context.repository.ChatMessageRepository;
import ro.cloud.security.user.context.service.authentication.UserService;

import java.time.LocalDateTime;
import java.util.*;

@Service
@AllArgsConstructor
@Slf4j
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ModelMapper modelMapper;
    private final ObjectMapper objectMapper;
    private final UserService userService;

    @Transactional
    public void sendPrivateMessage(ChatMessageDTO chatMessage, String senderId) {
        ChatMessage entity = ChatMessage.builder()
                .sender(senderId)
                .recipient(chatMessage.getRecipient())
                .content(chatMessage.getContent())
                .timestamp(LocalDateTime.now())
                .isRead(false)
                .build();
        entity = chatMessageRepository.save(entity);

        ChatMessageDTO outgoing = modelMapper.map(entity, ChatMessageDTO.class);
        outgoing.setClientTempId(chatMessage.getClientTempId());

        messagingTemplate.convertAndSendToUser(entity.getRecipient(), "/queue/messages", outgoing);
        messagingTemplate.convertAndSendToUser(senderId, "/queue/sent", outgoing);
    }

    public List<ChatMessageDTO> getConversation(String currentUserId, String participantId) {
        List<ChatMessage> conversation = chatMessageRepository.findConversation(currentUserId, participantId);
        List<ChatMessageDTO> dtos = new ArrayList<>();
        for (ChatMessage msg : conversation) {
            dtos.add(modelMapper.map(msg, ChatMessageDTO.class));
        }
        return dtos;
    }

    public List<ChatHistoryDTO> getChatHistory(String currentUserId) {
        List<ChatMessage> messages = chatMessageRepository.findBySenderOrRecipient(currentUserId, currentUserId);
        Map<String, List<ChatMessage>> grouped = new HashMap<>();
        for (ChatMessage m : messages) {
            String other = m.getSender().equals(currentUserId) ? m.getRecipient() : m.getSender();
            grouped.putIfAbsent(other, new ArrayList<>());
            grouped.get(other).add(m);
        }
        List<ChatHistoryDTO> chatHistories = new ArrayList<>();
        for (Map.Entry<String, List<ChatMessage>> entry : grouped.entrySet()) {
            String participantId = entry.getKey();
            List<ChatMessage> participantMessages = entry.getValue();
            int unreadCount = (int) participantMessages.stream()
                    .filter(m -> m.getRecipient().equals(currentUserId) && !m.isRead())
                    .count();
            List<ChatMessageDTO> messageDTOs = new ArrayList<>();
            for (ChatMessage cm : participantMessages) {
                messageDTOs.add(modelMapper.map(cm, ChatMessageDTO.class));
            }
            String participantUsername = "Unknown";
            try {
                participantUsername = userService.getUserById(UUID.fromString(participantId)).getUsername();
            } catch (Exception ex) {
                log.warn("Participant not found: {}", participantId);
            }
            chatHistories.add(ChatHistoryDTO.builder()
                    .participant(participantId)
                    .participantUsername(participantUsername)
                    .messages(messageDTOs)
                    .unreadCount(unreadCount)
                    .build());
        }
        return chatHistories;
    }

    @Transactional
    public ResponseEntity<?> markMessagesAsRead(MarkReadRequest markReadRequest, String currentUserId) {
        List<UUID> messageIds = markReadRequest.getMessageIds();
        if (messageIds == null || messageIds.isEmpty()) {
            return ResponseEntity.badRequest().body("No message IDs provided.");
        }
        List<ChatMessage> unread = chatMessageRepository.findAllById(messageIds).stream()
                .filter(m -> m.getRecipient().equals(currentUserId) && !m.isRead())
                .toList();
        if (unread.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No unread messages found.");
        }
        unread.forEach(m -> {
            m.setRead(true);
            m.setReadTimestamp(LocalDateTime.now());
        });
        chatMessageRepository.saveAll(unread);
        notifyReadReceipts(currentUserId, unread);
        return ResponseEntity.ok("Messages marked as read.");
    }

    @Transactional
    public void markAsReadViaStomp(String payload, String currentUserId) {
        try {
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
            List<ChatMessage> unread = chatMessageRepository.findAllById(messageIds).stream()
                    .filter(m -> m.getRecipient().equals(currentUserId) && !m.isRead())
                    .toList();
            if (unread.isEmpty()) {
                return;
            }
            unread.forEach(m -> {
                m.setRead(true);
                m.setReadTimestamp(LocalDateTime.now());
            });
            chatMessageRepository.saveAll(unread);
            notifyReadReceipts(currentUserId, unread);
        } catch (Exception e) {
            log.error("Error marking messages as read via STOMP", e);
        }
    }

    private void notifyReadReceipts(String currentUserId, List<ChatMessage> justRead) {
        Map<String, List<ChatMessage>> bySender = new HashMap<>();
        for (ChatMessage msg : justRead) {
            bySender.putIfAbsent(msg.getSender(), new ArrayList<>());
            bySender.get(msg.getSender()).add(msg);
        }
        for (Map.Entry<String, List<ChatMessage>> entry : bySender.entrySet()) {
            String senderId = entry.getKey();
            List<UUID> ids = entry.getValue().stream().map(ChatMessage::getId).toList();
            ReadReceiptNotification note = new ReadReceiptNotification(currentUserId, ids, LocalDateTime.now());
            messagingTemplate.convertAndSendToUser(senderId, "/queue/read-receipts", note);
        }
    }
}