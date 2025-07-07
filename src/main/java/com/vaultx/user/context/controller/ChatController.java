package com.vaultx.user.context.controller;

import com.vaultx.user.context.model.messaging.dto.*;
import com.vaultx.user.context.service.chat.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Chat", description = "Messaging and chat functionality endpoints")
public class ChatController {

    private final ChatService chatService;

    @MessageMapping("/sendPrivateMessage")
    @Operation(summary = "Send private message via WebSocket", hidden = true)
    public void sendPrivateMessage(@RequestBody ChatMessageDTO m, Authentication auth) {
        String sender = ((Jwt) auth.getPrincipal()).getSubject();
        chatService.sendPrivateMessage(m, sender);
    }

    @GetMapping("/api/messages")
    @Operation(
            summary = "Get conversation messages",
            description = "Retrieves all messages between the current user and a specified participant",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Messages retrieved successfully",
                            content = @Content(schema = @Schema(implementation = ChatMessageDTO.class, type = "array"))),
                    @ApiResponse(responseCode = "401", description = "User not authenticated", content = @Content)
            })
    public ResponseEntity<List<ChatMessageDTO>> getMessages(
            @Parameter(description = "ID of the conversation participant", required = true) @RequestParam("recipientId")
            String participantId,
            Authentication authentication) {
        String me = ((Jwt) authentication.getPrincipal()).getSubject();
        return ResponseEntity.ok(chatService.getConversation(me, participantId));
    }

    @GetMapping("/api/chats")
    @Operation(
            summary = "Get chat summaries",
            description = "Retrieves a summary of all conversations for the current user",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Chat summaries retrieved successfully",
                            content = @Content(schema = @Schema(implementation = ChatHistoryDTO.class, type = "array"))),
                    @ApiResponse(responseCode = "401", description = "User not authenticated", content = @Content),
                    @ApiResponse(
                            responseCode = "500",
                            description = "An error occurred while fetching chat summaries",
                            content = @Content)
            })
    public ResponseEntity<?> getChatSummaries(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String currentUserId = jwt.getSubject();
        return chatService.getChatSummaries(currentUserId);
    }

    @PostMapping("/api/chats/mark-as-read")
    @Operation(
            summary = "Mark messages as read",
            description = "Marks specified messages as read by the current user",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Messages marked as read successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
                    @ApiResponse(responseCode = "404", description = "No unread messages found", content = @Content),
                    @ApiResponse(responseCode = "401", description = "User not authenticated", content = @Content)
            })
    public ResponseEntity<?> markMessagesAsRead(
            @Parameter(description = "List of message IDs to mark as read", required = true) @RequestBody
            MarkReadRequest markReadRequest,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String currentUserId = jwt.getSubject();
        return chatService.markMessagesAsRead(markReadRequest, currentUserId);
    }

    @MessageMapping("/markAsRead")
    @Operation(summary = "Mark messages as read via WebSocket", hidden = true)
    public void markAsReadViaStomp(@RequestBody MarkReadRequest markReadRequest, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String currentUserId = jwt.getSubject();
        chatService.markAsReadViaStomp(markReadRequest, currentUserId);
    }

    @DeleteMapping("/api/messages")
    @Operation(
            summary = "Delete conversation",
            description = "Deletes all messages between the current user and a specified participant",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Conversation deleted successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid user ID format", content = @Content),
                    @ApiResponse(responseCode = "404", description = "No messages found between users", content = @Content),
                    @ApiResponse(responseCode = "401", description = "User not authenticated", content = @Content),
                    @ApiResponse(
                            responseCode = "500",
                            description = "An error occurred while deleting the conversation",
                            content = @Content)
            })
    public ResponseEntity<?> deleteConversation(
            @Parameter(description = "ID of the conversation participant", required = true)
            @RequestParam("participantId")
            String participantId,
            HttpServletRequest request) {
        return chatService.deleteConversation(request, participantId);
    }

    @MessageMapping("/userSearch")
    @Operation(summary = "Search for users via WebSocket", hidden = true)
    public void handleUserSearch(String payload, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String currentUserId = jwt.getSubject();
        chatService.handleUserSearch(payload, currentUserId);
    }

    // Group Chat Endpoints

    @PostMapping("/api/group-chats")
    @Operation(
            summary = "Create group chat",
            description = "Creates a new group chat with specified participants",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Group chat created successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content),
                    @ApiResponse(responseCode = "401", description = "User not authenticated", content = @Content)
            })
    public ResponseEntity<?> createGroupChat(
            @Parameter(description = "Group chat creation details", required = true) @RequestBody
            CreateGroupChatRequest request,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String creatorId = jwt.getSubject();
        return chatService.createGroupChat(request, creatorId);
    }

    @PostMapping("/api/group-chats/{groupId}/messages")
    @Operation(
            summary = "Send group message",
            description = "Sends a message to a group chat",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Group message sent successfully"),
                    @ApiResponse(responseCode = "404", description = "Group not found", content = @Content),
                    @ApiResponse(responseCode = "401", description = "User not authenticated", content = @Content)
            })
    public ResponseEntity<?> sendGroupMessage(
            @Parameter(description = "Group chat ID", required = true) @PathVariable("groupId") UUID groupId,
            @Parameter(description = "Message details", required = true) @RequestBody GroupChatMessageDTO messageDTO,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        messageDTO.setGroupId(groupId);
        messageDTO.setSender(jwt.getSubject());
        return chatService.sendGroupMessage(messageDTO);
    }

    @GetMapping("/api/group-chats/{groupId}/messages")
    @Operation(
            summary = "Get group chat history",
            description = "Retrieves message history for a group chat",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Group chat history retrieved successfully",
                            content = @Content(schema = @Schema(implementation = GroupChatHistoryDTO.class))),
                    @ApiResponse(responseCode = "404", description = "Group not found", content = @Content),
                    @ApiResponse(responseCode = "401", description = "User not authenticated", content = @Content)
            })
    public ResponseEntity<GroupChatHistoryDTO> getGroupChatHistory(
            @Parameter(description = "Group chat ID", required = true) @PathVariable("groupId") UUID groupId) {
        return chatService.getGroupChatHistory(groupId);
    }

    // Chat Request Endpoints

    @PostMapping("/api/chat-requests")
    @Operation(
            summary = "Send chat request",
            description = "Sends a chat request to another user",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Chat request sent successfully",
                            content = @Content(schema = @Schema(implementation = ChatRequestDTO.class))),
                    @ApiResponse(
                            responseCode = "403",
                            description = "Cannot send request - one user blocked the other",
                            content = @Content),
                    @ApiResponse(
                            responseCode = "409",
                            description = "A pending request already exists",
                            content = @Content),
                    @ApiResponse(responseCode = "401", description = "User not authenticated", content = @Content)
            })
    public ResponseEntity<ChatRequestDTO> sendChatRequest(
            @Parameter(description = "Chat request details", required = true) @RequestBody ChatMessageDTO dto,
            Authentication auth) {
        String senderId = ((Jwt) auth.getPrincipal()).getSubject();
        return chatService.sendChatRequest(dto, senderId);
    }

    @GetMapping("/api/chat-requests")
    @Operation(
            summary = "Get pending chat requests",
            description = "Retrieves all pending chat requests for the current user",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Pending requests retrieved successfully",
                            content = @Content(schema = @Schema(implementation = ChatRequestDTO.class, type = "array"))),
                    @ApiResponse(responseCode = "401", description = "User not authenticated", content = @Content)
            })
    public ResponseEntity<List<ChatRequestDTO>> getPendingChatRequests(Authentication auth) {
        String userId = ((Jwt) auth.getPrincipal()).getSubject();
        return chatService.getPendingChatRequests(userId);
    }

    @PostMapping("/api/chat-requests/{id}/accept")
    @Operation(
            summary = "Accept chat request",
            description = "Accepts a pending chat request sent to the current user",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Chat request accepted successfully"),
                    @ApiResponse(responseCode = "404", description = "Chat request not found", content = @Content),
                    @ApiResponse(responseCode = "403", description = "Not authorized for this request", content = @Content),
                    @ApiResponse(responseCode = "409", description = "Request already processed", content = @Content),
                    @ApiResponse(responseCode = "401", description = "User not authenticated", content = @Content)
            })
    public ResponseEntity<Void> acceptChatRequest(
            @Parameter(description = "Chat request ID", required = true) @PathVariable UUID id, Authentication auth) {
        String userId = ((Jwt) auth.getPrincipal()).getSubject();
        chatService.acceptChatRequest(id, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/chat-requests/{id}/reject")
    @Operation(
            summary = "Reject chat request",
            description = "Rejects a pending chat request sent to the current user",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Chat request rejected successfully"),
                    @ApiResponse(responseCode = "404", description = "Chat request not found", content = @Content),
                    @ApiResponse(responseCode = "403", description = "Not authorized for this request", content = @Content),
                    @ApiResponse(responseCode = "401", description = "User not authenticated", content = @Content)
            })
    public ResponseEntity<Void> rejectChatRequest(
            @Parameter(description = "Chat request ID", required = true) @PathVariable UUID id, Authentication auth) {
        String userId = ((Jwt) auth.getPrincipal()).getSubject();
        chatService.rejectChatRequest(id, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/chat-requests/{id}/cancel")
    @Operation(
            summary = "Cancel chat request",
            description = "Cancels a pending chat request sent by the current user",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Chat request cancelled successfully"),
                    @ApiResponse(responseCode = "404", description = "Chat request not found", content = @Content),
                    @ApiResponse(
                            responseCode = "403",
                            description = "Not authorized to cancel this request",
                            content = @Content),
                    @ApiResponse(responseCode = "401", description = "User not authenticated", content = @Content)
            })
    public ResponseEntity<Void> cancelChatRequest(
            @Parameter(description = "Chat request ID", required = true) @PathVariable UUID id, Authentication auth) {
        String userId = ((Jwt) auth.getPrincipal()).getSubject();
        chatService.cancelChatRequest(id, userId);
        return ResponseEntity.ok().build();
    }
}
