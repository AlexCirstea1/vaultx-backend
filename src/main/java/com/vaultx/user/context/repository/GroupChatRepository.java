package com.vaultx.user.context.repository;

import com.vaultx.user.context.model.messaging.GroupChat;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupChatRepository extends JpaRepository<GroupChat, UUID> {}
