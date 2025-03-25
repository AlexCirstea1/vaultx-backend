package ro.cloud.security.user.context.service.authentication;

import com.github.javafaker.Faker;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyPair;
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
import ro.cloud.security.user.context.exception.UserAlreadyExistsException;
import ro.cloud.security.user.context.model.EventType;
import ro.cloud.security.user.context.model.authentication.response.RegistrationDTO;
import ro.cloud.security.user.context.model.authentication.response.UserResponseDTO;
import ro.cloud.security.user.context.model.user.Role;
import ro.cloud.security.user.context.model.user.User;
import ro.cloud.security.user.context.repository.RoleRepository;
import ro.cloud.security.user.context.repository.UserRepository;
import ro.cloud.security.user.context.service.BlockchainService;
import ro.cloud.security.user.context.utils.KeyGeneratorUtility;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ModelMapper mapper;
    private final BlockchainService blockchainService;

    public UserResponseDTO registerUser(RegistrationDTO dto) {
        String encodedPassword = passwordEncoder.encode(dto.getPassword());

        if (userRepository.findUserByUsername(dto.getUsername()).isPresent()
                || userRepository.findUserByEmail(dto.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("Try a different username or password");
        }

        try {
            return createUser(dto.getUsername(), dto.getEmail(), encodedPassword);
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
                    passwordEncoder.encode(password));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error generating keys", e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private UserResponseDTO createUser(String username, String email, String encodedPassword)
            throws NoSuchAlgorithmException, IOException {

        var userRole = roleRepository.findByAuthority("USER").orElseThrow();

        Set<Role> roles = new HashSet<>();
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

        // 8) Log event on blockchain
        blockchainService.recordDIDEvent(user.getId(), user.getPublicKey(), EventType.REGISTER);

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
