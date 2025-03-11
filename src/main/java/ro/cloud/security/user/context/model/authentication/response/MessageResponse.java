package ro.cloud.security.user.context.model.authentication.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A helper class to structure the response message.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MessageResponse {
    private String type;
    private Object payload;
}
