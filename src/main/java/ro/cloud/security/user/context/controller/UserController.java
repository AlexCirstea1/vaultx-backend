package ro.cloud.security.user.context.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.cloud.security.user.context.model.authentication.response.UserResponseDTO;
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
                            content = @Content(schema = @Schema(implementation = UserResponseDTO.class))
                    ),
                    @ApiResponse(responseCode = "401", description = "User not authenticated", content = @Content)
            }
    )
    public ResponseEntity<UserResponseDTO> getUser(HttpServletRequest request) {
        return ResponseEntity.ok(userService.getUser(request));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get user by ID",
            description = "Returns information about a user by their UUID",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "User information retrieved successfully",
                            content = @Content(schema = @Schema(implementation = UserResponseDTO.class))
                    ),
                    @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
            }
    )
    public UserResponseDTO getUserById(
            @Parameter(description = "User UUID")
            @PathVariable UUID id) {
        return userService.getUserById(id);
    }

    @DeleteMapping
    @Operation(
            summary = "Delete current user",
            description = "Deletes the currently authenticated user's account and all associated data",
            responses = {
                    @ApiResponse(responseCode = "200", description = "User deleted successfully"),
                    @ApiResponse(responseCode = "401", description = "User not authenticated", content = @Content)
            }
    )
    public ResponseEntity<String> deleteUser(HttpServletRequest request) {
        return ResponseEntity.ok(userService.deleteUser(request));
    }
}