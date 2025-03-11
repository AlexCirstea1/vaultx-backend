package ro.cloud.security.user.context.model.messaging.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

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
