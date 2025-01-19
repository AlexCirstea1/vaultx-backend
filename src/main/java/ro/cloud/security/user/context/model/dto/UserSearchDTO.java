package ro.cloud.security.user.context.model.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserSearchDTO {
    private UUID id;
    private String username;
}
