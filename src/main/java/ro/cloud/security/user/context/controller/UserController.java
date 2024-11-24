package ro.cloud.security.user.context.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.cloud.security.user.context.model.dto.UserResponseDTO;
import ro.cloud.security.user.context.service.UserService;

import java.util.UUID;

@RestController
@RequestMapping("/user")
@CrossOrigin("*")
@AllArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<UserResponseDTO> getUser(HttpServletRequest request) {
        return ResponseEntity.ok(userService.getUser(request));
    }

    @GetMapping("/{id}")
    public UserResponseDTO getUserById(@PathVariable UUID id) {
        return userService.getUserById(id);
    }

    @DeleteMapping
    public ResponseEntity<String> deleteUser(HttpServletRequest request) {
        return ResponseEntity.ok(userService.deleteUser(request));
    }
}
