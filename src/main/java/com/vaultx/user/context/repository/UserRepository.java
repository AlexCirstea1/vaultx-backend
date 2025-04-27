package com.vaultx.user.context.repository;

import com.vaultx.user.context.model.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findUserByUsername(String username);

    Optional<User> findUserByEmail(String email);

    List<User> findTop10ByUsernameContainingIgnoreCase(String usernamePart);
}
