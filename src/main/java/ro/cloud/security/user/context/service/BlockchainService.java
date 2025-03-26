package ro.cloud.security.user.context.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
    public void recordDIDEvent(UUID userId, String publicDid, EventType eventType, Object payload) {
        String jsonPayload = payload != null ? serializeToJson(payload) : null;

        DIDEvent event = new DIDEvent(userId, publicDid, eventType, Instant.now(), jsonPayload);

        kafkaProducer.sendDIDEvent(event);
    }

    /**
     * Serializes any object to JSON string, handling nested objects and dates.
     *
     * @param object Object to serialize
     * @return JSON string representation
     */
    public static String serializeToJson(Object object) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing object to JSON", e);
        }
    }
}
