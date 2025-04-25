package ro.cloud.security.user.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;
import ro.cloud.security.user.context.model.user.Role;
import ro.cloud.security.user.context.model.user.RoleType;
import ro.cloud.security.user.context.repository.RoleRepository;
import ro.cloud.security.user.context.repository.UserRepository;

@SpringBootApplication
@AllArgsConstructor
@EnableCaching
@EnableScheduling
@Slf4j
public class UserContextApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(UserContextApplication.class, args);
    }

    private final RoleRepository roleRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void run(String... args) {
        deleteCache();

        List<String> createdRoles = new ArrayList<>();
        for (RoleType roleType : RoleType.values()) {
            if (roleRepository.findByAuthority(roleType.getValue()).isEmpty()) {
                roleRepository.save(Role.from(roleType));
                createdRoles.add(roleType.getValue());
            }
        }
        if (!createdRoles.isEmpty()) {
            log.info("Created roles: {}", String.join(", ", createdRoles));
        } else {
            log.info("All roles already exist in the database");
        }
    }

    private void deleteCache() {
        Objects.requireNonNull(redisTemplate.getConnectionFactory())
                .getConnection()
                .serverCommands()
                .flushAll();
        log.info("Redis cache cleared on application startup");
    }
}
