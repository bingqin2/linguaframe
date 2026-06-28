package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.vo.JobDiagnosticsArtifactVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.JobPipelineProgressVo;
import com.linguaframe.job.domain.vo.JobTimelineEventVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.domain.vo.FailureTriageVo;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.vo.SubtitleDraftSummaryVo;
import com.linguaframe.job.domain.vo.SubtitleReviewSummaryVo;
import com.linguaframe.job.service.JobEvidenceReportService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.job.service.SubtitleDraftService;
import com.linguaframe.job.service.SubtitleReviewService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class JobEvidenceReportServiceImpl implements JobEvidenceReportService {

    private final LocalizationJobQueryService queryService;
    private final SubtitleReviewService subtitleReviewService;
    private final SubtitleDraftService subtitleDraftService;

    public JobEvidenceReportServiceImpl(
            LocalizationJobQueryService queryService,
            SubtitleReviewService subtitleReviewService,
            SubtitleDraftService subtitleDraftService
    ) {
        this.queryService = queryService;
        this.subtitleReviewService = subtitleReviewService;
        this.subtitleDraftService = subtitleDraftService;
    }

    @Override
    public String buildMarkdownReport(String jobId) {
        JobDiagnosticsReportVo report = queryService.getDiagnosticsReport(jobId);
        LocalizationJobVo job = report.job();
        JobUsageSummaryVo usage = job.usageSummary();
        List<String> lines = new ArrayList<>();

        lines.add("# LinguaFrame Demo Evidence");
        lines.add("");
        lines.add("- Job: " + job.jobId());
        lines.add("- Video: " + job.videoId());
        lines.add("- Target language: " + job.targetLanguage());
        lines.add("- Subtitle style: " + job.subtitleStylePreset());
        lines.add("- Status: " + job.status());
        lines.add("- Retries: " + job.retryCount());
        lines.add("- Model calls: " + usage.modelCallCount());
        lines.add("- Failed model calls: " + usage.failedModelCallCount());
        lines.add("- Estimated cost: " + formatCost(usage.estimatedCostUsd()));
        lines.add("- Cache hits: " + job.cacheSummary().cacheHitCount()
                + " artifacts / " + job.cacheSummary().providerCacheHitCount() + " provider");
        lines.add("- Artifacts: " + report.artifactCount());
        lines.add("- Result bundle: /api/jobs/" + job.jobId() + "/artifacts/archive/download");
        lines.add("- Diagnostics: /api/jobs/" + job.jobId() + "/diagnostics/download");

        if (job.failureStage() != null || hasText(job.failureReason())) {
            lines.add("- Failure: " + valueOrDefault(job.failureStage(), "Unknown")
                    + " / " + valueOrDefault(job.failureReason(), "No reason"));
        }
        FailureTriageVo triage = job.failureTriage();
        if (triage != null) {
            lines.add("- Failure triage: " + triage.category()
                    + ", retryable=" + triage.retryable()
                    + ", " + triage.summary()
                    + " Action: " + triage.recommendedAction());
            if (hasText(triage.runbookCommand())) {
                lines.add("- Failure runbook: " + triage.runbookCommand());
            }
        }
        JobPipelineProgressVo progress = job.pipelineProgress();
        if (progress != null) {
            lines.add("- Pipeline current stage: " + valueOrDefault(progress.currentStage(), "Queued"));
            lines.add("- Pipeline completed: " + progress.completedStageCount()
                    + " / " + progress.totalStageCount());
            lines.add("- Pipeline states: " + progress.failedStageCount()
                    + " failed / " + progress.skippedStageCount()
                    + " skipped / " + progress.cacheHitStageCount() + " cache hits");
            lines.add("- Pipeline measured time: " + progress.totalMeasuredDurationMs() + " ms");
            lines.add("- Pipeline slowest stage: " + valueOrDefault(progress.slowestStage(), "Not measured")
                    + " / " + valueOrDefault(progress.slowestStageDurationMs(), "0") + " ms");
        }

        QualityEvaluationVo quality = job.qualityEvaluation();
        if (quality != null) {
            lines.add("- Quality: " + quality.score() + " / 100, "
                    + quality.verdict() + ", " + quality.status());
        }
        SubtitleReviewSummaryVo subtitleReview = subtitleReviewService.buildReview(
                job.jobId(),
                job.targetLanguage()
        );
        lines.add("- Subtitle review segments: " + subtitleReview.segmentCount());
        lines.add("- Subtitle review missing targets: " + subtitleReview.missingTargetCount());
        lines.add("- Subtitle review timing mismatches: " + subtitleReview.timingMismatchCount());
        lines.add("- Subtitle review quality: " + formatSubtitleReviewQuality(subtitleReview));
        lines.add("- Subtitle review downloadable subtitle artifacts: "
                + subtitleReview.downloadableSubtitleArtifactCount());
        addSubtitleDraftEvidence(lines, job);
        addReviewedSubtitleEvidence(lines, report);

        if (!job.timelineEvents().isEmpty()) {
            lines.add("");
            lines.add("Timeline:");
            for (JobTimelineEventVo event : job.timelineEvents()) {
                lines.add("- " + event.stage() + ": " + event.status());
            }
        }

        if (!report.artifacts().isEmpty()) {
            lines.add("");
            lines.add("Artifacts:");
            for (JobDiagnosticsArtifactVo artifact : report.artifacts()) {
                lines.add("- " + artifact.type()
                        + ": " + artifact.filename()
                        + ", " + formatBytes(artifact.sizeBytes())
                        + ", " + shortHash(artifact.contentSha256())
                        + ", " + (artifact.cacheHit() ? "Reused" : "Generated"));
            }
        }

        return String.join("\n", lines);
    }

    private String formatCost(BigDecimal value) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : value;
        return "$" + normalized.setScale(8, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String formatBytes(long sizeBytes) {
        if (sizeBytes < 1024) {
            return sizeBytes + " B";
        }
        double kib = sizeBytes / 1024.0;
        if (kib < 1024) {
            return String.format(java.util.Locale.ROOT, "%.1f KiB", kib);
        }
        return String.format(java.util.Locale.ROOT, "%.1f MiB", kib / 1024.0);
    }

    private String shortHash(String value) {
        if (!hasText(value)) {
            return "sha256 unavailable";
        }
        return value.length() <= 16 ? value : value.substring(0, 16);
    }

    private String formatSubtitleReviewQuality(SubtitleReviewSummaryVo subtitleReview) {
        if (subtitleReview.qualityScore() == null) {
            return "Not evaluated";
        }
        return subtitleReview.qualityScore() + " / 100, "
                + valueOrDefault(subtitleReview.qualityVerdict(), "No verdict");
    }

    private void addSubtitleDraftEvidence(List<String> lines, LocalizationJobVo job) {
        try {
            SubtitleDraftSummaryVo subtitleDraft = subtitleDraftService.getDraft(
                    job.jobId(),
                    job.targetLanguage()
            );
            lines.add("- Subtitle draft segments: " + subtitleDraft.segmentCount());
            lines.add("- Subtitle draft edited segments: " + subtitleDraft.editedSegmentCount());
            lines.add("- Subtitle draft last updated: "
                    + valueOrDefault(subtitleDraft.lastUpdatedAt(), "Not saved"));
        } catch (NoSuchElementException ex) {
            lines.add("- Subtitle draft segments: 0");
            lines.add("- Subtitle draft edited segments: 0");
            lines.add("- Subtitle draft last updated: Not saved");
        }
    }

    private void addReviewedSubtitleEvidence(List<String> lines, JobDiagnosticsReportVo report) {
        long reviewedSubtitleArtifacts = report.artifacts().stream()
                .filter(artifact -> artifact.type() == JobArtifactType.REVIEWED_SUBTITLE_JSON
                        || artifact.type() == JobArtifactType.REVIEWED_SUBTITLE_SRT
                        || artifact.type() == JobArtifactType.REVIEWED_SUBTITLE_VTT)
                .count();
        boolean reviewedBurnedVideoAvailable = report.artifacts().stream()
                .anyMatch(artifact -> artifact.type() == JobArtifactType.REVIEWED_BURNED_VIDEO);
        lines.add("- Reviewed subtitle artifacts: " + reviewedSubtitleArtifacts);
        lines.add("- Reviewed burned video: " + (reviewedBurnedVideoAvailable ? "Available" : "Not available"));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String valueOrDefault(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString();
        return text.isBlank() ? fallback : text;
    }
}
