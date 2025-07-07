package com.vaultx.user.context.service.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vaultx.user.context.model.blockchain.DIDEvent;
import com.vaultx.user.context.model.blockchain.EventHistory;
import com.vaultx.user.context.model.blockchain.EventType;
import com.vaultx.user.context.model.file.FileBlockchainMeta;
import com.vaultx.user.context.model.user.User;
import com.vaultx.user.context.service.kafka.KafkaProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static com.vaultx.user.context.model.activity.ActivityType.BLOCKCHAIN;
import static com.vaultx.user.context.model.blockchain.EventType.*;

@Service
@Slf4j
public class BlockchainService {

    private final KafkaProducer kafkaProducer;
    private final ActivityService activityService;
    private final RestTemplate rest;
    private final String baseUrl;
    private final String username;
    private final String password;

    public BlockchainService(
            KafkaProducer kafkaProducer,
            ActivityService activityService,
            RestTemplate rest,
            @Value("${hyperledger.base-url}") String baseUrl,
            @Value("${hyperledger.user}") String username,
            @Value("${hyperledger.password}") String password) {
        this.kafkaProducer = kafkaProducer;
        this.activityService = activityService;
        this.rest = rest;
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;
    }

    /**
     * Generates a human-readable description for blockchain events based on the event type.
     *
     * @param eventType The type of event (USER_REGISTERED, USER_KEY_ROTATED, USER_ROLE_CHANGED, CHAT_CREATED)
     * @return A descriptive string representing the blockchain event
     */
    private static String getDescription(EventType eventType) {
        String description = "Document hash committed to blockchain";
        if (eventType == USER_KEY_ROTATED) {
            description = "Encryption key rotation recorded on blockchain";
        } else if (eventType == USER_REGISTERED) {
            description = "New user registered on blockchain";
        } else if (eventType == USER_ROLE_CHANGED) {
            description = "User role change recorded on blockchain";
        } else if (eventType == CHAT_CREATED) {
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

    private HttpHeaders createBasicAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (username != null && !username.isEmpty() && password != null) {
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            headers.set("Authorization", "Basic " + encodedAuth);
        }
        return headers;
    }

    public List<DIDEvent> getEventsByUser(UUID userId) {
        String url = String.format("%s/api/chaincode/queryEventsByUser?userId=%s", baseUrl, userId);
        HttpEntity<Void> requestEntity = new HttpEntity<>(createBasicAuthHeaders());
        ResponseEntity<List<DIDEvent>> resp =
                rest.exchange(url, HttpMethod.GET, requestEntity, new ParameterizedTypeReference<>() {
                });
        return resp.getBody();
    }

    public DIDEvent getEvent(UUID eventId) {
        String url = String.format("%s/api/chaincode/queryEvent?eventId=%s", baseUrl, eventId);
        HttpEntity<Void> requestEntity = new HttpEntity<>(createBasicAuthHeaders());
        return rest.exchange(url, HttpMethod.GET, requestEntity, DIDEvent.class).getBody();
    }

    public List<EventHistory> getEventHistory(UUID eventId) {
        String url = String.format("%s/api/chaincode/queryHistory?eventId=%s", baseUrl, eventId);
        HttpEntity<Void> requestEntity = new HttpEntity<>(createBasicAuthHeaders());
        ResponseEntity<List<EventHistory>> resp =
                rest.exchange(url, HttpMethod.GET, requestEntity, new ParameterizedTypeReference<>() {
                });
        return resp.getBody();
    }

    public DIDEvent getFileEvent(UUID userId, UUID fileId) {
        List<DIDEvent> events = getEventsByUser(userId);
        return events.stream()
                .filter(event -> event.getEventType() == FILE_UPLOAD)
                .filter(event -> {
                    try {
                        FileBlockchainMeta meta = new ObjectMapper().readValue(event.getPayload(), FileBlockchainMeta.class);
                        return meta.getFileId().equals(fileId);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .findFirst()
                .orElse(null);
    }

    /**
     * Send a DIDEvent to Kafka whenever a user is registered, updates their key, etc.
     *
     * @param user      The user
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
                BLOCKCHAIN,
                description,
                false,
                "Event Type: " + eventType + ", Transaction ID: 0x"
                        + UUID.randomUUID().toString().replace("-", ""));
    }
}
