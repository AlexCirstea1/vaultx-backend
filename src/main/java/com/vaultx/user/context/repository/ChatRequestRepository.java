package com.vaultx.user.context.repository;

import com.vaultx.user.context.model.messaging.ChatRequest;
import com.vaultx.user.context.model.messaging.ChatRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatRequestRepository extends JpaRepository<ChatRequest, UUID> {

    List<ChatRequest> findByRecipient_IdAndStatus(UUID recipientId, ChatRequestStatus status);

    @Query(
            "select cr from ChatRequest cr where cr.requester.id = :requester and cr.recipient.id = :recipient and cr.status = :status")
    Optional<ChatRequest> findByRequesterAndRecipientAndStatus(
            UUID requester, UUID recipient, ChatRequestStatus status);

    @Modifying
    @Query("update ChatRequest cr set cr.status = 'EXPIRED' where cr.status = 'PENDING' and cr.createdAt < :threshold")
    int expireOld(LocalDateTime threshold);
}
