package ro.cloud.security.user.context.service;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ro.cloud.security.user.context.kafka.KafkaProducer;
import ro.cloud.security.user.context.model.DIDEvent;
import ro.cloud.security.user.context.model.EventType;

@Service
@AllArgsConstructor
@Slf4j
public class BlockchainService {

    private final KafkaProducer kafkaProducer;

    /**
     * Send a DIDEvent to Kafka whenever a user is registered, updates their key, etc.
     *
     * @param userId    The UUID of the user
     * @param publicDid Base64-encoded public key (or DID)
     * @param eventType The type of event (REGISTER, KEY_UPDATED, ROLE_CHANGED)
     */
    public void recordDIDEvent(UUID userId, String publicDid, EventType eventType) {
        DIDEvent event = new DIDEvent(
                userId, publicDid, eventType, Instant.now() // current timestamp
                );

        kafkaProducer.sendDIDEvent(event);
    }
}
