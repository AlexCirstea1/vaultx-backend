package ro.cloud.security.user.context.model.messaging;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ro.cloud.security.user.context.model.user.User;

@Entity
@Table(name = "group_chat_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    // Reference the GroupChat entity instead of storing a raw UUID
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private GroupChat group;

    // The sender is a User entity
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Instant timestamp;
}
