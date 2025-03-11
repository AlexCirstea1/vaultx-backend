package ro.cloud.security.user.context.model.messaging.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateGroupChatRequest {
    private String groupName;
    private List<String> participantIds;  // Include creator's ID as well
}
