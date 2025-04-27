package com.vaultx.user.context.repository;

import com.vaultx.user.context.model.user.UserReport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserReportRepository extends JpaRepository<UserReport, UUID> {

    @Query(
            "SELECT ur FROM UserReport ur WHERE ur.reporter.id = :reporterId AND ur.reported.id = :reportedId AND ur.createdAt > :since")
    List<UserReport> findRecentReports(
            @Param("reporterId") UUID reporterId, @Param("reportedId") UUID reportedId, @Param("since") Instant since);
}
