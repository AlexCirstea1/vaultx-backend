package com.vaultx.user.context.service;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import com.vaultx.user.context.model.activity.ActivityType;
import com.vaultx.user.context.model.user.User;
import com.vaultx.user.context.model.user.UserReport;
import com.vaultx.user.context.repository.UserReportRepository;
import com.vaultx.user.context.repository.UserRepository;
import com.vaultx.user.context.service.authentication.UserService;

@Service
public class ReportService {

    private final UserRepository userRepository;
    private final UserReportRepository reportRepository;
    private final UserService userService;
    private final ActivityService activityService;

    public ReportService(
            UserRepository userRepository,
            UserReportRepository reportRepository,
            UserService userService,
            ActivityService activityService) {
        this.userRepository = userRepository;
        this.reportRepository = reportRepository;
        this.userService = userService;
        this.activityService = activityService;
    }

    public ResponseEntity<String> reportUser(HttpServletRequest request, String id, String reason) {
        UUID reporterId = userService.getSessionUser(request).getId();
        UUID reportedId = UUID.fromString(id);
        // Define time window: 1 month ago
        Instant oneMonthAgo = Instant.now().minusSeconds(30L * 24 * 60 * 60);

        List<UserReport> recentReports = reportRepository.findRecentReports(reporterId, reportedId, oneMonthAgo);
        if (!recentReports.isEmpty()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("You have already reported this user in the last month.");
        }

        // Fetch users
        User reporter = userRepository
                .findById(reporterId)
                .orElseThrow(() -> new UsernameNotFoundException("Reporter not found"));
        User reported = userRepository
                .findById(reportedId)
                .orElseThrow(() -> new UsernameNotFoundException("Reported user not found"));

        // Create a new report record
        UserReport report = UserReport.builder()
                .reporter(reporter)
                .reported(reported)
                .createdAt(Instant.now())
                .reason(reason)
                .build();
        reportRepository.save(report);

        // Increment strike count on the reported user
        reported.setStrikeCount(reported.getStrikeCount() + 1);

        // Optionally, if the strike count exceeds a threshold, block or suspend the user.
        if (reported.getStrikeCount() >= 3) {
            // Example: disable account or mark as banned.
            reported.setEnabled(false);
        }

        userRepository.save(reported);

        activityService.logActivity(
                reporter,
                ActivityType.USER_ACTION,
                "Reported a user",
                false,
                "Reported user ID: " + reportedId + ", Reason: " + reason);

        return ResponseEntity.ok("User reported successfully. Strike count: " + reported.getStrikeCount());
    }
}
