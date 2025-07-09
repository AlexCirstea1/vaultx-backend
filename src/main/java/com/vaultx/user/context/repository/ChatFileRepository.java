package com.vaultx.user.context.repository;

import com.vaultx.user.context.model.file.ChatFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ChatFileRepository extends JpaRepository<ChatFile, UUID> {

    @Modifying
    @Query("DELETE FROM ChatFile cf WHERE cf.message.id = :messageId")
    void deleteByMessageId(UUID messageId);
}
