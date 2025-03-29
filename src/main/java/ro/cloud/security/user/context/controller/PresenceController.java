package ro.cloud.security.user.context.controller;

import java.security.Principal;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import ro.cloud.security.user.context.service.PresenceService;

@Controller
@CrossOrigin("*")
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
