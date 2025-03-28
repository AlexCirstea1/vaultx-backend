package ro.cloud.security.user.context.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ro.cloud.security.user.context.model.PublicKeyResponse;
import ro.cloud.security.user.context.model.UserReportRequest;
import ro.cloud.security.user.context.model.activity.ActivityResponseDTO;
import ro.cloud.security.user.context.model.authentication.response.UserResponseDTO;
import ro.cloud.security.user.context.model.user.RoleType;
import ro.cloud.security.user.context.model.user.User;
import ro.cloud.security.user.context.service.ActivityService;
import ro.cloud.security.user.context.service.BlockService;
import ro.cloud.security.user.context.service.ReportService;
import ro.cloud.security.user.context.service.authentication.UserService;

@RestController
@RequestMapping("/api/user")
@CrossOrigin("*")
@AllArgsConstructor
@Tag(name = "User", description = "User management endpoints")
public class UserController {

    private final UserService userService;
    private final ReportService reportService;
    private final BlockService blockService;
    private final ActivityService activityService;

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
    public ResponseEntity<UserResponseDTO> getUser(HttpServletRequest request) {
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
    public ResponseEntity<String> deleteUser(HttpServletRequest request) {
        return ResponseEntity.ok(userService.deleteUser(request));
    }

    @GetMapping("/public/avatar/{id}")
    @Transactional(readOnly = true)
    @Operation(
            summary = "Get user avatar",
            description = "Retrieves a user's avatar as SVG image",
            responses = {
                @ApiResponse(responseCode = "200", description = "Avatar retrieved successfully"),
                @ApiResponse(responseCode = "404", description = "User or avatar not found", content = @Content)
            })
    public ResponseEntity<String> getUserAvatar(@PathVariable UUID id) {
        User user = userService.getUserById(id);
        if (user.getProfileImage() == null) {
            return ResponseEntity.notFound().build();
        }
        // We can label it as text/plain or application/json
        return ResponseEntity.ok(user.getProfileImage());
    }

    @GetMapping("/public/{userid}")
    public ResponseEntity<UserResponseDTO> getUserData(@PathVariable String userid) {
        return ResponseEntity.ok(userService.getUserData(userid));
    }

    @GetMapping("/publicKey/{id}")
    @Transactional(readOnly = true)
    @Operation(
            summary = "Get user public key",
            description = "Retrieves a user's public key by their ID",
            responses = {
                @ApiResponse(responseCode = "200", description = "Public key retrieved successfully"),
                @ApiResponse(responseCode = "404", description = "User or public key not found", content = @Content)
            })
    public ResponseEntity<PublicKeyResponse> getUserPublicKey(@PathVariable UUID id) {
        try {
            var response = userService.getUserPublicKey(id);

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/publicKey")
    public ResponseEntity<String> saveUserPublicKey(
            HttpServletRequest request, @RequestBody(required = false) String publicKey) {
        if (publicKey == null || publicKey.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Public key is required.");
        }
        var response = userService.saveUserPublicKey(request, publicKey.trim());
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

        if (reportRequest.getUserId() == null || reportRequest.getReason() == null) {
            return ResponseEntity.badRequest().body("User ID and reason are required");
        }

        return reportService.reportUser(request, reportRequest.getUserId(), reportRequest.getReason());
    }

    @PostMapping("/block/{blockedId}")
    public ResponseEntity<String> blockUser(@PathVariable UUID blockedId, HttpServletRequest request) {
        UUID blockerId = userService.getSessionUser(request).getId();
        blockService.blockUser(blockerId, blockedId);
        return ResponseEntity.ok("User blocked successfully.");
    }

    @DeleteMapping("/block/{blockedId}")
    public ResponseEntity<String> unblockUser(@PathVariable UUID blockedId, HttpServletRequest request) {
        UUID blockerId = userService.getSessionUser(request).getId();
        blockService.unblockUser(blockerId, blockedId);
        return ResponseEntity.ok("User unblocked successfully.");
    }

    @GetMapping("/block/{blockedId}/status")
    public ResponseEntity<Boolean> checkIfIBlocked(@PathVariable UUID blockedId, HttpServletRequest request) {
        UUID blockerId = userService.getSessionUser(request).getId();
        boolean isBlocked = blockService.isUserBlocked(blockerId, blockedId);
        return ResponseEntity.ok(isBlocked);
    }

    @GetMapping("/blockedBy/{blockedId}/status")
    public ResponseEntity<Boolean> checkIfBlockedMe(@PathVariable UUID blockedId, HttpServletRequest request) {
        UUID currentUserId = userService.getSessionUser(request).getId();
        boolean blockedMe = blockService.isUserBlocked(blockedId, currentUserId);
        return ResponseEntity.ok(blockedMe);
    }

    @PostMapping("/blockchain-consent")
    public ResponseEntity<String> updateBlockchainConsent(@RequestParam boolean consent, HttpServletRequest request) {
        User user = userService.getSessionUser(request);
        userService.setConsent(consent, user);
        return ResponseEntity.ok("Consent updated successfully.");
    }

    @GetMapping("/public/{userId}/roles")
    @Operation(
            summary = "Get user roles",
            description = "Retrieves roles assigned to a specific user by their ID",
            responses = {
                @ApiResponse(responseCode = "200", description = "Roles retrieved successfully"),
                @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
            })
    public ResponseEntity<List<String>> getUserRoles(@PathVariable UUID userId) {
        try {
            User user = userService.getUserById(userId);
            List<String> roles = user.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());

            // Define custom order directly - no reassignment
            final List<String> orderedRoles = List.of(
                    RoleType.VERIFIED.getValue(),
                    RoleType.ANONYMOUS.getValue(),
                    RoleType.ADMIN.getValue(),
                    RoleType.USER.getValue());

            // Sort based on the index in the ordered list
            roles.sort(Comparator.comparing(role -> {
                int index = orderedRoles.indexOf(role);
                return index >= 0 ? index : Integer.MAX_VALUE;
            }));

            return ResponseEntity.ok(roles);
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/activities")
    @Operation(
            summary = "Get user activities",
            description = "Retrieves recent activities for the authenticated user",
            responses = {
                @ApiResponse(responseCode = "200", description = "Activities retrieved successfully"),
                @ApiResponse(responseCode = "401", description = "User not authenticated", content = @Content)
            })
    public ResponseEntity<List<ActivityResponseDTO>> getUserActivities(HttpServletRequest request) {
        User user = userService.getSessionUser(request);
        return ResponseEntity.ok(activityService.getUserActivities(user));
    }
}
