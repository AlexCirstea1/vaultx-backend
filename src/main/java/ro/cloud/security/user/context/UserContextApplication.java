package ro.cloud.security.user.context;

import lombok.AllArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.security.crypto.password.PasswordEncoder;
import ro.cloud.security.user.context.model.Role;
import ro.cloud.security.user.context.model.User;
import ro.cloud.security.user.context.repository.RoleRepository;
import ro.cloud.security.user.context.repository.UserRepository;

import java.util.HashSet;
import java.util.Set;

@SpringBootApplication
@AllArgsConstructor
@EnableCaching
public class UserContextApplication implements CommandLineRunner {

	public static void main(String[] args) {
		SpringApplication.run(UserContextApplication.class, args);
	}


	private final RoleRepository roleRepository;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;


	@Override
	public void run(String... args) {
		if(userRepository.findUserByUsername("admin").isPresent()
				|| roleRepository.findByAuthority("ADMIN").isPresent()
				|| roleRepository.findByAuthority("USER").isPresent()) return;

		var adminRole = roleRepository.save(Role.builder().authority("ADMIN").build());
		var userRole = roleRepository.save(Role.builder().authority("USER").build());

		Set<Role> roles = new HashSet<>();
		roles.add(adminRole);
		roles.add(userRole);

		var admin = userRepository.save(User.builder()
				.username("admin")
				.email("admin@mail.com")
				.password(passwordEncoder.encode("admin"))
				.authorities(roles)
				.build());
	}
}
