package com.vaultx.user.context.model.messaging.dto;

import java.util.List;
import lombok.Data;

@Data
public class CreateGroupChatRequest {
    private String groupName;
    private List<String> participantIds;
}
