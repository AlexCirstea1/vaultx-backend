package com.vaultx.user.context.model.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vaultx.user.context.model.authentication.response.LoginResponseDTO;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserSession {
    @JsonProperty("data")
    private LoginResponseDTO user;

    @JsonProperty("client_ip")
    private String clientIp;

    @JsonProperty("user_agent")
    private String userAgent;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
