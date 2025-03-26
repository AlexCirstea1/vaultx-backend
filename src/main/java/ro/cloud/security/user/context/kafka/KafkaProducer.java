package ro.cloud.security.user.context.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import ro.cloud.security.user.context.model.DIDEvent;
import ro.cloud.security.user.context.model.EventType;

@Slf4j
@Component
public class KafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String BLOCKCHAIN_TOPIC = "blockchain.transactions";
    private static final String USER_REGISTRATION_TOPIC = "users.registration";
    private static final String USER_KEY_ROTATION_TOPIC = "users.key-rotation";
    private static final String USER_ROLE_TOPIC = "users.role-change";
    private static final String CHAT_TOPIC = "chats.events";

    public KafkaProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendDIDEvent(DIDEvent event) {
        String topic = getTopicForEvent(event.getEventType());
        kafkaTemplate.send(topic, event);
        log.info("Message {} has been successfully sent to topic: {}", event, topic);
    }

    private String getTopicForEvent(EventType eventType) {
        return switch (eventType) {
            case USER_REGISTERED -> USER_REGISTRATION_TOPIC;
            case USER_KEY_ROTATED -> USER_KEY_ROTATION_TOPIC;
            case USER_ROLE_CHANGED -> USER_ROLE_TOPIC;
            case CHAT_CREATED -> CHAT_TOPIC;
            default -> BLOCKCHAIN_TOPIC;
        };
    }
}
