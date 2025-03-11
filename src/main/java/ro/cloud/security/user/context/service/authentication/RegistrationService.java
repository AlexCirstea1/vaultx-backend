package ro.cloud.security.user.context.service.authentication;

import com.github.javafaker.Faker;
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
import ro.cloud.security.user.context.utils.CipherUtils;
import ro.cloud.security.user.context.utils.KeyGeneratorUtility;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

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
        }
    }

    private UserResponseDTO createUser(String username, String email, String encodedPassword)
            throws NoSuchAlgorithmException {

        var userRole = roleRepository.findByAuthority("USER").orElseThrow();

        // Generate DID key pair
        KeyPair keyPair = KeyGeneratorUtility.generateRSAKey();
        String publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String encryptedPrivateKey = CipherUtils.encryptPrivateKey(privateKeyBase64);

        // Set up user roles
        Set<Role> roles = new HashSet<>();
        roles.add(userRole);

        // Create and save user
        User user = User.builder()
                .username(username)
                .email(email)
                .password(encodedPassword)
                .authorities(roles)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .publicDid(publicKeyBase64)
                .privateDidEncrypted(encryptedPrivateKey)
                .build();

        user = userRepository.save(user);

        // Create response DTO
        UserResponseDTO userResponseDTO = mapper.map(user, UserResponseDTO.class);
        userResponseDTO.setHasPin(user.getPin() != null);

        blockchainService.recordDIDEvent(user.getId(), user.getPublicDid(), EventType.REGISTER);

        return userResponseDTO;
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