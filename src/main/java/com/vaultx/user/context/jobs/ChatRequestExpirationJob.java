package com.vaultx.user.context.jobs;

import com.vaultx.user.context.repository.ChatRequestRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs every night at 03:15 and marks all PENDING chat‑requests that are older
 * than 30 days as EXPIRED.  Uses a bulk JPQL update for efficiency.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatRequestExpirationJob {

    private final ChatRequestRepository chatRequestRepository;

    /**
     * cron: second minute hour day month dow
     *        0      15     3   *   *    *   ==> 03:15 every day
     */
    @Scheduled(cron = "0 15 3 * * *")
    public void expireOldPendingRequests() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        int affected = chatRequestRepository.expireOld(threshold);
        if (affected > 0) {
            log.info("Expired {} chat requests older than {}", affected, threshold);
        }
    }
}
