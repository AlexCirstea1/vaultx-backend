package ro.cloud.security.user.context.model.messaging.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {
    private UUID id;
    private String sender;
    private String recipient;
    private String content;
    private LocalDateTime timestamp;
    private boolean isRead;
    private LocalDateTime readTimestamp;
    private String clientTempId;
    private String type;
}
