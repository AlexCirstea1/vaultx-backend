package ro.cloud.security.user.context.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
public class DIDEvent {
    private UUID userId;
    private String publicDid;
    private EventType eventType;
    private Instant timestamp;
}
