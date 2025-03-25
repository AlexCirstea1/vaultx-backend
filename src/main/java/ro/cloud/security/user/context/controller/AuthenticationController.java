package ro.cloud.security.user.context.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.cloud.security.user.context.model.authentication.request.LoginDTO;
import ro.cloud.security.user.context.model.authentication.request.SignatureVerificationRequest;
import ro.cloud.security.user.context.model.authentication.response.LoginResponseDTO;
import ro.cloud.security.user.context.model.authentication.response.RegistrationDTO;
import ro.cloud.security.user.context.model.authentication.response.UserResponseDTO;
import ro.cloud.security.user.context.service.DIDService;
import ro.cloud.security.user.context.service.authentication.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin("*")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and authorization endpoints")
public class AuthenticationController {
    private final LoginService loginService;
    private final RegistrationService registrationService;
    private final PinService pinService;
    private final DIDService didService;
    private final UserService userService;

    @GetMapping
    @Operation(summary = "Health check endpoint", description = "Used to check if the auth API is up")
    public ResponseEntity.BodyBuilder hello() {
        return ResponseEntity.ok();
    }

    @GetMapping("/verify")
    @Operation(
            summary = "Verify a JWT token",
            description = "Checks if a provided JWT token is valid and not expired",
            responses = {
                @ApiResponse(responseCode = "200", description = "Token validity status"),
                @ApiResponse(responseCode = "401", description = "Invalid token format", content = @Content)
            })
    public ResponseEntity<Boolean> verifyToken(
            @Parameter(description = "JWT token with Bearer prefix") @RequestHeader("Authorization")
                    String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
        boolean isValid = loginService.verifyToken(token);
        return ResponseEntity.ok(isValid);
    }

    @PostMapping("/pin/save")
    @Operation(
            summary = "Save user PIN",
            description = "Saves a 6-digit PIN for the authenticated user",
            responses = {
                @ApiResponse(responseCode = "200", description = "PIN saved successfully"),
                @ApiResponse(responseCode = "400", description = "Invalid PIN format", content = @Content),
                @ApiResponse(responseCode = "401", description = "User not authenticated", content = @Content)
            })
    public ResponseEntity<Void> savePin(
            HttpServletRequest request, @Parameter(description = "6-digit PIN") @RequestParam String pin) {
        pinService.savePin(request, pin);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/pin/verify")
    @Operation(
            summary = "Verify user PIN",
            description = "Verifies that the provided PIN matches the stored PIN for the authenticated user",
            responses = {
                @ApiResponse(responseCode = "200", description = "PIN verification result"),
                @ApiResponse(responseCode = "400", description = "Invalid PIN format", content = @Content),
                @ApiResponse(responseCode = "401", description = "User not authenticated", content = @Content)
            })
    public ResponseEntity<Boolean> verifyPin(
            HttpServletRequest request, @Parameter(description = "6-digit PIN") @RequestParam String pin) {
        return ResponseEntity.ok(pinService.verifyPin(request, pin));
    }

    @PostMapping("/register")
    @Operation(
            summary = "Register a new user",
            description = "Creates a new user account with the provided details",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "User registered successfully",
                        content = @Content(schema = @Schema(implementation = UserResponseDTO.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid registration data or user already exists",
                        content = @Content)
            })
    public ResponseEntity<UserResponseDTO> registerUser(@RequestBody RegistrationDTO dto) {
        return ResponseEntity.ok(registrationService.registerUser(dto));
    }

    @PostMapping("/register/default")
    @Operation(
            summary = "Register a random user",
            description = "Creates a new user account with random credentials but specified password",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Random user registered successfully",
                        content = @Content(schema = @Schema(implementation = UserResponseDTO.class))),
                @ApiResponse(responseCode = "400", description = "Invalid password data", content = @Content)
            })
    public ResponseEntity<UserResponseDTO> registerDefaultUser(@RequestBody String password) {
        return ResponseEntity.ok(registrationService.registerRandomUser(password));
    }

    @PostMapping("/login")
    @Operation(
            summary = "User login",
            description = "Authenticates a user and returns access and refresh tokens",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Login successful",
                        content = @Content(schema = @Schema(implementation = LoginResponseDTO.class))),
                @ApiResponse(responseCode = "401", description = "Authentication failed", content = @Content)
            })
    public ResponseEntity<LoginResponseDTO> loginUser(HttpServletRequest request, @RequestBody LoginDTO dto) {
        return ResponseEntity.ok(loginService.loginUser(request, dto));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh access token",
            description = "Generates a new access token using a valid refresh token",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Token refreshed successfully",
                        content = @Content(schema = @Schema(implementation = LoginResponseDTO.class))),
                @ApiResponse(responseCode = "401", description = "Invalid refresh token", content = @Content)
            })
    public ResponseEntity<LoginResponseDTO> refreshAccessToken(
            HttpServletRequest request, @RequestBody String refreshTokenJson) {
        return ResponseEntity.ok(loginService.refreshToken(request, refreshTokenJson));
    }

    @PostMapping("/logout")
    @Operation(
            summary = "User logout",
            description = "Invalidates the user's session and tokens",
            responses = {
                @ApiResponse(responseCode = "200", description = "Logout successful"),
                @ApiResponse(responseCode = "401", description = "User not authenticated", content = @Content)
            })
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        loginService.logout(request, userService);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/challenge")
    @Operation(
            summary = "Generate DID challenge",
            description = "Generates a random challenge string for signature verification",
            responses = {@ApiResponse(responseCode = "200", description = "Challenge generated successfully")})
    public ResponseEntity<String> getChallenge() {
        return ResponseEntity.ok(didService.generateChallenge());
    }

    @PostMapping("/verify-signature")
    @Operation(
            summary = "Verify DID signature",
            description = "Verifies a cryptographic signature using the user's public DID key",
            responses = {
                @ApiResponse(responseCode = "200", description = "Signature verification result"),
                @ApiResponse(responseCode = "401", description = "User not authenticated", content = @Content),
                @ApiResponse(responseCode = "400", description = "Invalid signature data", content = @Content)
            })
    public ResponseEntity<Boolean> verifySignature(
            HttpServletRequest request, @RequestBody SignatureVerificationRequest body) {
        var user = userService.getSessionUser(request);
        boolean isValid = didService.verifyUserSignature(user.getPublicKey(), body.getMessage(), body.getSignature());
        return ResponseEntity.ok(isValid);
    }
}
