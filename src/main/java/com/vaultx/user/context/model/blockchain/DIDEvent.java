package com.vaultx.user.context.model.blockchain;

import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DIDEvent {
    /** chain‑code key */
    private UUID eventId;

    private UUID userId;
    private String publicKey;

    private EventType eventType;
    private Instant timestamp;

    private String payload;
    private long kafkaOffset;
    private String payloadHash;
    private String docType;

    public DIDEvent(UUID id, String publicKey, EventType eventType, Instant now, String jsonPayload) {
        this.userId = id;
        this.publicKey = publicKey;
        this.eventType = eventType;
        this.timestamp = now;
        this.payload = jsonPayload;
    }


    /**
     * Convert this event into a CSV-formatted line.
     */
    public String toCsvLine() {
        // Safely escape any double quotes in the payload
        String safePayload = payload == null ? "" : payload.replace("\"", "\"\"");
        // Wrap the payload in quotes in case it contains commas
        safePayload = "\"" + safePayload + "\"";

        return String.join(",",
                eventId == null ? "" : eventId.toString(),
                userId == null ? "" : userId.toString(),
                eventType == null ? "" : eventType.name(),
                payloadHash == null ? "" : payloadHash,
                String.valueOf(kafkaOffset),
                timestamp == null ? "" : timestamp.toString(),
                safePayload
        );
    }
}