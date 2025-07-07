package com.vaultx.user.context;

import com.vaultx.user.context.model.PublicKeyResponse;
import com.vaultx.user.context.model.activity.ActivityResponseDTO;
import com.vaultx.user.context.model.authentication.response.LoginResponseDTO;
import com.vaultx.user.context.model.authentication.response.UserResponseDTO;
import com.vaultx.user.context.model.user.UserReportRequest;
import com.vaultx.user.context.util.AuthTestUtils;
import com.vaultx.user.context.util.TestCredentialsGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserIT extends BaseIT {

    private String accessToken;
    private UUID userId;
    private String testEmail;
    private String testUsername;

    @BeforeEach
    void setUp() {
        // Create unique credentials
        TestCredentialsGenerator.TestCredentials credentials = generateTestCredentials("user.test", "TestUser");
        testEmail = credentials.getEmail();
        testUsername = credentials.getUsername();

        // Register and login a test user
        AuthTestUtils.registerUser(http, testEmail, testUsername, credentials.getPassword());
        LoginResponseDTO loginResponse = AuthTestUtils.loginUser(http, testUsername, credentials.getPassword());
        accessToken = loginResponse.getAccessToken();

        // Get a user profile to extract ID
        HttpHeaders headers = createAuthHeaders(accessToken);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        ResponseEntity<UserResponseDTO> profileResp =
                http.exchange("/api/user", HttpMethod.GET, requestEntity, UserResponseDTO.class);

        Assertions.assertNotNull(profileResp.getBody());
        userId = profileResp.getBody().getId();
    }

    @Test
    void getCurrentUserTest() {
        HttpHeaders headers = createAuthHeaders(accessToken);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<UserResponseDTO> response =
                http.exchange("/api/user", HttpMethod.GET, requestEntity, UserResponseDTO.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEmail()).isEqualTo(testEmail);
        assertThat(response.getBody().getUsername()).isEqualTo(testUsername);
    }

    @Test
    void getPublicUserDataTest() {
        ResponseEntity<UserResponseDTO> response =
                http.getForEntity("/api/user/public/" + userId, UserResponseDTO.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEmail()).isEqualTo(testEmail);
        assertThat(response.getBody().getUsername()).isEqualTo(testUsername);
    }

    @Test
    void saveAndGetPublicKeyTest() {
        String publicKey =
                "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvwRN7r5tVhGqvXUcHPMa\nWNCcXYx4uGb99hwT5k1HVqG2L9d+ZGIimRx2dLASY78MnU/6iPxOQhdO0kK+Jk9h\nTYW7jgN7JLEfIg9249D+CrcFfWUPzjHYs2dpV2hVDPk98G8LEzPvGLH/BVbTaG0E\nRYRA8S+mXRcKvNJEKUvnUtzZuGWGONx01YwqvbnsAOSAcMdVnfGQQxXozH9mMPaQ\nX8wTeZqULcJRxgIQm4x+AOQKd6B7CuKU5mf+UtsQjho28WL+Ft4bePZLzmFYX1xD\na5W+D4IsuRUE8JrQPQqXMKA2yvs8Mn6/uOLWoXwTEwLY1G5G/FtOcQCJqDIBJPQZ\nwQIDAQAB\n-----END PUBLIC KEY-----";

        // Save the public key
        HttpHeaders headers = createAuthHeaders(accessToken);
        headers.setContentType(MediaType.TEXT_PLAIN);
        HttpEntity<String> requestEntity = new HttpEntity<>(publicKey, headers);

        ResponseEntity<String> saveResponse =
                http.exchange("/api/user/publicKey", HttpMethod.POST, requestEntity, String.class);

        assertThat(saveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Get the public key - add authentication here
        HttpEntity<Void> getEntity = new HttpEntity<>(createAuthHeaders(accessToken));
        ResponseEntity<PublicKeyResponse> getResponse =
                http.exchange("/api/user/publicKey/" + userId, HttpMethod.GET, getEntity, PublicKeyResponse.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().getPublicKey()).isEqualTo(publicKey);
    }

    //    @Test
    void blockUnblockUserTest() {
        // Create another user
        String blockedEmail = "blocked+" + UUID.randomUUID() + "@example.com";
        String blockedUsername = "BlockedUser" + System.currentTimeMillis();

        // Register the user to be blocked
        UserResponseDTO blockedUser = AuthTestUtils.registerUser(http, blockedEmail, blockedUsername, "P4ssw0rd!");
        LoginResponseDTO blockedLogin = AuthTestUtils.loginUser(http, blockedEmail, "P4ssw0rd!");

        // Get blocked user ID directly from the registration response
        UUID blockedId = blockedUser.getId();

        // Verify the blocked user exists
        HttpHeaders blockedHeaders = createAuthHeaders(blockedLogin.getAccessToken());
        HttpEntity<Void> blockedRequestEntity = new HttpEntity<>(blockedHeaders);
        ResponseEntity<UserResponseDTO> blockedProfileResp =
                http.exchange("/api/user", HttpMethod.GET, blockedRequestEntity, UserResponseDTO.class);

        assertThat(blockedProfileResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(blockedProfileResp.getBody()).isNotNull();

        // Block the user with proper headers
        HttpHeaders headers = createAuthHeaders(accessToken);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Void> blockResponse =
                    http.exchange("/api/user/block/" + blockedId, HttpMethod.POST, requestEntity, Void.class);

            assertThat(blockResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Continue with the rest of the test
            ResponseEntity<Boolean> statusResponse = http.exchange(
                    "/api/user/block/" + blockedId + "/status", HttpMethod.GET, requestEntity, Boolean.class);

            assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(statusResponse.getBody()).isTrue();

            // Unblock the user
            ResponseEntity<Void> unblockResponse =
                    http.exchange("/api/user/block/" + blockedId, HttpMethod.DELETE, requestEntity, Void.class);

            assertThat(unblockResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Check status after unblocking
            ResponseEntity<Boolean> afterStatusResponse = http.exchange(
                    "/api/user/block/" + blockedId + "/status", HttpMethod.GET, requestEntity, Boolean.class);

            assertThat(afterStatusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(afterStatusResponse.getBody()).isFalse();
        } catch (Exception e) {
            System.err.println("Error during block test: " + e.getMessage());
            throw e;
        }
    }

    @Test
    void updateBlockchainConsentTest() {
        HttpHeaders headers = createAuthHeaders(accessToken);
        HttpEntity<Void> consentEntity = new HttpEntity<>(headers);

        ResponseEntity<Void> consentResponse =
                http.exchange("/api/user/blockchain-consent?consent=true", HttpMethod.POST, consentEntity, Void.class);

        assertThat(consentResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify consent was updated
        HttpEntity<Void> profileEntity = new HttpEntity<>(headers);
        ResponseEntity<UserResponseDTO> profileResp =
                http.exchange("/api/user", HttpMethod.GET, profileEntity, UserResponseDTO.class);

        Assertions.assertNotNull(profileResp.getBody());
        assertThat(profileResp.getBody().isBlockchainConsent()).isTrue();
    }

    @Test
    void getUserRolesTest() {
        ResponseEntity<List<String>> response = http.exchange(
                "/api/user/public/" + userId + "/roles",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<String>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        // Fixed: Check for individual roles, not combined strings
        assertThat(response.getBody()).contains("VERIFIED", "USER");
    }

    @Test
    void getUserActivitiesTest() {
        HttpHeaders headers = createAuthHeaders(accessToken);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<List<ActivityResponseDTO>> response = http.exchange(
                "/api/user/activities?type=all",
                HttpMethod.GET,
                requestEntity,
                new ParameterizedTypeReference<List<ActivityResponseDTO>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().size()).isGreaterThan(0);
    }

    // @Test
    void reportUserTest() {
        // Create a unique user to report
        String reportedEmail = "reported+" + UUID.randomUUID() + "@example.com";
        String reportedUsername = "ReportedUser" + System.currentTimeMillis();
        AuthTestUtils.registerUser(http, reportedEmail, reportedUsername, "P4ssw0rd!");
        LoginResponseDTO reportedLogin = AuthTestUtils.loginUser(http, reportedEmail, "P4ssw0rd!");

        HttpHeaders headers = createAuthHeaders(reportedLogin.getAccessToken());
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<UserResponseDTO> profileResp =
                http.exchange("/api/user", HttpMethod.GET, requestEntity, UserResponseDTO.class);

        Assertions.assertNotNull(profileResp.getBody());
        String reportedId = profileResp.getBody().getId().toString();

        // Report the user
        HttpHeaders reportHeaders = createAuthHeaders(accessToken);
        UserReportRequest reportRequest = new UserReportRequest(reportedId, "Inappropriate behavior");
        HttpEntity<UserReportRequest> reportEntity = new HttpEntity<>(reportRequest, reportHeaders);

        ResponseEntity<String> reportResponse =
                http.exchange("/api/user/report", HttpMethod.POST, reportEntity, String.class);

        assertThat(reportResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reportResponse.getBody()).contains("User reported successfully");
    }
}
