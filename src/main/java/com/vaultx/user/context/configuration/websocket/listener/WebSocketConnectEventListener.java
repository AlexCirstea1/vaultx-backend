package com.vaultx.user.context.configuration.websocket.listener;

import com.vaultx.user.context.service.user.PresenceService;
import java.security.Principal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;

@Component
@Slf4j
public class WebSocketConnectEventListener implements ApplicationListener<SessionConnectedEvent> {

    private final PresenceService presenceService;

    public WebSocketConnectEventListener(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @Override
    public void onApplicationEvent(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();
        if (user != null) {
            log.info("User connected: {}", user.getName());
            presenceService.markUserOnline(user.getName());
            // (Optional) You can also broadcast this presence change to a topic
        }
    }
}
