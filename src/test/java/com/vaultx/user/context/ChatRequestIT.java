package com.vaultx.user.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaultx.user.context.model.authentication.response.LoginResponseDTO;
import com.vaultx.user.context.model.authentication.response.UserResponseDTO;
import com.vaultx.user.context.model.messaging.dto.ChatMessageDTO;
import com.vaultx.user.context.model.messaging.dto.ChatRequestDTO;
import com.vaultx.user.context.util.AuthTestUtils;
import com.vaultx.user.context.util.TestCredentialsGenerator.TestCredentials;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

class ChatRequestIT extends BaseIT {

    String senderToken;
    private String recipientToken;
    private UUID senderId;
    private UUID recipientId;

    @BeforeEach
    void setUp() {
        // Create first test user (sender)
        TestCredentials senderCreds = generateTestCredentials("requester", "ChatRequester");
        UserResponseDTO senderUser = AuthTestUtils.registerUser(
                http, senderCreds.getEmail(), senderCreds.getUsername(), senderCreds.getPassword());
        LoginResponseDTO senderLogin =
                AuthTestUtils.loginUser(http, senderCreds.getUsername(), senderCreds.getPassword());
        senderToken = senderLogin.getAccessToken();
        senderId = senderUser.getId();

        // Create second test user (recipient)
        TestCredentials recipientCreds = generateTestCredentials("responder", "ChatResponder");
        UserResponseDTO recipientUser = AuthTestUtils.registerUser(
                http, recipientCreds.getEmail(), recipientCreds.getUsername(), recipientCreds.getPassword());
        LoginResponseDTO recipientLogin =
                AuthTestUtils.loginUser(http, recipientCreds.getUsername(), recipientCreds.getPassword());
        recipientToken = recipientLogin.getAccessToken();
        recipientId = recipientUser.getId();

        // Setup WebSocket connection
        try {
            setupWebSocketClient();
        } catch (Exception e) {
            Assertions.fail("Failed to setup WebSocket client: " + e.getMessage());
        }
    }

    @Test
    void sendChatRequestAndAccept() {
        // Send a chat request (still using REST)
        ChatMessageDTO requestMessage =
                createChatRequestMessage(senderId.toString(), recipientId.toString(), "Hi, can we chat?");
        HttpEntity<ChatMessageDTO> requestEntity = createEntity(requestMessage, createAuthHeaders(senderToken));
        ResponseEntity<ChatRequestDTO> response =
                http.exchange("/api/chat-requests", HttpMethod.POST, requestEntity, ChatRequestDTO.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        UUID requestId = response.getBody().getId();

        // Get pending requests for recipient (still using REST)
        ResponseEntity<List<ChatRequestDTO>> pendingResponse = http.exchange(
                "/api/chat-requests",
                HttpMethod.GET,
                createEntity(null, createAuthHeaders(recipientToken)),
                new ParameterizedTypeReference<List<ChatRequestDTO>>() {});

        assertThat(pendingResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pendingResponse.getBody()).isNotEmpty();

        // Accept the request (still using REST)
        ResponseEntity<Void> acceptResponse = http.exchange(
                "/api/chat-requests/" + requestId + "/accept",
                HttpMethod.POST,
                createEntity(null, createAuthHeaders(recipientToken)),
                Void.class);

        assertThat(acceptResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify we can now send messages using WebSocket
        ChatMessageDTO message =
                createChatRequestMessage(senderId.toString(), recipientId.toString(), "Thanks for accepting!");
        stompSession.send("/app/sendPrivateMessage", message);

        // Wait briefly to allow message processing
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify the message was sent using REST
        ResponseEntity<List<ChatMessageDTO>> messagesResponse = http.exchange(
                "/api/messages?recipientId=" + recipientId,
                HttpMethod.GET,
                createEntity(null, createAuthHeaders(senderToken)),
                new ParameterizedTypeReference<List<ChatMessageDTO>>() {});

        assertThat(messagesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(messagesResponse.getBody()).isNotEmpty();
    }

    @Test
    void sendChatRequestAndReject() {
        // Send a chat request
        ChatMessageDTO requestMessage =
                createChatRequestMessage(senderId.toString(), recipientId.toString(), "Hi, can we chat?");
        HttpEntity<ChatMessageDTO> requestEntity = createEntity(requestMessage, createAuthHeaders(senderToken));
        ResponseEntity<ChatRequestDTO> response =
                http.exchange("/api/chat-requests", HttpMethod.POST, requestEntity, ChatRequestDTO.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID requestId = response.getBody().getId();

        // Reject the request
        ResponseEntity<Void> rejectResponse = http.exchange(
                "/api/chat-requests/" + requestId + "/reject",
                HttpMethod.POST,
                createEntity(null, createAuthHeaders(recipientToken)),
                Void.class);

        assertThat(rejectResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify the request is no longer pending
        ResponseEntity<List<ChatRequestDTO>> pendingResponse = http.exchange(
                "/api/chat-requests",
                HttpMethod.GET,
                createEntity(null, createAuthHeaders(recipientToken)),
                new ParameterizedTypeReference<List<ChatRequestDTO>>() {});

        assertThat(pendingResponse.getBody()).isEmpty();
    }

    @Test
    void sendChatRequestAndCancel() {
        // Send a chat request
        ChatMessageDTO requestMessage =
                createChatRequestMessage(senderId.toString(), recipientId.toString(), "Hi, can we chat?");
        HttpEntity<ChatMessageDTO> requestEntity = createEntity(requestMessage, createAuthHeaders(senderToken));
        ResponseEntity<ChatRequestDTO> response =
                http.exchange("/api/chat-requests", HttpMethod.POST, requestEntity, ChatRequestDTO.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID requestId = response.getBody().getId();

        // Cancel the request
        ResponseEntity<Void> cancelResponse = http.exchange(
                "/api/chat-requests/" + requestId + "/cancel",
                HttpMethod.POST,
                createEntity(null, createAuthHeaders(senderToken)),
                Void.class);

        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify no pending requests for recipient
        ResponseEntity<List<ChatRequestDTO>> pendingResponse = http.exchange(
                "/api/chat-requests",
                HttpMethod.GET,
                createEntity(null, createAuthHeaders(recipientToken)),
                new ParameterizedTypeReference<List<ChatRequestDTO>>() {});

        assertThat(pendingResponse.getBody()).isEmpty();
    }

    private ChatMessageDTO createChatRequestMessage(String senderId, String recipientId, String content) {
        ChatMessageDTO message = new ChatMessageDTO();
        message.setSender(senderId);
        message.setRecipient(recipientId);
        message.setCiphertext(content);
        message.setEncryptedKeyForSender("testKey123");
        message.setEncryptedKeyForRecipient("testKey456");
        message.setIv("testIv123");
        message.setSenderKeyVersion("1");
        message.setRecipientKeyVersion("1");
        return message;
    }
}
