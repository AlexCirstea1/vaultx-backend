package com.vaultx.user.context.service.authentication;

import com.github.javafaker.Faker;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.vaultx.user.context.exception.UserAlreadyExistsException;
import com.vaultx.user.context.model.authentication.response.RegistrationDTO;
import com.vaultx.user.context.model.authentication.response.UserResponseDTO;
import com.vaultx.user.context.model.user.Role;
import com.vaultx.user.context.model.user.RoleType;
import com.vaultx.user.context.model.user.User;
import com.vaultx.user.context.repository.RoleRepository;
import com.vaultx.user.context.repository.UserRepository;
import com.vaultx.user.context.service.BlockchainService;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ModelMapper mapper;
    private final BlockchainService blockchainService;

    public UserResponseDTO registerUser(HttpServletRequest request, RegistrationDTO dto) {
        String encodedPassword = passwordEncoder.encode(dto.getPassword());

        if (userRepository.findUserByUsername(dto.getUsername()).isPresent()
                || userRepository.findUserByEmail(dto.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("Try a different username or password");
        }

        try {
            String roleValue = RoleType.VERIFIED.getValue();

            // Check if request is from Postman and set ADMIN role
            if (request != null) {
                String userAgent = request.getHeader("User-Agent");
                if (userAgent != null && userAgent.contains("Postman")) {
                    log.info("Registration from Postman detected, assigning ADMIN role");
                    roleValue = RoleType.ADMIN.getValue();
                }
            }

            return createUser(dto.getUsername(), dto.getEmail(), encodedPassword, roleValue);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error generating keys", e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public UserResponseDTO registerRandomUser(String password) {
        Faker faker = new Faker();
        try {
            return createUser(
                    generateUsername(),
                    "%s@vaultx.net".formatted(faker.number().digits(8)),
                    passwordEncoder.encode(password),
                    RoleType.ANONYMOUS.getValue());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error generating keys", e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private UserResponseDTO createUser(String username, String email, String encodedPassword, String role)
            throws NoSuchAlgorithmException, IOException {

        var userDefaultRole = roleRepository
                .findByAuthority(RoleType.USER.getValue())
                .orElseThrow(() -> new RuntimeException("Role not found: " + RoleType.USER.getValue()));

        var userRole =
                roleRepository.findByAuthority(role).orElseThrow(() -> new RuntimeException("Role not found: " + role));

        Set<Role> roles = new HashSet<>();
        roles.add(userDefaultRole);
        roles.add(userRole);

        User user = User.builder()
                .username(username)
                .email(email)
                .password(encodedPassword)
                .authorities(roles)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // 3) Save user (so we have user.id)
        user = userRepository.save(user);

        // 4) Generate the DiceBear URL (for example, "thumbs" style in v9)
        String diceBearUrl = "https://api.dicebear.com/9.x/thumbs/png?seed=" + user.getId();

        // 5) Fetch the SVG text
        user.setProfileImage(fetchDiceBearPngAsBase64(diceBearUrl));
        user = userRepository.save(user);

        // 7) Build response
        UserResponseDTO userResponseDTO = mapper.map(user, UserResponseDTO.class);
        userResponseDTO.setHasPin(user.getPin() != null);

        return userResponseDTO;
    }

    /**
     * Helper method to fetch the text from the DiceBear URL.
     */
    private String fetchDiceBearPngAsBase64(String avatarUrl) throws IOException {
        try (InputStream in = new URL(avatarUrl).openStream()) {
            byte[] pngBytes = in.readAllBytes();
            return Base64.getEncoder().encodeToString(pngBytes);
        }
    }

    private String generateUsername() {
        Faker faker = new Faker();
        String adjective = faker.color().name();
        String animal = faker.animal().name();
        return capitalize(adjective) + capitalize(animal) + faker.number().digits(4);
    }

    private String capitalize(String word) {
        return word.substring(0, 1).toUpperCase() + word.substring(1);
    }
}
