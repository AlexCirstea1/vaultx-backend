package ro.cloud.security.user.context.model.activity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ro.cloud.security.user.context.model.user.User;

@Entity
@Table(name = "user_activities")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Activity {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType type;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private boolean isUnusual;

    @Column(length = 1000)
    private String details;
}
