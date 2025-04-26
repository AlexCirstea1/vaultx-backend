package com.vaultx.user.context.model.messaging.dto;

import java.util.List;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatHistoryDTO {
    private String participant;
    private String participantUsername;
    private List<ChatMessageDTO> messages;
    private int unreadCount;
}
