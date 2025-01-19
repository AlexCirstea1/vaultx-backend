package ro.cloud.security.user.context.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Use /topic for public broadcasts, /queue for private user-based messages
        registry.enableSimpleBroker("/topic", "/queue");
        // Inbound messages to @MessageMapping endpoints have this prefix
        registry.setApplicationDestinationPrefixes("/app");
        // For private messages to a user: messagingTemplate.convertAndSendToUser(userId, ...)
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("*") // Adjust for production
                .withSockJS(); // (Optional) fallback for older browsers
    }
}
