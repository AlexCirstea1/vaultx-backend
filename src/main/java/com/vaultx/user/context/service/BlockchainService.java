package com.vaultx.user.context.service;

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
import com.vaultx.user.context.kafka.KafkaProducer;
import com.vaultx.user.context.model.DIDEvent;
import com.vaultx.user.context.model.EventType;
import com.vaultx.user.context.model.activity.ActivityType;
import com.vaultx.user.context.model.user.User;

@Service
@AllArgsConstructor
@Slf4j
public class BlockchainService {

    private final KafkaProducer kafkaProducer;
    private final ActivityService activityService;

    /**
     * Send a DIDEvent to Kafka whenever a user is registered, updates their key, etc.
     *
     * @param user    The user
     * @param eventType The type of event (REGISTER, KEY_UPDATED, ROLE_CHANGED)
     */
    public void recordDIDEvent(User user, EventType eventType, Object payload) {

        if (!user.isBlockchainConsent()) return;

        String jsonPayload = payload != null ? serializeToJson(payload) : null;
        DIDEvent event = new DIDEvent(user.getId(), user.getPublicKey(), eventType, Instant.now(), jsonPayload);

        kafkaProducer.sendDIDEvent(event);

        String description = getDescription(eventType);
        activityService.logActivity(
                user,
                ActivityType.BLOCKCHAIN,
                description,
                false,
                "Event Type: " + eventType + ", Transaction ID: 0x"
                        + UUID.randomUUID().toString().replace("-", ""));
    }

    /**
     * Generates a human-readable description for blockchain events based on the event type.
     *
     * @param eventType The type of event (USER_REGISTERED, USER_KEY_ROTATED, USER_ROLE_CHANGED, CHAT_CREATED)
     * @return A descriptive string representing the blockchain event
     */
    private static String getDescription(EventType eventType) {
        String description = "Document hash committed to blockchain";
        if (eventType == EventType.USER_KEY_ROTATED) {
            description = "Encryption key rotation recorded on blockchain";
        } else if (eventType == EventType.USER_REGISTERED) {
            description = "New user registered on blockchain";
        } else if (eventType == EventType.USER_ROLE_CHANGED) {
            description = "User role change recorded on blockchain";
        } else if (eventType == EventType.CHAT_CREATED) {
            description = "New conversation created and recorded on blockchain";
        }
        return description;
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
