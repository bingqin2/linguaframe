package com.linguaframe.operator.repository;

import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.ModelCallStatus;
import com.linguaframe.operator.domain.vo.OperatorCacheSummaryVo;
import com.linguaframe.operator.domain.vo.OperatorDashboardVo;
import com.linguaframe.operator.domain.vo.OperatorJobStatusCountVo;
import com.linguaframe.operator.domain.vo.OperatorModelCallSummaryVo;
import com.linguaframe.operator.domain.vo.OperatorRecentFailureVo;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Repository
public class OperatorDashboardRepository {

    private final JdbcClient jdbcClient;

    public OperatorDashboardRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public OperatorDashboardVo fetchDashboard() {
        return new OperatorDashboardVo(
                statusCounts(),
                recentFailures(),
                modelCallSummary(),
                cacheSummary()
        );
    }

    private List<OperatorJobStatusCountVo> statusCounts() {
        Map<LocalizationJobStatus, Long> counts = new EnumMap<>(LocalizationJobStatus.class);
        Arrays.stream(LocalizationJobStatus.values()).forEach(status -> counts.put(status, 0L));
        jdbcClient.sql("""
                        SELECT status, COUNT(*) AS status_count
                        FROM localization_jobs
                        GROUP BY status
                        """)
                .query((rs, rowNum) -> counts.put(
                        LocalizationJobStatus.valueOf(rs.getString("status")),
                        rs.getLong("status_count")
                ))
                .list();
        return Arrays.stream(LocalizationJobStatus.values())
                .map(status -> new OperatorJobStatusCountVo(status, counts.get(status)))
                .toList();
    }

    private List<OperatorRecentFailureVo> recentFailures() {
        return jdbcClient.sql("""
                        SELECT
                            jobs.id AS job_id,
                            jobs.video_id,
                            videos.original_filename,
                            jobs.failure_stage,
                            jobs.failure_reason,
                            jobs.failed_at
                        FROM localization_jobs jobs
                        JOIN videos ON videos.id = jobs.video_id
                        WHERE jobs.status = :failedStatus
                          AND jobs.failed_at IS NOT NULL
                        ORDER BY jobs.failed_at DESC, jobs.id DESC
                        LIMIT 5
                        """)
                .param("failedStatus", LocalizationJobStatus.FAILED.name())
                .query(this::mapFailure)
                .list();
    }

    private OperatorModelCallSummaryVo modelCallSummary() {
        return jdbcClient.sql("""
                        SELECT
                            COUNT(*) AS model_call_count,
                            COALESCE(SUM(CASE WHEN status = :failedStatus THEN 1 ELSE 0 END), 0) AS failed_model_call_count,
                            COALESCE(SUM(latency_ms), 0) AS total_latency_ms,
                            COALESCE(SUM(estimated_cost_usd), 0) AS estimated_cost_usd
                        FROM model_call_records
                        """)
                .param("failedStatus", ModelCallStatus.FAILED.name())
                .query((rs, rowNum) -> new OperatorModelCallSummaryVo(
                        rs.getLong("model_call_count"),
                        rs.getLong("failed_model_call_count"),
                        rs.getLong("total_latency_ms"),
                        decimalOrZero(rs, "estimated_cost_usd")
                ))
                .single();
    }

    private OperatorCacheSummaryVo cacheSummary() {
        Long artifactCacheHits = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM job_artifacts
                        WHERE cache_hit = TRUE
                        """)
                .query(Long.class)
                .single();
        Long generatedArtifacts = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM job_artifacts
                        WHERE cache_hit = FALSE
                        """)
                .query(Long.class)
                .single();
        Long providerCacheHits = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM job_timeline_events
                        WHERE status = :cacheHitStatus
                        """)
                .param("cacheHitStatus", JobTimelineEventStatus.CACHE_HIT.name())
                .query(Long.class)
                .single();
        return new OperatorCacheSummaryVo(
                artifactCacheHits == null ? 0L : artifactCacheHits,
                generatedArtifacts == null ? 0L : generatedArtifacts,
                providerCacheHits == null ? 0L : providerCacheHits
        );
    }

    private OperatorRecentFailureVo mapFailure(ResultSet rs, int rowNum) throws SQLException {
        String failureStage = rs.getString("failure_stage");
        return new OperatorRecentFailureVo(
                rs.getString("job_id"),
                rs.getString("video_id"),
                rs.getString("original_filename"),
                failureStage == null ? null : LocalizationJobStage.valueOf(failureStage),
                rs.getString("failure_reason"),
                rs.getTimestamp("failed_at").toInstant()
        );
    }

    private BigDecimal decimalOrZero(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }
}
