package com.vaultx.user.context.model.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupChatHistoryDTO {
    private UUID groupId;
    private String groupName;
    private List<GroupChatMessageDTO> messages;
}
