package ro.cloud.security.user.context.model.activity;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
