package ro.cloud.security.user.context.model.authentication.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarkReadRequest {
    private List<UUID> messageIds;
}
