package ro.cloud.security.user.context.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ro.cloud.security.user.context.model.authentication.response.MessageResponse;
import ro.cloud.security.user.context.model.authentication.response.UserSearchDTO;
import ro.cloud.security.user.context.model.messaging.dto.*;
import ro.cloud.security.user.context.model.messaging.dto.MarkReadRequest;
import ro.cloud.security.user.context.service.ChatService;
import ro.cloud.security.user.context.service.GroupChatService;
import ro.cloud.security.user.context.service.authentication.UserService;

@RestController
@AllArgsConstructor
@Slf4j
@RequestMapping
public class ChatController {

    private final ChatService chatService;
    private final GroupChatService groupChatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @MessageMapping("/sendPrivateMessage")
    public void sendPrivateMessage(ChatMessageDTO chatMessage, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String senderId = jwt.getSubject();
        chatService.sendPrivateMessage(chatMessage, senderId);
    }

    @GetMapping("/api/messages")
    public ResponseEntity<List<ChatMessageDTO>> getMessages(
            @RequestParam("recipientId") String participantId, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String currentUserId = jwt.getSubject();
        List<ChatMessageDTO> messages = chatService.getConversation(currentUserId, participantId);
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/api/chats")
    public ResponseEntity<?> getChatSummaries(Authentication authentication) {
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String currentUserId = jwt.getSubject();
            return ResponseEntity.ok(chatService.getChatSummaries(currentUserId));
        } catch (Exception e) {
            log.error("Error fetching chat summaries", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while fetching chat summaries.");
        }
    }

    @PostMapping("/api/chats/mark-as-read")
    public ResponseEntity<?> markMessagesAsRead(
            @RequestBody MarkReadRequest markReadRequest, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String currentUserId = jwt.getSubject();
        return chatService.markMessagesAsRead(markReadRequest, currentUserId);
    }

    @MessageMapping("/markAsRead")
    public void markAsReadViaStomp(@RequestBody MarkReadRequest markReadRequest, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String currentUserId = jwt.getSubject();
        chatService.markAsReadViaStomp(markReadRequest, currentUserId);
    }

    @DeleteMapping("/api/messages")
    public ResponseEntity<?> deleteConversation(
            @RequestParam("participantId") String participantId, HttpServletRequest request) {
        return chatService.deleteConversation(request, participantId);
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

    // ---------------------------
    // Endpoints for Group Chats
    // ---------------------------

    @PostMapping("/api/group-chats")
    public ResponseEntity<?> createGroupChat(
            @RequestBody CreateGroupChatRequest request, Authentication authentication) {
        // Optionally, you can verify the creator is in the participant list
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String creatorId = jwt.getSubject();
        if (!request.getParticipantIds().contains(creatorId)) {
            request.getParticipantIds().add(creatorId);
        }
        var groupChat = groupChatService.createGroupChat(request.getGroupName(), request.getParticipantIds());
        return ResponseEntity.ok(groupChat);
    }

    @PostMapping("/api/group-chats/{groupId}/messages")
    public ResponseEntity<?> sendGroupMessage(
            @PathVariable("groupId") UUID groupId,
            @RequestBody GroupChatMessageDTO messageDTO,
            Authentication authentication) {
        // Set group ID and sender from authentication
        messageDTO.setGroupId(groupId);
        Jwt jwt = (Jwt) authentication.getPrincipal();
        messageDTO.setSender(jwt.getSubject());
        groupChatService.sendGroupMessage(messageDTO);
        return ResponseEntity.ok("Group message sent.");
    }

    @GetMapping("/api/group-chats/{groupId}/messages")
    public ResponseEntity<GroupChatHistoryDTO> getGroupChatHistory(@PathVariable("groupId") UUID groupId) {
        GroupChatHistoryDTO history = groupChatService.getGroupChatHistory(groupId);
        return ResponseEntity.ok(history);
    }

    @PostMapping("/api/chat-requests")
    public ResponseEntity<?> sendChatRequest(
            @RequestBody ChatMessageDTO chatRequestDto, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String senderId = jwt.getSubject();
        chatService.sendChatRequest(chatRequestDto, senderId);
        return ResponseEntity.ok("Chat request sent.");
    }

    @GetMapping("/api/chat-requests")
    public ResponseEntity<?> getChatRequests(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String currentUserId = jwt.getSubject();
        List<ChatRequestDTO> requests = chatService.getPendingChatRequests(currentUserId);
        return ResponseEntity.ok(requests);
    }

    @PostMapping("/api/chat-requests/{requestId}/accept")
    public ResponseEntity<?> acceptChatRequest(@PathVariable UUID requestId, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String currentUserId = jwt.getSubject();
        chatService.acceptChatRequest(requestId, currentUserId);
        return ResponseEntity.ok("Chat request accepted.");
    }

    @PostMapping("/api/chat-requests/{requestId}/reject")
    public ResponseEntity<?> rejectChatRequest(@PathVariable UUID requestId, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String currentUserId = jwt.getSubject();
        chatService.rejectChatRequest(requestId, currentUserId);
        return ResponseEntity.ok("Chat request rejected.");
    }
}
