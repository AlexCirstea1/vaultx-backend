package com.vaultx.user.context.model.messaging.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupChatMessageDTO {
    private UUID id;
    private UUID groupId;
    private String sender;
    private String content;
    private Instant timestamp;
}
