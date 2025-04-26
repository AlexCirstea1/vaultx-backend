package com.vaultx.user.context.model.user;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "user_key_history")
@AllArgsConstructor
@NoArgsConstructor
public class UserKeyHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    // The user to whom this key belonged
    @Column(name = "user_id", nullable = false)
    private String userId;

    // The key version for this historical key
    @Column(name = "key_version", nullable = false)
    private String keyVersion;

    // The old public key (stored as text)
    @Column(name = "public_key", columnDefinition = "TEXT", nullable = false)
    private String publicKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
