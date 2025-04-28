package com.vaultx.user.context.model.blockchain;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DIDEvent {
    private UUID userId;
    private String publicKey;
    private EventType eventType;
    private Instant timestamp;
    private String payload;

    /**
     * Convert this event into a CSV-formatted line.
     */
    public String toCsvLine() {
        // Safely escape any double quotes in the payload
        String safePayload = payload == null ? "" : payload.replace("\"", "\"\"");
        // Wrap the payload in quotes in case it contains commas
        safePayload = "\"" + safePayload + "\"";

        return String.join(",",
                userId == null ? "" : userId.toString(),
                publicKey == null ? "" : publicKey,
                eventType == null ? "" : eventType.name(),
                timestamp == null ? "" : timestamp.toString(),
                safePayload
        );
    }
}
