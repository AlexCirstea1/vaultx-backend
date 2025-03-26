package ro.cloud.security.user.context.model;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DIDEvent {
    private UUID userId;
    private String publicDid;
    private EventType eventType;
    private Instant timestamp;
    private String payload;
}
