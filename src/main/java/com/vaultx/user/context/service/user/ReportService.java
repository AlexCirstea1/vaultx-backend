package com.vaultx.user.context.service.user;

import com.vaultx.user.context.model.activity.ActivityType;
import com.vaultx.user.context.model.user.*;
import com.vaultx.user.context.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final int REPORT_WINDOW_DAYS = 30;
    private static final int MAX_STRIKES = 3;

    private final UserRepository userRepository;
    private final UserReportRepository reportRepository;
    private final UserService userService;
    private final ActivityService activityService;

    @Transactional
    public ResponseEntity<String> reportUser(HttpServletRequest req, String id, String reason) {

        UUID reporterId = userService.getSessionUser(req).getId();
        UUID reportedId = UUID.fromString(id);

        if (alreadyReported(reporterId, reportedId)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("You have already reported this user in the last month.");
        }

        User reporter = findUser(reporterId);
        User reported = findUser(reportedId);

        // persist report
        reportRepository.save(UserReport.builder()
                .reporter(reporter)
                .reported(reported)
                .createdAt(Instant.now())
                .reason(reason)
                .build());

        updateStrikes(reported);

        activityService.logActivity(
                reporter,
                ActivityType.USER_ACTION,
                "Reported a user",
                false,
                "Reported user ID: %s, Reason: %s".formatted(reportedId, reason));

        return ResponseEntity.ok("User reported successfully. Strike count: " + reported.getStrikeCount());
    }

    /*----------  helpers  ----------*/
    private boolean alreadyReported(UUID reporter, UUID reported) {
        Instant cutoff = Instant.now().minus(REPORT_WINDOW_DAYS, ChronoUnit.DAYS);
        List<UserReport> recent = reportRepository.findRecentReports(reporter, reported, cutoff);
        return !recent.isEmpty();
    }

    private void updateStrikes(User reported) {
        reported.setStrikeCount(reported.getStrikeCount() + 1);
        if (reported.getStrikeCount() >= MAX_STRIKES) {
            reported.setEnabled(false); // simple ban logic
        }
        userRepository.save(reported);
    }

    private User findUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
