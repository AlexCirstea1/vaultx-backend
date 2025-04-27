package com.vaultx.user.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaultx.user.context.model.authentication.request.LoginDTO;
import com.vaultx.user.context.model.authentication.response.LoginResponseDTO;
import com.vaultx.user.context.model.authentication.response.RegistrationDTO;
import com.vaultx.user.context.model.authentication.response.UserResponseDTO;
import com.vaultx.user.context.util.AuthTestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

class AuthenticationIT extends BaseIT {

    @Test
    void registerThenLoginHappyPath() {
        // Direct API call approach - test the raw endpoints
        RegistrationDTO reg = new RegistrationDTO("alice@local", "Alice", "P4ss!");
        ResponseEntity<UserResponseDTO> regResp = http.postForEntity("/api/auth/register", reg, UserResponseDTO.class);
        assertThat(regResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        LoginDTO login = new LoginDTO("Alice", "P4ss!");
        ResponseEntity<LoginResponseDTO> loginResp =
                http.postForEntity("/api/auth/login", login, LoginResponseDTO.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertNotNull(loginResp.getBody());
        assertThat(loginResp.getBody().getAccessToken()).isNotBlank();
    }

    @Test
    void completeAuthFlowTest() {
        // Using helper methods approach - cleaner test code
        UserResponseDTO user = AuthTestUtils.registerUser(http, "user@test.com", "TestUser", "P4ssw0rd!");
        Assertions.assertNotNull(user);

        LoginResponseDTO loginResponse = AuthTestUtils.loginUser(http, "TestUser", "P4ssw0rd!");
        String accessToken = loginResponse.getAccessToken();
        String refreshToken = loginResponse.getRefreshToken();
        assertThat(accessToken).isNotEmpty();

        boolean isValid = AuthTestUtils.verifyToken(http, accessToken);
        assertThat(isValid).isTrue();

        LoginResponseDTO refreshResponse = AuthTestUtils.refreshToken(http, refreshToken);
        String newAccessToken = refreshResponse.getAccessToken();
        assertThat(newAccessToken).isNotBlank();
        assertThat(newAccessToken).isNotEmpty();

        AuthTestUtils.logout(http, newAccessToken);
    }

    @Test
    void invalidTokenVerificationTest() {
        // Register a user to establish a valid session context
        AuthTestUtils.registerUser(http, "test1@example.com", "TestUser1", "P4ssw0rd!");

        // Test with invalid token
        String invalidToken = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJmYWtlIn0.fake";
        HttpHeaders headers = createAuthHeaders(invalidToken);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<String> verifyResp =
                http.exchange("/api/auth/verify", HttpMethod.GET, requestEntity, String.class);

        boolean validTest = verifyResp.getStatusCode().equals(HttpStatus.UNAUTHORIZED)
                || (verifyResp.getStatusCode().equals(HttpStatus.OK) && "false".equals(verifyResp.getBody()));

        assertThat(validTest).isTrue();
    }

    @Test
    void invalidRefreshTokenTest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> refreshRequest = new HttpEntity<>("{\"refresh_token\":\"invalid-token\"}", headers);

        ResponseEntity<String> refreshResp = http.postForEntity("/api/auth/refresh", refreshRequest, String.class);

        assertThat(refreshResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
