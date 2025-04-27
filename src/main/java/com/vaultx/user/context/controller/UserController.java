package com.vaultx.user.context.controller;

import com.vaultx.user.context.model.PublicKeyResponse;
import com.vaultx.user.context.model.activity.ActivityResponseDTO;
import com.vaultx.user.context.model.authentication.response.UserResponseDTO;
import com.vaultx.user.context.model.user.UserReportRequest;
import com.vaultx.user.context.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "User", description = "User management endpoints")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(
            summary = "Get current user",
            description = "Returns information about the currently authenticated user",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "User information retrieved successfully",
                        content = @Content(schema = @Schema(implementation = UserResponseDTO.class))),
                @ApiResponse(responseCode = "401", description = "User not authenticated", content = @Content)
            })
    public ResponseEntity<UserResponseDTO> getCurrentUser(HttpServletRequest request) {
        return ResponseEntity.ok(userService.getUser(request));
    }

    @DeleteMapping
    @Operation(
            summary = "Delete current user",
            description = "Deletes the currently authenticated user's account and all associated data",
            responses = {
                @ApiResponse(responseCode = "200", description = "User deleted successfully"),
                @ApiResponse(responseCode = "401", description = "User not authenticated", content = @Content)
            })
    public ResponseEntity<String> deleteCurrentUser(HttpServletRequest request) {
        return ResponseEntity.ok(userService.deleteUser(request));
    }

    @GetMapping("/public/avatar/{userId}")
    @Operation(
            summary = "Get user avatar",
            description = "Retrieves a user's avatar as SVG image",
            responses = {
                @ApiResponse(responseCode = "200", description = "Avatar retrieved successfully"),
                @ApiResponse(responseCode = "404", description = "User or avatar not found", content = @Content)
            })
    public ResponseEntity<String> getUserAvatar(@PathVariable UUID userId) {
        String avatar = userService.getUserAvatar(userId);
        return ResponseEntity.ok(avatar);
    }

    @GetMapping("/public/{userId}")
    @Operation(
            summary = "Get public user data",
            description = "Retrieves public information about a user by their ID",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "User data retrieved successfully",
                        content = @Content(schema = @Schema(implementation = UserResponseDTO.class))),
                @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
            })
    public ResponseEntity<UserResponseDTO> getPublicUserData(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getUserData(userId.toString()));
    }

    @GetMapping("/publicKey/{userId}")
    @Operation(
            summary = "Get user public key",
            description = "Retrieves a user's public key by their ID",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Public key retrieved successfully",
                        content = @Content(schema = @Schema(implementation = PublicKeyResponse.class))),
                @ApiResponse(responseCode = "404", description = "User or public key not found", content = @Content)
            })
    public ResponseEntity<PublicKeyResponse> getUserPublicKey(@PathVariable UUID userId) {
        PublicKeyResponse publicKey = userService.getUserPublicKey(userId);
        return ResponseEntity.ok(publicKey);
    }

    @PostMapping("/publicKey")
    @Operation(
            summary = "Save or update user public key",
            description = "Saves a new public key or rotates an existing key for the authenticated user",
            responses = {
                @ApiResponse(responseCode = "200", description = "Public key saved successfully"),
                @ApiResponse(responseCode = "400", description = "Invalid or missing public key", content = @Content),
                @ApiResponse(responseCode = "401", description = "User not authenticated", content = @Content)
            })
    public ResponseEntity<String> saveUserPublicKey(
            HttpServletRequest request, @RequestBody(required = false) String publicKey) {
        String response = userService.savePublicKey(request, publicKey);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/report")
    @Operation(
            summary = "Report a user",
            description = "Report a user for inappropriate behavior",
            responses = {
                @ApiResponse(responseCode = "200", description = "User reported successfully"),
                @ApiResponse(responseCode = "400", description = "Invalid request data"),
                @ApiResponse(responseCode = "404", description = "User not found"),
                @ApiResponse(responseCode = "429", description = "Too many reports")
            })
    public ResponseEntity<String> reportUser(HttpServletRequest request, @RequestBody UserReportRequest reportRequest) {
        String response = userService.reportUser(request, reportRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/block/{blockedId}")
    @Operation(summary = "Block a user", description = "Blocks a user by their ID, preventing further interactions")
    public ResponseEntity<Void> blockUser(HttpServletRequest request, @PathVariable UUID blockedId) {
        userService.blockUser(blockedId, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/block/{blockedId}")
    @Operation(summary = "Unblock a user", description = "Unblocks a previously blocked user by their ID")
    public ResponseEntity<Void> unblockUser(HttpServletRequest request, @PathVariable UUID blockedId) {
        userService.unblockUser(blockedId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/block/{blockedId}/status")
    @Operation(summary = "Check block status", description = "Checks if the current user has blocked another user")
    public ResponseEntity<Boolean> isBlocked(HttpServletRequest request, @PathVariable UUID blockedId) {
        boolean isBlocked = userService.isUserBlocked(blockedId, request);
        return ResponseEntity.ok(isBlocked);
    }

    @GetMapping("/blockedBy/{blockedId}/status")
    @Operation(
            summary = "Check if blocked by user",
            description = "Checks if the current user has been blocked by another user")
    public ResponseEntity<Boolean> isBlockedBy(HttpServletRequest request, @PathVariable UUID blockedId) {
        boolean isBlockedBy = userService.isBlockedByUser(blockedId, request);
        return ResponseEntity.ok(isBlockedBy);
    }

    @PostMapping("/blockchain-consent")
    @Operation(
            summary = "Update blockchain consent",
            description = "Updates the user's consent for storing data on the blockchain")
    public ResponseEntity<Void> updateBlockchainConsent(HttpServletRequest request, @RequestParam boolean consent) {
        userService.updateBlockchainConsent(consent, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/public/{userId}/roles")
    @Operation(summary = "Get user roles", description = "Retrieves roles assigned to a specific user by their ID")
    public ResponseEntity<List<String>> getUserRoles(@PathVariable UUID userId) {
        List<String> roles = userService.getUserRoles(userId);
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/activities")
    @Operation(summary = "Get user activities", description = "Retrieves recent activities for the authenticated user")
    public ResponseEntity<List<ActivityResponseDTO>> getUserActivities(
            HttpServletRequest request, @RequestParam(defaultValue = "all") String type) {
        List<ActivityResponseDTO> activities = userService.getUserActivities(type, request);
        return ResponseEntity.ok(activities);
    }
}
