package ro.cloud.security.user.context.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ro.cloud.security.user.context.model.authentication.response.UserResponseDTO;
import ro.cloud.security.user.context.model.user.User;
import ro.cloud.security.user.context.service.authentication.UserService;

import java.util.UUID;

@RestController
@RequestMapping("/api/user")
@CrossOrigin("*")
@AllArgsConstructor
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

    @GetMapping("/avatar/{id}")
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

    @GetMapping("/publicKey/{id}")
    @Transactional(readOnly = true)
    @Operation(
            summary = "Get user public key",
            description = "Retrieves a user's public key by their ID",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Public key retrieved successfully"),
                    @ApiResponse(responseCode = "404", description = "User or public key not found", content = @Content)
            })
    public ResponseEntity<String> getUserPublicKey(@PathVariable UUID id) {
        try {
            String publicKey = userService.getUserPublicKey(id);

            if (publicKey == null || publicKey.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);

            return new ResponseEntity<>(publicKey, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/publicKey")
    public ResponseEntity<String> saveUserPublicKey(
            HttpServletRequest request,
            @RequestBody(required = false) String publicKey
    ) {
        if (publicKey == null || publicKey.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Public key is required.");
        }
        var response = userService.saveUserPublicKey(request, publicKey.trim());
        return ResponseEntity.ok(response);
    }
}
