package ro.cloud.security.user.context.model.messaging;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "group_chat")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupChat {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String groupName;

    // A collection of user IDs participating in the group chat
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "group_chat_participants", joinColumns = @JoinColumn(name = "group_chat_id"))
    @Column(name = "participant_id")
    private Set<String> participantIds = new HashSet<>();

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;
}
