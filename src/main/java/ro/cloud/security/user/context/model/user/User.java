package ro.cloud.security.user.context.model.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import ro.cloud.security.user.context.model.activity.Activity;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @JsonIgnore
    @Column(name = "pin")
    private String pin;

    @Column(name = "strike_count")
    private int strikeCount;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "last_seen")
    private Instant lastSeen;

    @Column(name = "is_online")
    private boolean isOnline;

    @Column(name = "refresh_token", length = 2048)
    private String refreshToken;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_role",
            joinColumns = {@JoinColumn(name = "user_id")},
            inverseJoinColumns = {@JoinColumn(name = "role_id")})
    private Set<Role> authorities = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Set<Activity> activities = new HashSet<>();

    @Column(name = "public_key", columnDefinition = "TEXT")
    private String publicKey;

    @Column(name = "current_key_version")
    private String currentKeyVersion;

    @Column(name = "profile_image", columnDefinition = "TEXT")
    private String profileImage;

    @Column(name = "is_enabled")
    @Builder.Default
    private boolean isEnabled = true;

    @Column(name = "blockchain_consent")
    @Builder.Default
    private boolean blockchainConsent = false;

    @ManyToMany
    @JoinTable(
            name = "user_block",
            joinColumns = @JoinColumn(name = "blocker_id"),
            inverseJoinColumns = @JoinColumn(name = "blocked_id"))
    private Set<User> blockedUsers = new HashSet<>();

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }
}
