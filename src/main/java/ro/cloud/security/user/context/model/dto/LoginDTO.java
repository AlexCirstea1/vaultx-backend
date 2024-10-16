package ro.cloud.security.user.context.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class LoginDTO {
    @NonNull
    @NotBlank
    private String username;

    @NonNull
    @NotBlank
    private String password;
}