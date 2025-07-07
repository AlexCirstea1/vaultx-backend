package com.vaultx.user.context.controller;

import com.vaultx.user.context.service.user.PresenceService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class PresenceController {

    private final PresenceService presenceService;

    public PresenceController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @MessageMapping("/heartbeat")
    public void heartbeat(Principal principal) {
        if (principal != null) {
            // Simply update the user's status as online and update lastSeen
            presenceService.markUserOnline(principal.getName());
        }
    }
}
