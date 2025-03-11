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

    private static final String DID_TOPIC = "blockchain-events";
    private static final String REGISTRATION_TOPIC = "user-registration-events";
    private static final String KEY_UPDATE_TOPIC = "key-update-events";

    public KafkaProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendDIDEvent(DIDEvent event) {
        String topic;

        if (event.getEventType() == EventType.REGISTER) {
            topic = REGISTRATION_TOPIC;
        } else if (event.getEventType() == EventType.KEY_UPDATED) {
            topic = KEY_UPDATE_TOPIC;
        } else {
            topic = DID_TOPIC;
        }

        kafkaTemplate.send(topic, event);
        log.info("Message {} has been successfully sent to topic: {}", event, topic);
    }
}