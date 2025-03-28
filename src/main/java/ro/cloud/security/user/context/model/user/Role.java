package ro.cloud.security.user.context.model.user;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;

@Entity
@Table(name = "roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role implements GrantedAuthority {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "role_id")
    private Long roleId;

    @Column(nullable = false)
    private String authority;

    public static Role from(RoleType roleType) {
        return Role.builder().authority(roleType.getValue()).build();
    }

    @Override
    public String getAuthority() {
        return authority;
    }

    public RoleType getRoleType() {
        return RoleType.valueOf(authority);
    }
}