package ro.cloud.security.user.context.model.messaging.dto;

import lombok.*;

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
