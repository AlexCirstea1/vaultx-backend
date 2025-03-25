package ro.cloud.security.user.context.model.authentication.response;

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
}
