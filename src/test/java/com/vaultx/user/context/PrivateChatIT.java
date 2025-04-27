package com.vaultx.user.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.vaultx.user.context.model.authentication.response.UserResponseDTO;
import com.vaultx.user.context.model.messaging.dto.ChatHistoryDTO;
import com.vaultx.user.context.model.messaging.dto.ChatMessageDTO;
import com.vaultx.user.context.model.messaging.dto.MarkReadRequest;
import com.vaultx.user.context.service.user.PresenceService;
import com.vaultx.user.context.util.AuthTestUtils;
import com.vaultx.user.context.util.TestCredentialsGenerator.TestCredentials;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class PrivateChatIT extends BaseIT {

    @MockitoBean
    private PresenceService presenceService;

    String senderToken;
    private String recipientToken;
    private UUID senderId;
    private UUID recipientId;
    private String senderUsername;
    private String recipientUsername;

    @BeforeEach
    void setUp() {
        TestCredentials senderCreds = generateTestCredentials("sender", "ChatSender");
        senderUsername = senderCreds.getUsername();
        UserResponseDTO senderUser =
                AuthTestUtils.registerUser(http, senderCreds.getEmail(), senderUsername, senderCreds.getPassword());
        senderId = senderUser.getId();
        senderToken = AuthTestUtils.loginUser(http, senderUsername, senderCreds.getPassword())
                .getAccessToken();

        TestCredentials recipientCreds = generateTestCredentials("recipient", "ChatRecipient");
        recipientUsername = recipientCreds.getUsername();
        UserResponseDTO recipientUser = AuthTestUtils.registerUser(
                http, recipientCreds.getEmail(), recipientUsername, recipientCreds.getPassword());
        recipientId = recipientUser.getId();
        recipientToken = AuthTestUtils.loginUser(http, recipientUsername, recipientCreds.getPassword())
                .getAccessToken();

        try {
            setupWebSocketClient();
        } catch (Exception e) {
            Assertions.fail("Failed to setup WebSocket client: " + e.getMessage());
        }
    }

    @Test
    void sendAndRetrieveMessage() {
        ChatMessageDTO msg = createTestMessage(senderId.toString(), recipientId.toString(), "Hello, recipient!");

        stompSession.send("/app/sendPrivateMessage", msg);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            ResponseEntity<List<ChatMessageDTO>> resp = http.exchange(
                    "/api/messages?recipientId=" + recipientId,
                    HttpMethod.GET,
                    createEntity(null, createAuthHeaders(senderToken)),
                    new ParameterizedTypeReference<List<ChatMessageDTO>>() {});

            List<ChatMessageDTO> messages = resp.getBody();
            assertThat(messages).isNotNull().isNotEmpty();
            assertThat(messages.getFirst().getCiphertext()).isEqualTo("Hello, recipient!");
        });
    }

    //    @Test
    void markMessagesAsRead() {
        ChatMessageDTO msg = createTestMessage(recipientId.toString(), senderId.toString(), "Please read this");
        stompSession.send("/app/sendPrivateMessage", msg);

        // wait until at least one message arrives
        List<ChatMessageDTO> before = await().atMost(5, TimeUnit.SECONDS)
                .until(
                        () -> {
                            List<ChatMessageDTO> list = http.exchange(
                                            "/api/messages?recipientId=" + senderId,
                                            HttpMethod.GET,
                                            createEntity(null, createAuthHeaders(senderToken)),
                                            new ParameterizedTypeReference<List<ChatMessageDTO>>() {})
                                    .getBody();
                            Assertions.assertNotNull(list);
                            return list.isEmpty() ? null : list;
                        },
                        Objects::nonNull);

        UUID messageId = before.getFirst().getId();

        MarkReadRequest markRead = new MarkReadRequest();
        markRead.setMessageIds(List.of(messageId));
        stompSession.send("/app/markAsRead", markRead);

        // wait until a read flag flips
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<ChatMessageDTO> after = http.exchange(
                            "/api/messages?recipientId=" + senderId, // Changed from recipientId to senderId
                            HttpMethod.GET,
                            createEntity(null, createAuthHeaders(senderToken)),
                            new ParameterizedTypeReference<List<ChatMessageDTO>>() {})
                    .getBody();

            assertThat(after).isNotEmpty();
            assertThat(after.getFirst().isRead()).isTrue();
        });
    }

    @Test
    void getChatSummaries() {
        ChatMessageDTO msg = createTestMessage(senderId.toString(), recipientId.toString(), "Test for chat summary");
        stompSession.send("/app/sendPrivateMessage", msg);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            ResponseEntity<List<ChatHistoryDTO>> resp = http.exchange(
                    "/api/chats",
                    HttpMethod.GET,
                    createEntity(null, createAuthHeaders(senderToken)),
                    new ParameterizedTypeReference<List<ChatHistoryDTO>>() {});

            List<ChatHistoryDTO> summaries = resp.getBody();
            assertThat(summaries).isNotNull().isNotEmpty();
            assertThat(summaries.stream().anyMatch(c -> recipientId.toString().equals(c.getParticipant())))
                    .isTrue();
        });
    }

    @Test
    void deleteConversation() {
        ChatMessageDTO msg = createTestMessage(senderId.toString(), recipientId.toString(), "Message to delete");
        stompSession.send("/app/sendPrivateMessage", msg);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<ChatMessageDTO> before = http.exchange(
                            "/api/messages?recipientId=" + recipientId,
                            HttpMethod.GET,
                            createEntity(null, createAuthHeaders(senderToken)),
                            new ParameterizedTypeReference<List<ChatMessageDTO>>() {})
                    .getBody();
            assertThat(before).isNotEmpty();
        });

        ResponseEntity<Void> delResp = http.exchange(
                "/api/messages?participantId=" + recipientId, // <- correct mapping
                HttpMethod.DELETE,
                createEntity(null, createAuthHeaders(senderToken)),
                Void.class);
        assertThat(delResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<ChatMessageDTO> afterDelete = http.exchange(
                        "/api/messages?recipientId=" + recipientId,
                        HttpMethod.GET,
                        createEntity(null, createAuthHeaders(senderToken)),
                        new ParameterizedTypeReference<List<ChatMessageDTO>>() {})
                .getBody();

        assertThat(afterDelete).isEmpty();
    }

    private ChatMessageDTO createTestMessage(String senderId, String recipientId, String content) {
        ChatMessageDTO m = new ChatMessageDTO();
        m.setSender(senderId);
        m.setRecipient(recipientId);
        m.setCiphertext(content);
        m.setEncryptedKeyForSender("testKey123");
        m.setEncryptedKeyForRecipient("testKey456");
        m.setIv("testIv123");
        m.setSenderKeyVersion("1");
        m.setRecipientKeyVersion("1");
        m.setOneTime(false);
        return m;
    }
}
