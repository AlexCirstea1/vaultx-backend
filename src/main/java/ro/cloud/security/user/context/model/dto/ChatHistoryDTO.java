package ro.cloud.security.user.context.model.dto;

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
