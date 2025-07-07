package com.vaultx.user.context.model.messaging;

import com.vaultx.user.context.model.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "group_chats")
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

    // Instead of storing participantIds as strings, we create a many-to-many to the User table
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "group_chat_participants",
            joinColumns = @JoinColumn(name = "group_chat_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> participants = new HashSet<>();

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;
}
