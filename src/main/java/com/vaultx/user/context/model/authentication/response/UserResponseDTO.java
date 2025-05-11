package com.vaultx.user.context.model.authentication.response;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserResponseDTO {
    private UUID id;
    private String username;
    private String email;
    private boolean hasPin;
    private boolean blockchainConsent;
    private Instant lastSeen;
    private boolean isOnline;
}
