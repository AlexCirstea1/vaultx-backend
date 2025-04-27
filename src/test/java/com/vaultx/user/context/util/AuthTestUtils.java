package com.vaultx.user.context.util;

import com.vaultx.user.context.model.authentication.request.LoginDTO;
import com.vaultx.user.context.model.authentication.response.LoginResponseDTO;
import com.vaultx.user.context.model.authentication.response.RegistrationDTO;
import com.vaultx.user.context.model.authentication.response.UserResponseDTO;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

/**
 * Helper utilities for authentication tests
 */
public class AuthTestUtils {

    /**
     * Register a new user
     */
    public static UserResponseDTO registerUser(TestRestTemplate http, String email, String username, String password) {
        RegistrationDTO reg = new RegistrationDTO(email, username, password);
        ResponseEntity<UserResponseDTO> response = http.postForEntity("/api/auth/register", reg, UserResponseDTO.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Registration failed with status: " + response.getStatusCode());
        }
        return response.getBody();
    }

    /**
     * Login a user and get auth tokens
     */
    public static LoginResponseDTO loginUser(TestRestTemplate http, String username, String password) {
        LoginDTO login = new LoginDTO(username, password);
        ResponseEntity<LoginResponseDTO> response =
                http.postForEntity("/api/auth/login", login, LoginResponseDTO.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Login failed with status: " + response.getStatusCode());
        }
        return response.getBody();
    }

    /**
     * Verify if a token is valid
     */
    public static boolean verifyToken(TestRestTemplate http, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<Boolean> response =
                http.exchange("/api/auth/verify", HttpMethod.GET, requestEntity, Boolean.class);
        return response.getStatusCode().is2xxSuccessful() && Boolean.TRUE.equals(response.getBody());
    }

    /**
     * Refresh an access token
     */
    public static LoginResponseDTO refreshToken(TestRestTemplate http, String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> requestEntity = new HttpEntity<>("{\"refresh_token\":\"" + refreshToken + "\"}", headers);

        ResponseEntity<LoginResponseDTO> response =
                http.postForEntity("/api/auth/refresh", requestEntity, LoginResponseDTO.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Token refresh failed with status: " + response.getStatusCode());
        }
        return response.getBody();
    }

    /**
     * Logout a user
     */
    public static void logout(TestRestTemplate http, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        http.exchange("/api/auth/logout", HttpMethod.POST, requestEntity, Void.class);
    }
}
