package ro.cloud.security.user.context.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserReportRequest {
    private String userId;
    private String reason;
}