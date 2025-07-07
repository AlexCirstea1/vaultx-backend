package com.vaultx.user.context.model.activity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityResponseDTO {
    private String id;
    private String type;
    private String description;
    private Instant timestamp;
    private boolean isUnusual;
    private String details;
}
