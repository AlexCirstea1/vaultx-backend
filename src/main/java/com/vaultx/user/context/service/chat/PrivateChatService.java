package com.vaultx.user.context.service.chat;

import com.vaultx.user.context.mapper.ChatMessageMapper;
import com.vaultx.user.context.model.activity.ActivityType;
import com.vaultx.user.context.model.authentication.response.ReadReceiptNotification;
import com.vaultx.user.context.model.messaging.ChatMessage;
import com.vaultx.user.context.model.messaging.dto.ChatHistoryDTO;
import com.vaultx.user.context.model.messaging.dto.ChatMessageDTO;
import com.vaultx.user.context.model.messaging.dto.MarkReadRequest;
import com.vaultx.user.context.model.user.User;
import com.vaultx.user.context.repository.ChatMessageRepository;
import com.vaultx.user.context.service.user.ActivityService;
import com.vaultx.user.context.service.user.BlockService;
import com.vaultx.user.context.service.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrivateChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageMapper chatMessageMapper;
    private final UserService userService;
    private final BlockService blockService;
    private final ActivityService activityService;

    @Transactional
    public void sendPrivateMessage(ChatMessageDTO chatMessageDto, String senderId) {
        UUID senderUuid = UUID.fromString(senderId);
        UUID recipientUuid = UUID.fromString(chatMessageDto.getRecipient());

        checkBlockStatus(senderUuid, recipientUuid);

        User senderUser = userService.getUserById(senderUuid);
        User recipientUser = userService.getUserById(recipientUuid);

        // Build & save entity
        ChatMessage entity = ChatMessage.builder()
                .sender(senderUser)
                .recipient(recipientUser)
                .ciphertext(chatMessageDto.getCiphertext())
                .encryptedKeyForRecipient(chatMessageDto.getEncryptedKeyForRecipient())
                .encryptedKeyForSender(chatMessageDto.getEncryptedKeyForSender())
                .iv(chatMessageDto.getIv())
                .senderKeyVersion(chatMessageDto.getSenderKeyVersion())
                .recipientKeyVersion(chatMessageDto.getRecipientKeyVersion())
                .timestamp(LocalDateTime.now())
                .isRead(false)
                .oneTime(chatMessageDto.isOneTime())
                .build();
        entity = chatMessageRepository.save(entity);

        // Prepare and send messages
        sendMessageNotifications(entity, chatMessageDto);
    }

    public List<ChatMessageDTO> getConversation(String currentUserId, String participantId) {
        UUID currentUserUuid = UUID.fromString(currentUserId);
        UUID participantUuid = UUID.fromString(participantId);

        List<ChatMessage> conversation = chatMessageRepository.findConversation(currentUserUuid, participantUuid);

        return conversation.stream()
                .map(this::enhanceChatMessageDto)
                .toList();
    }

    private ChatMessageDTO enhanceChatMessageDto(ChatMessage entity) {
        ChatMessageDTO dto = chatMessageMapper.toDto(entity);
        if (entity.isRead() || entity.getReadTimestamp() != null) {
            dto.setRead(true);
        }
        dto.setReadTimestamp(entity.getReadTimestamp());
        return dto;
    }

    public List<ChatHistoryDTO> getChatSummaries(String currentUserId) {
        UUID currentUserUuid = UUID.fromString(currentUserId);

        // Find the latest message for each conversation
        List<ChatMessage> latestMessages = chatMessageRepository.findLatestMessagesByUser(currentUserUuid);
        List<ChatHistoryDTO> chatSummaries = new ArrayList<>();

        for (ChatMessage message : latestMessages) {
            UUID senderUuid = message.getSender().getId();
            UUID recipientUuid = message.getRecipient().getId();

            // Determine the other participant
            UUID participantUuid = senderUuid.equals(currentUserUuid) ? recipientUuid : senderUuid;

            // Count unread messages for this conversation
            int unreadCount = countUnreadMessages(currentUserUuid, participantUuid);

            // Get participant's username
            String participantUsername = getParticipantUsername(participantUuid);

            // Build the chat summary
            chatSummaries.add(ChatHistoryDTO.builder()
                    .participant(participantUuid.toString())
                    .participantUsername(participantUsername)
                    .messages(List.of(chatMessageMapper.toDto(message)))
                    .unreadCount(unreadCount)
                    .build());
        }

        return chatSummaries;
    }

    @Transactional
    public ResponseEntity<?> markMessagesAsRead(MarkReadRequest markReadRequest, String currentUserId) {
        UUID currentUserUuid = UUID.fromString(currentUserId);

        List<UUID> messageIds = markReadRequest.getMessageIds();
        if (messageIds == null || messageIds.isEmpty()) {
            return ResponseEntity.badRequest().body("No message IDs provided.");
        }

        // Fetch messages to mark as read
        List<ChatMessage> unread = findUnreadMessages(messageIds, currentUserUuid);
        if (unread.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body("No unread messages found.");
        }

        // Process messages
        processReadMessages(unread, currentUserUuid);

        return ResponseEntity.ok("Messages marked as read.");
    }

    @Transactional
    public void markAsReadViaStomp(MarkReadRequest markReadRequest, String currentUserId) {
        try {
            UUID currentUserUuid = UUID.fromString(currentUserId);

            List<UUID> messageIds = markReadRequest.getMessageIds();
            if (messageIds == null || messageIds.isEmpty()) {
                return;
            }

            // Find unread messages
            List<ChatMessage> unread = findUnreadMessages(messageIds, currentUserUuid);
            if (!unread.isEmpty()) {
                processReadMessages(unread, currentUserUuid);
            }
        } catch (Exception e) {
            log.error("Error marking messages as read via STOMP", e);
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
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No messages found between users.");
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

    // Helper methods

    private void checkBlockStatus(UUID senderUuid, UUID recipientUuid) {
        if (blockService.isUserBlocked(senderUuid, recipientUuid)
                || blockService.isUserBlocked(recipientUuid, senderUuid)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Cannot send message. One user has blocked the other.");
        }
    }

    private void sendMessageNotifications(ChatMessage entity, ChatMessageDTO originalDto) {
        // Convert to base DTO
        ChatMessageDTO baseDto = chatMessageMapper.toDto(entity);
        baseDto.setClientTempId(originalDto.getClientTempId());

        // Create "INCOMING_MESSAGE" + "SENT_MESSAGE" variants
        ChatMessageDTO toRecipient = chatMessageMapper.clone(baseDto);
        toRecipient.setType("INCOMING_MESSAGE");

        ChatMessageDTO toSender = chatMessageMapper.clone(baseDto);
        toSender.setType("SENT_MESSAGE");

        // Send to WebSocket destinations for both users
        messagingTemplate.convertAndSendToUser(
                entity.getRecipient().getId().toString(), "/queue/messages", toRecipient);
        messagingTemplate.convertAndSendToUser(entity.getSender().getId().toString(), "/queue/sent", toSender);
    }

    private int countUnreadMessages(UUID userId, UUID otherUserId) {
        return chatMessageRepository.countByRecipientIdAndSenderIdAndIsReadFalse(userId, otherUserId);
    }

    private String getParticipantUsername(UUID participantId) {
        try {
            User participant = userService.getUserById(participantId);
            return participant.getUsername();
        } catch (Exception e) {
            log.error("Error retrieving username for user {}", participantId, e);
            return "Unknown User";
        }
    }

    private List<ChatMessage> findUnreadMessages(List<UUID> messageIds, UUID recipientId) {
        return chatMessageRepository.findByIdInAndRecipientIdAndIsReadFalse(messageIds, recipientId);
    }

    private void processReadMessages(List<ChatMessage> messages, UUID currentUserId) {
        if (messages.isEmpty()) return;

        LocalDateTime readTime = LocalDateTime.now();

        List<ChatMessage> oneTimeMessages = messages.stream()
                .filter(ChatMessage::isOneTime)
                .toList();

        messages.forEach(m -> {
            m.setRead(true);
            m.setReadTimestamp(readTime);
        });
        chatMessageRepository.saveAll(messages);

        Map<UUID, List<UUID>> messagesBySender = new HashMap<>();
        for (ChatMessage msg : messages) {
            UUID senderId = msg.getSender().getId();
            messagesBySender.computeIfAbsent(senderId, k -> new ArrayList<>()).add(msg.getId());
        }

        messagesBySender.forEach((senderId, msgIds) -> {
            ReadReceiptNotification notification = new ReadReceiptNotification();
            notification.setReaderId(currentUserId.toString());
            notification.setMessageIds(msgIds);
            notification.setReadTimestamp(LocalDateTime.now());

            messagingTemplate.convertAndSendToUser(senderId.toString(), "/queue/read-receipts", notification);
        });

        if (!oneTimeMessages.isEmpty()) {
            chatMessageRepository.deleteAll(oneTimeMessages);
            activityService.logActivity(
                    userService.getUserById(currentUserId),
                    ActivityType.USER_ACTION,
                    "Deleted one-time messages after reading",
                    false,
                    "Messages deleted: " + oneTimeMessages.size());
        }
    }
}
