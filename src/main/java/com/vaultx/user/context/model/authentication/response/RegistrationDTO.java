package com.vaultx.user.context.model.authentication.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class RegistrationDTO {
    private String email;
    private String username;
    private String password;
}
