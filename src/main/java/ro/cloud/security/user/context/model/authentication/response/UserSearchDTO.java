package ro.cloud.security.user.context.model.authentication.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class UserSearchDTO {
    private UUID id;
    private String username;
}
