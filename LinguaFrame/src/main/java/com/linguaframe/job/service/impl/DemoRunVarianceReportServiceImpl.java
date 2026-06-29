package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.DemoRunVarianceMetricVo;
import com.linguaframe.job.domain.vo.DemoRunVarianceReportVo;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.service.DemoRunVarianceReportService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class DemoRunVarianceReportServiceImpl implements DemoRunVarianceReportService {

    private final LocalizationJobQueryService localizationJobQueryService;
    private final ObjectMapper objectMapper;

    public DemoRunVarianceReportServiceImpl(
            LocalizationJobQueryService localizationJobQueryService,
            ObjectMapper objectMapper
    ) {
        this.localizationJobQueryService = localizationJobQueryService;
        this.objectMapper = objectMapper;
    }

    @Override
    public DemoRunVarianceReportVo build(String jobId, String preUploadJson) {
        LocalizationJobVo job = localizationJobQueryService.getJob(jobId);
        Baseline baseline = parseBaseline(preUploadJson);

        List<DemoRunVarianceMetricVo> metrics = new ArrayList<>();
        JobUsageSummaryVo usage = job.usageSummary();
        JobCacheSummaryVo cache = job.cacheSummary();

        BigDecimal actualCost = usage == null ? null : usage.estimatedCostUsd();
        metrics.add(compareDecimal(
                "estimatedCostUsd",
                "Estimated cost USD",
                baseline.estimatedCostUsd(),
                actualCost,
                "Compares pre-upload cost estimate with actual model-call cost."
        ));

        Integer actualModelCalls = usage == null ? null : usage.modelCallCount();
        metrics.add(compareInteger(
                "modelCallCount",
                "Model calls",
                baseline.estimatedModelCallCount(),
                actualModelCalls,
                "Compares estimated paid stages with actual model calls."
        ));

        Long actualRuntimeSeconds = runtimeSeconds(job);
        metrics.add(compareLong(
                "runtimeSeconds",
                "Runtime seconds",
                baseline.estimatedRuntimeSecondsUpper(),
                actualRuntimeSeconds,
                "Uses the estimated upper bound when a baseline is supplied."
        ));

        metrics.add(new DemoRunVarianceMetricVo(
                "jobStatus",
                "Job status",
                baseline.overallStatus().orElse("BASELINE_MISSING"),
                safeEnum(job.status()),
                statusMetric(job, baseline),
                "Compares expected readiness with the final job status."
        ));

        metrics.add(new DemoRunVarianceMetricVo(
                "sourceReuseDecision",
                "Source reuse decision",
                baseline.sourceReuseDecision().isPresent() ? "ACTUAL_ONLY" : "BASELINE_MISSING",
                baseline.sourceReuseDecision().orElse("BASELINE_MISSING"),
                "ACTUAL_ONLY",
                "Source reuse is decided before upload; final job detail does not expose raw source paths."
        ));

        metrics.add(new DemoRunVarianceMetricVo(
                "cacheHits",
                "Cache hits",
                "ACTUAL_ONLY",
                "ACTUAL_ONLY",
                cache == null ? "unavailable" : "cache=" + cache.cacheHitCount()
                        + ", provider=" + cache.providerCacheHitCount()
                        + ", generated=" + cache.generatedArtifactCount(),
                "Cache evidence is reported from safe job summary counters."
        ));

        QualityEvaluationVo quality = job.qualityEvaluation();
        metrics.add(new DemoRunVarianceMetricVo(
                "qualityScore",
                "Quality score",
                "ACTUAL_ONLY",
                "ACTUAL_ONLY",
                quality == null ? "not evaluated" : quality.score() + " (" + clean(quality.verdict()) + ")",
                "Quality evaluation is included only as safe summary metadata."
        ));

        metrics.add(new DemoRunVarianceMetricVo(
                "deliveryReadiness",
                "Delivery readiness",
                job.status() == LocalizationJobStatus.COMPLETED ? "MATCH" : "ATTENTION",
                "ACTUAL_ONLY",
                job.status() == LocalizationJobStatus.COMPLETED ? "ready" : "not ready",
                "Delivery artifacts should be inspected through safe package and acceptance links."
        ));

        List<String> notes = new ArrayList<>(baseline.notes());
        String overallStatus = overallStatus(job, baseline, metrics);
        String recommendedNextAction = recommendedNextAction(job, baseline, overallStatus);

        List<String> safeLinks = List.of(
                "/api/jobs/" + clean(job.jobId()) + "/demo-run-package/download",
                "/api/jobs/" + clean(job.jobId()) + "/demo-acceptance-gate",
                "/api/jobs/" + clean(job.jobId()) + "/demo-completion-certificate"
        );
        List<String> safetyNotes = List.of(
                "Report is read-only and does not call model providers, FFmpeg, object storage, or queue dispatch.",
                "Report excludes media bytes, object keys, local filesystem paths, raw transcripts, subtitles, provider payloads, tokens, and credentials.",
                "Use safe job evidence endpoints for delivery package, acceptance gate, and completion certificate details."
        );

        return new DemoRunVarianceReportVo(
                job.jobId(),
                job.videoId(),
                Instant.now(),
                overallStatus,
                baseline.mode(),
                safeEnum(job.status()),
                job.targetLanguage(),
                job.demoProfileId(),
                recommendedNextAction,
                List.copyOf(metrics),
                List.copyOf(notes),
                safeLinks,
                safetyNotes
        );
    }

    @Override
    public String renderMarkdown(DemoRunVarianceReportVo report) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Demo Run Variance Report\n\n");
        markdown.append("## Summary\n\n");
        markdown.append("- Job: `").append(clean(report.jobId())).append("`\n");
        markdown.append("- Video: `").append(clean(report.videoId())).append("`\n");
        markdown.append("- Overall status: `").append(clean(report.overallStatus())).append("`\n");
        markdown.append("- Recommended next action: ").append(clean(report.recommendedNextAction())).append("\n\n");

        markdown.append("## Baseline\n\n");
        markdown.append("- Baseline mode: `").append(clean(report.baselineMode())).append("`\n");
        markdown.append("- Target language: `").append(clean(report.targetLanguage())).append("`\n");
        if (StringUtils.hasText(report.demoProfileId())) {
            markdown.append("- Demo profile: `").append(clean(report.demoProfileId())).append("`\n");
        }
        for (String note : report.notes()) {
            markdown.append("- ").append(clean(note)).append("\n");
        }
        markdown.append("\n");

        markdown.append("## Actual Run\n\n");
        markdown.append("- Job status: `").append(clean(report.jobStatus())).append("`\n");
        markdown.append("- Generated at: `").append(report.generatedAt()).append("`\n\n");

        markdown.append("## Variance Metrics\n\n");
        markdown.append("| Metric | Status | Estimated | Actual | Detail |\n");
        markdown.append("| --- | --- | --- | --- | --- |\n");
        for (DemoRunVarianceMetricVo metric : report.metrics()) {
            markdown.append("| ")
                    .append(clean(metric.label()))
                    .append(" | `").append(clean(metric.status())).append("` | ")
                    .append(clean(metric.estimatedValue()))
                    .append(" | ")
                    .append(clean(metric.actualValue()))
                    .append(" | ")
                    .append(clean(metric.detail()))
                    .append(" |\n");
        }
        markdown.append("\n");

        markdown.append("## Delivery Evidence\n\n");
        for (String link : report.safeLinks()) {
            markdown.append("- `").append(clean(link)).append("`\n");
        }
        markdown.append("\n");

        markdown.append("## Safety Notes\n\n");
        for (String note : report.safetyNotes()) {
            markdown.append("- ").append(clean(note)).append("\n");
        }
        return markdown.toString();
    }

    private Baseline parseBaseline(String preUploadJson) {
        if (!StringUtils.hasText(preUploadJson)) {
            return Baseline.missing("No pre-upload baseline was supplied; report is actual-only.");
        }
        try {
            JsonNode root = objectMapper.readTree(preUploadJson);
            if (root == null || !root.isObject()) {
                return Baseline.invalid("Pre-upload baseline JSON must be an object; report is actual-only.");
            }
            JsonNode executionPlan = root.path("executionPlan");
            if (executionPlan.isObject()) {
                return baselineFrom("DECISION_PACKAGE", executionPlan, root);
            }
            if (hasAny(root, "estimatedCostUsd", "estimatedDurationSecondsLower", "estimatedDurationSecondsUpper", "stages")) {
                return baselineFrom("EXECUTION_PLAN", root, root);
            }
            return baselineFrom("MINIMAL_JSON", root, root)
                    .withNote("Pre-upload baseline used minimal safe JSON fields only.");
        } catch (Exception ignored) {
            return Baseline.invalid("Pre-upload baseline JSON could not be parsed; report is actual-only.");
        }
    }

    private Baseline baselineFrom(String mode, JsonNode plan, JsonNode root) {
        List<String> notes = new ArrayList<>();
        optionalText(plan, "recommendedNextAction")
                .ifPresent(value -> notes.add("Baseline next action: " + value));
        optionalText(root, "recommendedDecision")
                .ifPresent(value -> notes.add("Baseline decision: " + value));
        optionalText(plan, "filename")
                .ifPresent(value -> notes.add("Baseline filename captured without exposing a local path: " + value));

        return new Baseline(
                mode,
                optionalText(plan, "overallStatus"),
                optionalDecimal(plan, "estimatedCostUsd"),
                optionalLong(plan, "estimatedDurationSecondsUpper"),
                estimatedPaidStages(plan),
                sourceReuseDecision(plan),
                List.copyOf(notes)
        );
    }

    private Optional<String> sourceReuseDecision(JsonNode plan) {
        JsonNode sourceReuseDecision = plan.path("sourceReuseDecision");
        if (sourceReuseDecision.isObject()) {
            return optionalText(sourceReuseDecision, "status")
                    .or(() -> optionalText(sourceReuseDecision, "decision"));
        }
        return optionalText(plan, "sourceReuseDecision");
    }

    private Optional<Integer> estimatedPaidStages(JsonNode plan) {
        JsonNode stages = plan.path("stages");
        if (!stages.isArray()) {
            return Optional.empty();
        }
        int count = 0;
        for (JsonNode stage : stages) {
            String executionType = optionalText(stage, "executionType").orElse("");
            if ("PAID".equalsIgnoreCase(executionType)) {
                count++;
            }
        }
        return Optional.of(count);
    }

    private DemoRunVarianceMetricVo compareDecimal(
            String id,
            String label,
            Optional<BigDecimal> estimated,
            BigDecimal actual,
            String detail
    ) {
        return new DemoRunVarianceMetricVo(
                id,
                label,
                compareStatus(estimated, Optional.ofNullable(actual)),
                estimated.map(BigDecimal::toPlainString).orElse("BASELINE_MISSING"),
                actual == null ? "unavailable" : actual.toPlainString(),
                detail
        );
    }

    private DemoRunVarianceMetricVo compareInteger(
            String id,
            String label,
            Optional<Integer> estimated,
            Integer actual,
            String detail
    ) {
        return new DemoRunVarianceMetricVo(
                id,
                label,
                compareStatus(estimated.map(BigDecimal::valueOf), Optional.ofNullable(actual).map(BigDecimal::valueOf)),
                estimated.map(String::valueOf).orElse("BASELINE_MISSING"),
                actual == null ? "unavailable" : String.valueOf(actual),
                detail
        );
    }

    private DemoRunVarianceMetricVo compareLong(
            String id,
            String label,
            Optional<Long> estimated,
            Long actual,
            String detail
    ) {
        return new DemoRunVarianceMetricVo(
                id,
                label,
                compareStatus(estimated.map(BigDecimal::valueOf), Optional.ofNullable(actual).map(BigDecimal::valueOf)),
                estimated.map(String::valueOf).orElse("BASELINE_MISSING"),
                actual == null ? "unavailable" : String.valueOf(actual),
                detail
        );
    }

    private String compareStatus(Optional<BigDecimal> estimated, Optional<BigDecimal> actual) {
        if (estimated.isEmpty()) {
            return "BASELINE_MISSING";
        }
        if (actual.isEmpty()) {
            return "ACTUAL_ONLY";
        }
        int comparison = actual.get().compareTo(estimated.get());
        if (comparison == 0) {
            return "MATCH";
        }
        if (comparison < 0) {
            return "LOWER_THAN_ESTIMATE";
        }
        return "HIGHER_THAN_ESTIMATE";
    }

    private String statusMetric(LocalizationJobVo job, Baseline baseline) {
        if (baseline.mode().equals("MISSING") || baseline.mode().equals("INVALID")) {
            return baseline.mode().equals("INVALID") ? "ATTENTION" : "BASELINE_MISSING";
        }
        if (job.status() == LocalizationJobStatus.COMPLETED) {
            return "MATCH";
        }
        if (job.status() == LocalizationJobStatus.FAILED || job.status() == LocalizationJobStatus.CANCELLED) {
            return "ATTENTION";
        }
        return "ACTUAL_ONLY";
    }

    private String overallStatus(LocalizationJobVo job, Baseline baseline, List<DemoRunVarianceMetricVo> metrics) {
        if (job.status() == LocalizationJobStatus.FAILED || job.status() == LocalizationJobStatus.CANCELLED) {
            return "BLOCKED";
        }
        if (baseline.mode().equals("INVALID")) {
            return "ATTENTION";
        }
        boolean higherThanEstimate = metrics.stream()
                .anyMatch(metric -> "HIGHER_THAN_ESTIMATE".equals(metric.status()));
        if (higherThanEstimate || job.status() != LocalizationJobStatus.COMPLETED) {
            return "ATTENTION";
        }
        return "READY";
    }

    private String recommendedNextAction(LocalizationJobVo job, Baseline baseline, String overallStatus) {
        if ("BLOCKED".equals(overallStatus)) {
            return "Review failure triage and rerun only after resolving the failed stage.";
        }
        if ("ATTENTION".equals(overallStatus)) {
            return "Review variance metrics before using this job as demo evidence.";
        }
        if (baseline.mode().equals("MISSING")) {
            return "Use actual-only report as evidence, or rerun with saved pre-upload baseline for variance comparison.";
        }
        return job.status() == LocalizationJobStatus.COMPLETED
                ? "Use delivery package, acceptance gate, and completion certificate as final demo evidence."
                : "Wait for the job to complete before treating this report as final evidence.";
    }

    private Long runtimeSeconds(LocalizationJobVo job) {
        if (job.startedAt() == null || job.completedAt() == null) {
            return null;
        }
        return Duration.between(job.startedAt(), job.completedAt()).getSeconds();
    }

    private Optional<BigDecimal> optionalDecimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return Optional.empty();
        }
        try {
            if (value.isNumber()) {
                return Optional.of(value.decimalValue());
            }
            if (StringUtils.hasText(value.asText())) {
                return Optional.of(new BigDecimal(value.asText()));
            }
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<Long> optionalLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return Optional.empty();
        }
        if (value.canConvertToLong()) {
            return Optional.of(value.asLong());
        }
        try {
            if (StringUtils.hasText(value.asText())) {
                return Optional.of(Long.parseLong(value.asText()));
            }
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<String> optionalText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || !StringUtils.hasText(value.asText())) {
            return Optional.empty();
        }
        return Optional.of(clean(value.asText()));
    }

    private boolean hasAny(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field)) {
                return true;
            }
        }
        return false;
    }

    private String safeEnum(Enum<?> value) {
        return value == null ? "unavailable" : value.name();
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\r', ' ').replace('\n', ' ').replace('|', '/').trim();
    }

    private record Baseline(
            String mode,
            Optional<String> overallStatus,
            Optional<BigDecimal> estimatedCostUsd,
            Optional<Long> estimatedRuntimeSecondsUpper,
            Optional<Integer> estimatedModelCallCount,
            Optional<String> sourceReuseDecision,
            List<String> notes
    ) {
        private static Baseline missing(String note) {
            return new Baseline(
                    "MISSING",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    List.of(note)
            );
        }

        private static Baseline invalid(String note) {
            return new Baseline(
                    "INVALID",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    List.of(note)
            );
        }

        private Baseline withNote(String note) {
            List<String> merged = new ArrayList<>(notes);
            merged.add(note);
            return new Baseline(
                    mode,
                    overallStatus,
                    estimatedCostUsd,
                    estimatedRuntimeSecondsUpper,
                    estimatedModelCallCount,
                    sourceReuseDecision,
                    List.copyOf(merged)
            );
        }
    }
}
