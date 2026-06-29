package com.linguaframe.operator.service.impl;

import com.linguaframe.common.security.DemoOwnerIdentityService;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.ModelCallStatus;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerCallVo;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerJobVo;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerOperationVo;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerSummaryVo;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerVo;
import com.linguaframe.operator.service.ModelUsageLedgerService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ModelUsageLedgerServiceImpl implements ModelUsageLedgerService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final BigDecimal FAILURE_ATTENTION_THRESHOLD = new BigDecimal("10.00");
    private static final BigDecimal FAILURE_BLOCKED_THRESHOLD = new BigDecimal("25.00");

    private final JdbcClient jdbcClient;
    private final DemoOwnerIdentityService ownerIdentityService;

    public ModelUsageLedgerServiceImpl(JdbcClient jdbcClient, DemoOwnerIdentityService ownerIdentityService) {
        this.jdbcClient = jdbcClient;
        this.ownerIdentityService = ownerIdentityService;
    }

    @Override
    public ModelUsageLedgerVo ledger(Integer limit) {
        int normalizedLimit = normalizeLimit(limit);
        String ownerId = ownerIdentityService.currentOwnerId();
        String ownershipScope = ownerIdentityService.ownershipScope();
        List<ModelUsageLedgerCallVo> calls = recentCalls(ownerId, normalizedLimit);
        List<ModelUsageLedgerJobVo> jobs = jobs(ownerId, calls);
        List<ModelUsageLedgerOperationVo> operations = operations(calls);
        ModelUsageLedgerSummaryVo summary = summary(calls, jobs);
        return new ModelUsageLedgerVo(
                Instant.now(),
                normalizedLimit,
                ownerId,
                ownershipScope,
                summary,
                jobs,
                operations,
                calls,
                List.of(
                        "/api/operator/model-usage-ledger",
                        "/api/operator/model-usage-ledger/markdown/download"
                ),
                List.of(
                        "Ledger is scoped to the current demo owner.",
                        "Only safe model-call summaries, aggregate costs, and stable API links are exposed.",
                        "Raw media object keys, prompts, provider responses, and secrets are intentionally excluded."
                )
        );
    }

    @Override
    public String ledgerMarkdown(Integer limit) {
        ModelUsageLedgerVo ledger = ledger(limit);
        StringBuilder markdown = new StringBuilder();
        markdown.append("# LinguaFrame Model Usage Ledger\n\n");
        markdown.append("- Status: ").append(ledger.summary().ledgerStatus()).append('\n');
        markdown.append("- Owner scope: ").append(ledger.ownershipScope()).append('\n');
        markdown.append("- Recent calls: ").append(ledger.summary().modelCallCount()).append('\n');
        markdown.append("- Failed calls: ").append(ledger.summary().failedModelCallCount()).append('\n');
        markdown.append("- Estimated cost USD: ").append(ledger.summary().estimatedCostUsd()).append('\n');
        markdown.append("- Failure rate: ").append(ledger.summary().failureRatePercent()).append("%\n");
        markdown.append("- Next action: ").append(ledger.summary().recommendedNextAction()).append("\n\n");
        markdown.append("## Jobs\n\n");
        if (ledger.jobs().isEmpty()) {
            markdown.append("- No model-call evidence is available yet.\n");
        } else {
            for (ModelUsageLedgerJobVo job : ledger.jobs()) {
                markdown.append("- `").append(job.jobId()).append("` ")
                        .append(job.jobStatus()).append(" calls=").append(job.modelCallCount())
                        .append(" failed=").append(job.failedModelCallCount())
                        .append(" cost=").append(job.estimatedCostUsd())
                        .append(" links=").append(job.safeLinks()).append('\n');
            }
        }
        markdown.append("\n## Operations\n\n");
        if (ledger.operations().isEmpty()) {
            markdown.append("- No operation usage has been recorded.\n");
        } else {
            for (ModelUsageLedgerOperationVo operation : ledger.operations()) {
                markdown.append("- ").append(operation.operation())
                        .append(" via ").append(operation.provider()).append('/')
                        .append(operation.model())
                        .append(" calls=").append(operation.modelCallCount())
                        .append(" failed=").append(operation.failedModelCallCount())
                        .append(" avgLatencyMs=").append(operation.averageLatencyMs())
                        .append(" cost=").append(operation.estimatedCostUsd()).append('\n');
            }
        }
        markdown.append("\n## Safety Notes\n\n");
        ledger.safetyNotes().forEach(note -> markdown.append("- ").append(note).append('\n'));
        return markdown.toString();
    }

    private List<ModelUsageLedgerCallVo> recentCalls(String ownerId, int limit) {
        return jdbcClient.sql("""
                        SELECT
                            calls.id AS model_call_id,
                            calls.job_id,
                            jobs.video_id,
                            calls.stage,
                            calls.operation,
                            calls.provider,
                            calls.model,
                            calls.prompt_version,
                            calls.status AS call_status,
                            calls.latency_ms,
                            calls.input_tokens,
                            calls.output_tokens,
                            calls.audio_seconds,
                            calls.character_count,
                            calls.estimated_cost_usd,
                            calls.safe_error_summary,
                            calls.created_at
                        FROM model_call_records calls
                        JOIN localization_jobs jobs ON jobs.id = calls.job_id
                        WHERE jobs.owner_id = :ownerId
                        ORDER BY calls.created_at DESC, calls.id DESC
                        LIMIT :limit
                        """)
                .param("ownerId", ownerId)
                .param("limit", limit)
                .query(this::mapCall)
                .list();
    }

    private List<ModelUsageLedgerJobVo> jobs(String ownerId, List<ModelUsageLedgerCallVo> calls) {
        Map<String, MutableJobLedger> jobs = new LinkedHashMap<>();
        calls.forEach(call -> jobs.computeIfAbsent(call.jobId(), ignored -> jobSeed(ownerId, call)));
        calls.forEach(call -> jobs.get(call.jobId()).add(call));
        return jobs.values().stream()
                .map(MutableJobLedger::toVo)
                .toList();
    }

    private MutableJobLedger jobSeed(String ownerId, ModelUsageLedgerCallVo call) {
        return jdbcClient.sql("""
                        SELECT
                            jobs.id,
                            jobs.video_id,
                            jobs.status,
                            jobs.target_language,
                            jobs.demo_profile_id,
                            COALESCE(SUM(CASE WHEN events.status = :cacheHitStatus THEN 1 ELSE 0 END), 0) AS provider_cache_hit_count,
                            COALESCE(SUM(CASE WHEN artifacts.cache_hit = FALSE THEN 1 ELSE 0 END), 0) AS generated_artifact_count
                        FROM localization_jobs jobs
                        LEFT JOIN job_timeline_events events ON events.job_id = jobs.id
                        LEFT JOIN job_artifacts artifacts ON artifacts.job_id = jobs.id
                        WHERE jobs.id = :jobId
                          AND jobs.owner_id = :ownerId
                        GROUP BY jobs.id, jobs.video_id, jobs.status, jobs.target_language, jobs.demo_profile_id
                        """)
                .param("cacheHitStatus", JobTimelineEventStatus.CACHE_HIT.name())
                .param("jobId", call.jobId())
                .param("ownerId", ownerId)
                .query((rs, rowNum) -> new MutableJobLedger(
                        rs.getString("id"),
                        rs.getString("video_id"),
                        rs.getString("status"),
                        rs.getString("target_language"),
                        rs.getString("demo_profile_id"),
                        rs.getInt("provider_cache_hit_count"),
                        rs.getInt("generated_artifact_count")
                ))
                .single();
    }

    private List<ModelUsageLedgerOperationVo> operations(List<ModelUsageLedgerCallVo> calls) {
        Map<OperationKey, MutableOperationLedger> operations = new LinkedHashMap<>();
        for (ModelUsageLedgerCallVo call : calls) {
            OperationKey key = new OperationKey(call.operation(), call.provider(), call.model(), call.promptVersion());
            operations.computeIfAbsent(key, MutableOperationLedger::new).add(call);
        }
        return operations.values().stream()
                .map(MutableOperationLedger::toVo)
                .toList();
    }

    private ModelUsageLedgerSummaryVo summary(
            List<ModelUsageLedgerCallVo> calls,
            List<ModelUsageLedgerJobVo> jobs
    ) {
        int callCount = calls.size();
        int failedCallCount = (int) calls.stream()
                .filter(call -> ModelCallStatus.FAILED.name().equals(call.status()))
                .count();
        long totalLatencyMs = calls.stream().mapToLong(ModelUsageLedgerCallVo::latencyMs).sum();
        BigDecimal estimatedCost = calls.stream()
                .map(ModelUsageLedgerCallVo::estimatedCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(8, RoundingMode.HALF_UP);
        long averageLatencyMs = callCount == 0 ? 0L : totalLatencyMs / callCount;
        BigDecimal failureRate = callCount == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(failedCallCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(callCount), 2, RoundingMode.HALF_UP);
        int providerCacheHits = jobs.stream().mapToInt(ModelUsageLedgerJobVo::providerCacheHitCount).sum();
        int generatedArtifacts = jobs.stream().mapToInt(ModelUsageLedgerJobVo::generatedArtifactCount).sum();
        String status = ledgerStatus(callCount, failureRate);
        return new ModelUsageLedgerSummaryVo(
                status,
                jobs.size(),
                callCount,
                failedCallCount,
                providerCacheHits,
                generatedArtifacts,
                totalLatencyMs,
                estimatedCost,
                averageLatencyMs,
                failureRate,
                recommendedNextAction(status)
        );
    }

    private String ledgerStatus(int callCount, BigDecimal failureRate) {
        if (callCount == 0) {
            return "EMPTY";
        }
        if (failureRate.compareTo(FAILURE_BLOCKED_THRESHOLD) >= 0) {
            return "BLOCKED";
        }
        if (failureRate.compareTo(FAILURE_ATTENTION_THRESHOLD) >= 0) {
            return "ATTENTION";
        }
        return "READY";
    }

    private String recommendedNextAction(String status) {
        return switch (status) {
            case "EMPTY" -> "Run a demo job, then refresh this ledger before presenting model spend.";
            case "BLOCKED" -> "Inspect failed model calls and rerun the OpenAI preflight before another full demo.";
            case "ATTENTION" -> "Review safe error summaries and retry only after provider health is stable.";
            default -> "Use the ledger links as cost and latency evidence for the current demo run.";
        };
    }

    private ModelUsageLedgerCallVo mapCall(ResultSet rs, int rowNum) throws SQLException {
        return new ModelUsageLedgerCallVo(
                rs.getString("model_call_id"),
                rs.getString("job_id"),
                rs.getString("video_id"),
                rs.getString("stage"),
                rs.getString("operation"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getString("prompt_version"),
                rs.getString("call_status"),
                rs.getLong("latency_ms"),
                integerOrNull(rs, "input_tokens"),
                integerOrNull(rs, "output_tokens"),
                rs.getBigDecimal("audio_seconds"),
                integerOrNull(rs, "character_count"),
                rs.getBigDecimal("estimated_cost_usd").setScale(8, RoundingMode.HALF_UP),
                rs.getString("safe_error_summary"),
                timestampOrNow(rs, "created_at")
        );
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private Integer integerOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Instant timestampOrNow(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }

    private List<String> safeJobLinks(String jobId) {
        return List.of(
                "/api/jobs/" + jobId,
                "/api/jobs/" + jobId + "/diagnostics/download",
                "/api/jobs/" + jobId + "/demo-run-package/download",
                "/api/jobs/" + jobId + "/ai-audit-package/download"
        );
    }

    private final class MutableJobLedger {
        private final String jobId;
        private final String videoId;
        private final String jobStatus;
        private final String targetLanguage;
        private final String demoProfileId;
        private final int providerCacheHitCount;
        private final int generatedArtifactCount;
        private int modelCallCount;
        private int failedModelCallCount;
        private long totalLatencyMs;
        private BigDecimal estimatedCostUsd = BigDecimal.ZERO;
        private Instant latestModelCallAt;

        private MutableJobLedger(
                String jobId,
                String videoId,
                String jobStatus,
                String targetLanguage,
                String demoProfileId,
                int providerCacheHitCount,
                int generatedArtifactCount
        ) {
            this.jobId = jobId;
            this.videoId = videoId;
            this.jobStatus = jobStatus;
            this.targetLanguage = targetLanguage;
            this.demoProfileId = demoProfileId;
            this.providerCacheHitCount = providerCacheHitCount;
            this.generatedArtifactCount = generatedArtifactCount;
        }

        private void add(ModelUsageLedgerCallVo call) {
            modelCallCount++;
            if (ModelCallStatus.FAILED.name().equals(call.status())) {
                failedModelCallCount++;
            }
            totalLatencyMs += call.latencyMs();
            estimatedCostUsd = estimatedCostUsd.add(call.estimatedCostUsd());
            if (latestModelCallAt == null || call.createdAt().isAfter(latestModelCallAt)) {
                latestModelCallAt = call.createdAt();
            }
        }

        private ModelUsageLedgerJobVo toVo() {
            return new ModelUsageLedgerJobVo(
                    jobId,
                    videoId,
                    jobStatus,
                    targetLanguage,
                    demoProfileId,
                    modelCallCount,
                    failedModelCallCount,
                    providerCacheHitCount,
                    generatedArtifactCount,
                    totalLatencyMs,
                    estimatedCostUsd.setScale(8, RoundingMode.HALF_UP),
                    latestModelCallAt,
                    safeJobLinks(jobId)
            );
        }
    }

    private record OperationKey(String operation, String provider, String model, String promptVersion) {
    }

    private static final class MutableOperationLedger {
        private final OperationKey key;
        private int modelCallCount;
        private int failedModelCallCount;
        private long totalLatencyMs;
        private BigDecimal estimatedCostUsd = BigDecimal.ZERO;

        private MutableOperationLedger(OperationKey key) {
            this.key = key;
        }

        private void add(ModelUsageLedgerCallVo call) {
            modelCallCount++;
            if (ModelCallStatus.FAILED.name().equals(call.status())) {
                failedModelCallCount++;
            }
            totalLatencyMs += call.latencyMs();
            estimatedCostUsd = estimatedCostUsd.add(call.estimatedCostUsd());
        }

        private ModelUsageLedgerOperationVo toVo() {
            return new ModelUsageLedgerOperationVo(
                    key.operation(),
                    key.provider(),
                    key.model(),
                    key.promptVersion(),
                    modelCallCount,
                    failedModelCallCount,
                    totalLatencyMs,
                    estimatedCostUsd.setScale(8, RoundingMode.HALF_UP),
                    modelCallCount == 0 ? 0L : totalLatencyMs / modelCallCount
            );
        }
    }
}
