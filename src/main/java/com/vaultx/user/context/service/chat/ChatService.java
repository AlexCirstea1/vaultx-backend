package com.vaultx.user.context.service.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultx.user.context.model.authentication.response.MessageResponse;
import com.vaultx.user.context.model.messaging.GroupChat;
import com.vaultx.user.context.model.messaging.dto.*;
import com.vaultx.user.context.service.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * A facade service that orchestrates calls to specialized services
 * related to chat functionality.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final PrivateChatService privateChatService;
    private final GroupChatService groupChatService;
    private final ChatRequestService chatRequestService;

    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    // Private Message Operations

    public void sendPrivateMessage(ChatMessageDTO chatMessage, String senderId) {
        privateChatService.sendPrivateMessage(chatMessage, senderId);
    }

    public List<ChatMessageDTO> getConversation(String currentUserId, String participantId) {
        return privateChatService.getConversation(currentUserId, participantId);
    }

    public ResponseEntity<?> getChatSummaries(String currentUserId) {
        try {
            return ResponseEntity.ok(privateChatService.getChatSummaries(currentUserId));
        } catch (Exception e) {
            log.error("Error fetching chat summaries", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while fetching chat summaries.");
        }
    }

    public ResponseEntity<?> markMessagesAsRead(MarkReadRequest markReadRequest, String currentUserId) {
        return privateChatService.markMessagesAsRead(markReadRequest, currentUserId);
    }

    @Transactional
    public void markAsReadViaStomp(MarkReadRequest markReadRequest, String currentUserId) {
        privateChatService.markAsReadViaStomp(markReadRequest, currentUserId);
    }

    public ResponseEntity<?> deleteConversation(HttpServletRequest request, String participantId) {
        return privateChatService.deleteConversation(request, participantId);
    }

    // User Search Operation

    public void handleUserSearch(String payload, String currentUserId) {
        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            String type = jsonNode.get("type").asText();
            if (!"USER_SEARCH".equals(type)) return;

            String query = jsonNode.get("payload").get("query").asText();
            if (query == null || query.trim().isEmpty()) return;

            List<com.vaultx.user.context.model.authentication.response.UserSearchDTO> users =
                    userService.searchUsers(query, currentUserId);

            // Return results
            messagingTemplate.convertAndSendToUser(
                    currentUserId, "/queue/userSearchResults", new MessageResponse("USER_SEARCH_RESULTS", users));
        } catch (Exception e) {
            log.error("Error handling user search: ", e);
        }
    }

    // Group Chat Operations

    public ResponseEntity<?> createGroupChat(CreateGroupChatRequest request, String creatorId) {
        try {
            // Ensure creator is in the participant list
            if (!request.getParticipantIds().contains(creatorId)) {
                request.getParticipantIds().add(creatorId);
            }

            GroupChat groupChat = groupChatService.createGroupChat(request.getGroupName(), request.getParticipantIds());
            return ResponseEntity.ok(groupChat);
        } catch (Exception e) {
            log.error("Error creating group chat", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Failed to create group chat: " + e.getMessage());
        }
    }

    public ResponseEntity<?> sendGroupMessage(GroupChatMessageDTO messageDTO) {
        try {
            groupChatService.sendGroupMessage(messageDTO);
            return ResponseEntity.ok("Group message sent.");
        } catch (Exception e) {
            log.error("Error sending group message", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to send group message: " + e.getMessage());
        }
    }

    public ResponseEntity<GroupChatHistoryDTO> getGroupChatHistory(UUID groupId) {
        try {
            GroupChatHistoryDTO history = groupChatService.getGroupChatHistory(groupId);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("Error retrieving group chat history", e);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group chat history not found");
        }
    }

    // Chat Request Operations

    public ResponseEntity<ChatRequestDTO> sendChatRequest(ChatMessageDTO dto, String senderId) {
        try {
            ChatRequestDTO created = chatRequestService.sendRequest(dto, senderId);
            return ResponseEntity.ok(created);
        } catch (ResponseStatusException e) {
            throw e; // Rethrow the exception with its status code
        } catch (Exception e) {
            log.error("Error sending chat request", e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send chat request: " + e.getMessage());
        }
    }

    public ResponseEntity<List<ChatRequestDTO>> getPendingChatRequests(String userId) {
        try {
            List<ChatRequestDTO> pendingRequests = chatRequestService.pendingForUser(userId);
            return ResponseEntity.ok(pendingRequests);
        } catch (Exception e) {
            log.error("Error fetching pending chat requests", e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch pending requests: " + e.getMessage());
        }
    }

    public void acceptChatRequest(UUID requestId, String userId) {
        try {
            chatRequestService.accept(requestId, userId);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error accepting chat request", e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to accept chat request: " + e.getMessage());
        }
    }

    public void rejectChatRequest(UUID requestId, String userId) {
        try {
            chatRequestService.reject(requestId, userId);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error rejecting chat request", e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to reject chat request: " + e.getMessage());
        }
    }

    public void cancelChatRequest(UUID requestId, String userId) {
        try {
            chatRequestService.cancel(requestId, userId);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error canceling chat request", e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to cancel chat request: " + e.getMessage());
        }
    }
}
