package ro.cloud.security.user.context.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ro.cloud.security.user.context.model.user.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findUserByUsername(String username);

    Optional<User> findUserByEmail(String email);

    List<User> findTop10ByUsernameContainingIgnoreCase(String usernamePart);
}
