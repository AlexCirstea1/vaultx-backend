package com.vaultx.user.context.service.chat;

import com.vaultx.user.context.mapper.ChatMessageMapper;
import com.vaultx.user.context.model.activity.ActivityType;
import com.vaultx.user.context.model.authentication.response.ReadReceiptNotification;
import com.vaultx.user.context.model.file.ChatFile;
import com.vaultx.user.context.model.file.FileInfo;
import com.vaultx.user.context.model.messaging.ChatMessage;
import com.vaultx.user.context.model.messaging.MessageType;
import com.vaultx.user.context.model.messaging.dto.ChatHistoryDTO;
import com.vaultx.user.context.model.messaging.dto.ChatMessageDTO;
import com.vaultx.user.context.model.messaging.dto.MarkReadRequest;
import com.vaultx.user.context.model.user.User;
import com.vaultx.user.context.repository.ChatFileRepository;
import com.vaultx.user.context.repository.ChatMessageRepository;
import com.vaultx.user.context.service.user.ActivityService;
import com.vaultx.user.context.service.user.BlockService;
import com.vaultx.user.context.service.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
    private final ChatFileRepository chatFileRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    // Cache TTL in hours
    private static final int CONVERSATION_CACHE_TTL = 24;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendPrivateMessage(ChatMessageDTO dto, String senderId) {

        UUID senderUuid = UUID.fromString(senderId);
        UUID recipientUuid = UUID.fromString(dto.getRecipient());
        checkBlockStatus(senderUuid, recipientUuid);

        User senderUser = userService.getUserById(senderUuid);
        User recipientUser = userService.getUserById(recipientUuid);

        boolean isFile = dto.getFile() != null;
        MessageType type = isFile ? MessageType.FILE : MessageType.NORMAL;

        ChatMessage msg = ChatMessage.builder()
                .sender(senderUser)
                .recipient(recipientUser)
                .ciphertext(isFile ? "__FILE__" : dto.getCiphertext())
                .encryptedKeyForSender(dto.getEncryptedKeyForSender())
                .encryptedKeyForRecipient(dto.getEncryptedKeyForRecipient())
                .iv(dto.getIv())
                .senderKeyVersion(dto.getSenderKeyVersion())
                .recipientKeyVersion(dto.getRecipientKeyVersion())
                .messageType(type)
                .timestamp(LocalDateTime.now())
                .isRead(false)
                .oneTime(dto.isOneTime())
                .build();

        /* persist ChatMessage FIRST – it becomes managed */
        msg = chatMessageRepository.save(msg);

        /* add ChatFile only to the managed message; DO NOT save separately */
        if (isFile) {
            FileInfo fi = dto.getFile();
            ChatFile cf = ChatFile.builder()
                    .id(fi.getFileId())               // client-generated UUID
                    .message(msg)                     // owning side
                    .fileName(fi.getFileName())
                    .mimeType(fi.getMimeType())
                    .sizeBytes(fi.getSizeBytes())
                    .iv(dto.getIv())
                    .encryptedKeySender(dto.getEncryptedKeyForSender())
                    .encryptedKeyRecipient(dto.getEncryptedKeyForRecipient())
                    .senderKeyVersion(dto.getSenderKeyVersion())
                    .recipientKeyVersion(dto.getRecipientKeyVersion())
                    .build();

            msg.setFile(cf);                          // cascade-PERSIST will save it
        }

        sendMessageNotifications(msg, dto);
        invalidateConversationCache(senderUuid, recipientUuid);
    }


    public List<ChatMessageDTO> getConversation(String currentUserId, String participantId) {
        UUID currentUserUuid = UUID.fromString(currentUserId);
        UUID participantUuid = UUID.fromString(participantId);

        // Try to get conversation from cache first
        String cacheKey = getConversationCacheKey(currentUserUuid, participantUuid);
        @SuppressWarnings("unchecked")
        List<ChatMessageDTO> cachedConversation = (List<ChatMessageDTO>) redisTemplate.opsForValue().get(cacheKey);

        if (cachedConversation != null) {
            log.info("Retrieved conversation from cache for users {} and {}", currentUserId, participantId);
            return cachedConversation;
        }

        // If not in cache, get from database
        log.info("Cache miss for conversation between {} and {}, fetching from database", currentUserId, participantId);
        List<ChatMessage> conversation = chatMessageRepository.findConversation(currentUserUuid, participantUuid);
        List<ChatMessageDTO> result = conversation.stream().map(this::enhanceChatMessageDto).toList();

        // Cache the result
        redisTemplate.opsForValue().set(cacheKey, result, CONVERSATION_CACHE_TTL, TimeUnit.HOURS);

        return result;
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

        // Invalidate conversation caches for each sender-recipient pair
        unread.forEach(message -> {
            invalidateConversationCache(message.getSender().getId(), message.getRecipient().getId());
        });

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

                // Invalidate conversation caches
                unread.forEach(message -> {
                    invalidateConversationCache(message.getSender().getId(), message.getRecipient().getId());
                });
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

            // Invalidate cache
            invalidateConversationCache(currentUserUuid, participantUuid);

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

        List<ChatMessage> oneTimeMessages =
                messages.stream().filter(ChatMessage::isOneTime).toList();

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

    @Transactional
    public ResponseEntity<?> deleteMessage(UUID messageId, UUID currentUserId) {
        // Find the message
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        // Check if user is authorized (must be the sender)
        if (!message.getSender().getId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete messages you've sent");
        }

        // If message has associated file, delete it first
        if (message.getMessageType() == MessageType.FILE) {
            chatFileRepository.deleteByMessageId(message.getId());
        }

        // Delete the message
        chatMessageRepository.delete(message);

        // Invalidate cache
        invalidateConversationCache(message.getSender().getId(), message.getRecipient().getId());

        // Notify the recipient about message deletion
        notifyMessageDeletion(message);

        return ResponseEntity.ok("Message deleted successfully");
    }

    private void notifyMessageDeletion(ChatMessage message) {
        try {
            Map<String, Object> notification = Map.of(
                    "type", "MESSAGE_DELETED",
                    "messageId", message.getId().toString()
            );

            messagingTemplate.convertAndSendToUser(
                    message.getRecipient().getId().toString(),
                    "/queue/notifications",
                    notification
            );
        } catch (Exception e) {
            log.error("Failed to send message deletion notification", e);
        }
    }

    // Cache utility methods

    private String getConversationCacheKey(UUID user1Id, UUID user2Id) {
        // Ensure consistent key regardless of which user is first
        UUID[] ids = new UUID[]{user1Id, user2Id};
        Arrays.sort(ids, Comparator.comparing(UUID::toString));
        return "chat:conversation:" + ids[0] + ":" + ids[1];
    }

    private void invalidateConversationCache(UUID user1Id, UUID user2Id) {
        String cacheKey = getConversationCacheKey(user1Id, user2Id);
        redisTemplate.delete(cacheKey);
        log.info("Invalidated conversation cache for users {} and {}", user1Id, user2Id);
    }
}