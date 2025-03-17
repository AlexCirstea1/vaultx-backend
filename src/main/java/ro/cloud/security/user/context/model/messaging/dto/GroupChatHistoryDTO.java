package ro.cloud.security.user.context.model.messaging.dto;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupChatHistoryDTO {
    private UUID groupId;
    private String groupName;
    private List<GroupChatMessageDTO> messages;
}
