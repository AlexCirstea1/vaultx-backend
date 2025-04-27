package com.vaultx.user.context.service.chat;

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultx.user.context.model.messaging.dto.ChatMessageDTO;
import com.vaultx.user.context.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    PrivateChatService privateSvc;

    @Mock
    GroupChatService groupSvc;

    @Mock
    ChatRequestService requestSvc;

    @Mock
    UserService userSvc;

    @Mock
    SimpMessagingTemplate broker;

    @Mock
    ObjectMapper mapper;

    @InjectMocks
    ChatService sut;

    @Test
    void delegatesToPrivateService() {
        ChatMessageDTO dto = ChatMessageDTO.builder()
                .ciphertext("hi")
                .sender("sender-id")
                .recipient("recipient-id")
                .build();
        sut.sendPrivateMessage(dto, "u-1");
        verify(privateSvc).sendPrivateMessage(dto, "u-1");
    }
}
