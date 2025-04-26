package com.vaultx.user.context.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.vaultx.user.context.model.messaging.GroupChat;

@Repository
public interface GroupChatRepository extends JpaRepository<GroupChat, UUID> {}
