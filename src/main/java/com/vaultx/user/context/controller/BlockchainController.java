package com.vaultx.user.context.controller;

import com.vaultx.user.context.model.blockchain.DIDEvent;
import com.vaultx.user.context.model.blockchain.EventHistory;
import com.vaultx.user.context.model.blockchain.StatsResponse;
import com.vaultx.user.context.service.user.BlockchainService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/blockchain/events")
@RequiredArgsConstructor
public class BlockchainController {

    private final BlockchainService bc;

    /**
     * List events for a user, with optional filtering by type and date range.
     */
    @GetMapping
    public List<DIDEvent> list(
            @RequestParam UUID userId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return bc.getEventsByUser(userId).stream()
                .filter(ev -> type == null || ev.getEventType().name().equalsIgnoreCase(type))
                .filter(ev -> {
                    Instant ts = ev.getTimestamp();
                    boolean after = from == null || !ts.isBefore(from);
                    boolean before = to == null || !ts.isAfter(to);
                    return after && before;
                })
                .collect(Collectors.toList());
    }

    /**
     * Search events by payload or type.
     */
    @GetMapping("/search")
    public List<DIDEvent> search(
            @RequestParam UUID userId, @RequestParam(required = false) String type, @RequestParam String q) {
        return list(userId, type, null, null).stream()
                .filter(ev ->
                        ev.getPayload().contains(q) || ev.getEventType().name().contains(q))
                .collect(Collectors.toList());
    }

    /** Retrieve single event details */
    @GetMapping("/{id}")
    public DIDEvent detail(@PathVariable UUID id) {
        return bc.getEvent(id);
    }

    /** Retrieve on-chain history for an event */
    @GetMapping("/{id}/history")
    public List<EventHistory> history(@PathVariable UUID id) {
        return bc.getEventHistory(id);
    }

    /** Export events as CSV */
    @GetMapping("/export")
    public ResponseEntity<InputStreamResource> exportCsv(@RequestParam UUID userId) {
        List<DIDEvent> events = bc.getEventsByUser(userId);
        StringBuilder sb = new StringBuilder("eventId,userId,type,payloadHash,kafkaOffset,timestamp\n");
        events.forEach(ev -> sb.append(ev.toCsvLine()).append("\n"));

        InputStreamResource isr =
                new InputStreamResource(new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8)));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=events.csv")
                .contentType(MediaType.TEXT_PLAIN)
                .body(isr);
    }

    /** Simple stats: count by event type */
    @GetMapping("/stats")
    public StatsResponse stats(@RequestParam UUID userId) {
        List<DIDEvent> events = bc.getEventsByUser(userId);
        Map<String, Long> counts = events.stream()
                .collect(Collectors.groupingBy(ev -> ev.getEventType().name(), Collectors.counting()));
        return new StatsResponse(counts);
    }
}
