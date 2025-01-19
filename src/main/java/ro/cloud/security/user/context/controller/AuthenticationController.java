package ro.cloud.security.user.context.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.cloud.security.user.context.model.dto.LoginDTO;
import ro.cloud.security.user.context.model.dto.LoginResponseDTO;
import ro.cloud.security.user.context.model.dto.RegistrationDTO;
import ro.cloud.security.user.context.model.dto.UserResponseDTO;
import ro.cloud.security.user.context.service.AuthenticationService;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin("*")
@AllArgsConstructor
public class AuthenticationController {
    private final AuthenticationService authenticationService;

    @GetMapping
    public ResponseEntity.BodyBuilder hello() {
        return ResponseEntity.ok();
    }

    @GetMapping("/verify")
    public ResponseEntity<Boolean> verifyToken(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
        boolean isValid = authenticationService.verifyToken(token);
        return ResponseEntity.ok(isValid);
    }

    @PostMapping("/pin/save")
    public ResponseEntity<Void> savePin(HttpServletRequest request, @RequestParam String pin) {
        authenticationService.savePin(request, pin);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/pin/verify")
    public ResponseEntity<Boolean> verifyPin(HttpServletRequest request, @RequestParam String pin) {
        return ResponseEntity.ok(authenticationService.verifyPin(request, pin));
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponseDTO> registerUser(@RequestBody RegistrationDTO dto) {
        return ResponseEntity.ok(authenticationService.registerUser(dto));
    }

    @PostMapping("/register/default")
    public ResponseEntity<UserResponseDTO> registerDefaultUser(@RequestBody String password) {
        return ResponseEntity.ok(authenticationService.registerRandomUser(password));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> loginUser(HttpServletRequest request, @RequestBody LoginDTO dto) {
        return ResponseEntity.ok(authenticationService.loginUser(request, dto));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDTO> refreshAccessToken(
            HttpServletRequest request, @RequestBody String refreshTokenJson) {
        return ResponseEntity.ok(authenticationService.refreshToken(request, refreshTokenJson));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authenticationService.logout(request);
        return ResponseEntity.ok().build();
    }
}
