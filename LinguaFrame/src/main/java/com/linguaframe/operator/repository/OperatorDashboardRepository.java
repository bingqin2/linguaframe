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
import com.linguaframe.operator.domain.vo.OperatorStageTimingVo;
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
                cacheSummary(),
                stageTimings()
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

    private List<OperatorStageTimingVo> stageTimings() {
        return jdbcClient.sql("""
                        WITH latest_stage_events AS (
                            SELECT
                                stage,
                                duration_ms,
                                ROW_NUMBER() OVER (PARTITION BY stage ORDER BY occurred_at DESC, id DESC) AS row_num
                            FROM job_timeline_events
                            WHERE duration_ms IS NOT NULL
                              AND status IN (:succeededStatus, :failedStatus)
                        )
                        SELECT
                            events.stage,
                            COALESCE(SUM(CASE WHEN events.status = :succeededStatus THEN 1 ELSE 0 END), 0) AS completed_event_count,
                            COALESCE(SUM(CASE WHEN events.status = :failedStatus THEN 1 ELSE 0 END), 0) AS failed_event_count,
                            COALESCE(AVG(events.duration_ms), 0) AS average_duration_ms,
                            COALESCE(MAX(events.duration_ms), 0) AS max_duration_ms,
                            COALESCE(MAX(CASE WHEN latest.row_num = 1 THEN latest.duration_ms ELSE NULL END), 0) AS latest_duration_ms
                        FROM job_timeline_events events
                        LEFT JOIN latest_stage_events latest
                            ON latest.stage = events.stage
                           AND latest.row_num = 1
                        WHERE events.duration_ms IS NOT NULL
                          AND events.status IN (:succeededStatus, :failedStatus)
                        GROUP BY events.stage
                        ORDER BY max_duration_ms DESC, events.stage ASC
                        LIMIT 6
                        """)
                .param("succeededStatus", JobTimelineEventStatus.SUCCEEDED.name())
                .param("failedStatus", JobTimelineEventStatus.FAILED.name())
                .query((rs, rowNum) -> new OperatorStageTimingVo(
                        LocalizationJobStage.valueOf(rs.getString("stage")),
                        rs.getLong("completed_event_count"),
                        rs.getLong("failed_event_count"),
                        rs.getLong("average_duration_ms"),
                        rs.getLong("max_duration_ms"),
                        rs.getLong("latest_duration_ms")
                ))
                .list();
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
