package com.vaultx.user.context.configuration.websocket.listener;

import com.vaultx.user.context.service.user.PresenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
@Slf4j
public class WebSocketDisconnectEventListener implements ApplicationListener<SessionDisconnectEvent> {

    private final PresenceService presenceService;

    public WebSocketDisconnectEventListener(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @Override
    public void onApplicationEvent(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();
        if (user != null) {
            log.info("User disconnected: {}", user.getName());
            presenceService.markUserOffline(user.getName());
            // (Optional) Broadcast the updated presence status to others
        }
    }
}
