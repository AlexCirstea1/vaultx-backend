package ro.cloud.security.user.context.model.authentication.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignatureVerificationRequest {
    private String message; // The challenge
    private String signature; // The user's signature in Base64
}
