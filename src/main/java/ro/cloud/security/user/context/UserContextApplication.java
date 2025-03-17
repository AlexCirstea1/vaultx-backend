package ro.cloud.security.user.context;

import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import ro.cloud.security.user.context.model.user.Role;
import ro.cloud.security.user.context.repository.RoleRepository;
import ro.cloud.security.user.context.repository.UserRepository;

@SpringBootApplication
@AllArgsConstructor
@EnableCaching
@Slf4j
public class UserContextApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(UserContextApplication.class, args);
    }

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void run(String... args) {

        deleteCache();

        if (userRepository.findUserByUsername("admin").isPresent()
                || roleRepository.findByAuthority("ADMIN").isPresent()
                || roleRepository.findByAuthority("USER").isPresent()) return;

        var adminRole = roleRepository.save(Role.builder().authority("ADMIN").build());
        var userRole = roleRepository.save(Role.builder().authority("USER").build());

        //		Set<Role> roles = new HashSet<>();
        //		roles.add(adminRole);
        //		roles.add(userRole);
        //
        //		var admin = userRepository.save(User.builder()
        //				.username("admin")
        //				.email("admin@mail.com")
        //				.password(passwordEncoder.encode("admin"))
        //				.authorities(roles)
        //				.createdAt(Instant.now())
        //				.updatedAt(Instant.now())
        //				.build());
    }

    private void deleteCache() {
        Objects.requireNonNull(redisTemplate.getConnectionFactory())
                .getConnection()
                .serverCommands()
                .flushAll();
        log.info("Redis cache cleared on application startup");
    }
}
