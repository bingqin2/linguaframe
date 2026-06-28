package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.DemoRunMonitorLinkVo;
import com.linguaframe.job.domain.vo.DemoRunMonitorStageVo;
import com.linguaframe.job.domain.vo.DemoRunMonitorVo;
import com.linguaframe.job.domain.vo.JobPipelineProgressVo;
import com.linguaframe.job.domain.vo.JobStageProgressVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.service.DemoRunMonitorService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class DemoRunMonitorServiceImpl implements DemoRunMonitorService {

    private static final long STALE_STAGE_THRESHOLD_MS = Duration.ofMinutes(15).toMillis();

    private final LocalizationJobQueryService queryService;
    private final Clock clock;

    public DemoRunMonitorServiceImpl(LocalizationJobQueryService queryService, Clock clock) {
        this.queryService = queryService;
        this.clock = clock;
    }

    @Override
    public DemoRunMonitorVo buildMonitor(String jobId) {
        LocalizationJobVo job = queryService.getJob(jobId);
        Instant generatedAt = Instant.now(clock);
        JobPipelineProgressVo progress = job.pipelineProgress();
        List<DemoRunMonitorStageVo> stages = stageRows(progress, generatedAt);
        String attentionLevel = attentionLevel(job, stages);
        String summary = summary(job, progress, attentionLevel);
        String nextAction = recommendedNextAction(job, attentionLevel);
        List<DemoRunMonitorLinkVo> links = links(job.jobId());
        Long elapsedMs = elapsedMs(job, generatedAt);
        String markdown = renderMarkdown(job, generatedAt, elapsedMs, progress, attentionLevel, summary, nextAction, stages, links);

        return new DemoRunMonitorVo(
                job.jobId(),
                job.videoId(),
                job.status(),
                job.dispatchStatus(),
                generatedAt,
                elapsedMs,
                progress == null ? null : progress.currentStage(),
                progress == null ? 0 : progress.completedStageCount(),
                progress == null ? 0 : progress.totalStageCount(),
                progress == null ? 0 : progress.failedStageCount(),
                progress == null ? null : progress.slowestStage(),
                progress == null ? null : progress.slowestStageDurationMs(),
                attentionLevel,
                summary,
                nextAction,
                stages,
                links,
                markdown
        );
    }

    @Override
    public String buildMarkdownMonitor(String jobId) {
        return buildMonitor(jobId).markdown();
    }

    private List<DemoRunMonitorStageVo> stageRows(JobPipelineProgressVo progress, Instant generatedAt) {
        if (progress == null || progress.stages() == null || progress.stages().isEmpty()) {
            return List.of();
        }
        List<DemoRunMonitorStageVo> rows = new ArrayList<>();
        for (JobStageProgressVo stage : progress.stages()) {
            if (stage.status() == null) {
                continue;
            }
            Long runningForMs = runningForMs(stage, generatedAt);
            rows.add(new DemoRunMonitorStageVo(
                    stage.stage(),
                    stage.status(),
                    stage.startedAt(),
                    stage.finishedAt(),
                    stage.durationMs(),
                    runningForMs,
                    stageAttention(stage, runningForMs),
                    safeStageMessage(stage)
            ));
        }
        return List.copyOf(rows);
    }

    private Long runningForMs(JobStageProgressVo stage, Instant generatedAt) {
        if (stage.status() != JobTimelineEventStatus.STARTED || stage.startedAt() == null || stage.finishedAt() != null) {
            return null;
        }
        return Math.max(0L, Duration.between(stage.startedAt(), generatedAt).toMillis());
    }

    private String stageAttention(JobStageProgressVo stage, Long runningForMs) {
        if (stage.status() == JobTimelineEventStatus.FAILED) {
            return "FAILED";
        }
        if (runningForMs != null && runningForMs >= STALE_STAGE_THRESHOLD_MS) {
            return "STALE";
        }
        if (stage.status() == JobTimelineEventStatus.STARTED) {
            return "RUNNING";
        }
        if (stage.status() == JobTimelineEventStatus.SUCCEEDED || stage.status() == JobTimelineEventStatus.CACHE_HIT) {
            return "OK";
        }
        return stage.status().name();
    }

    private String attentionLevel(LocalizationJobVo job, List<DemoRunMonitorStageVo> stages) {
        if (job.status() == LocalizationJobStatus.FAILED || stages.stream().anyMatch(stage -> "FAILED".equals(stage.attention()))) {
            return "BLOCKED";
        }
        if (job.status() == LocalizationJobStatus.CANCELLED) {
            return "ATTENTION";
        }
        if (job.status() == LocalizationJobStatus.COMPLETED) {
            return "READY";
        }
        if (stages.stream().anyMatch(stage -> "STALE".equals(stage.attention()))) {
            return "ATTENTION";
        }
        if (job.status() == LocalizationJobStatus.QUEUED) {
            return "QUEUED";
        }
        return "RUNNING";
    }

    private String summary(LocalizationJobVo job, JobPipelineProgressVo progress, String attentionLevel) {
        if ("READY".equals(attentionLevel)) {
            return "Localization job completed with stage timing evidence ready for review.";
        }
        if ("BLOCKED".equals(attentionLevel)) {
            return "Localization job failed at %s and needs diagnostics before retry or sharing."
                    .formatted(stageName(job.failureStage(), progress));
        }
        if ("ATTENTION".equals(attentionLevel)) {
            return "Localization job is running but has not produced timeline updates within the expected window.";
        }
        if ("QUEUED".equals(attentionLevel)) {
            return "Localization job is queued and waiting for dispatch or worker pickup.";
        }
        return "Localization job is running at %s with %d of %d measured stages complete."
                .formatted(stageName(null, progress), progress == null ? 0 : progress.completedStageCount(), progress == null ? 0 : progress.totalStageCount());
    }

    private String recommendedNextAction(LocalizationJobVo job, String attentionLevel) {
        return switch (attentionLevel) {
            case "READY" -> "Open the demo share sheet or presenter pack for handoff.";
            case "BLOCKED" -> "Open diagnostics and failure triage before retrying or sharing this run.";
            case "ATTENTION" -> "Check worker logs, runtime live checks, and queue health before waiting longer.";
            case "QUEUED" -> "Wait for dispatch, or run private-demo preflight if the job remains queued.";
            default -> "Keep watching this monitor until the job reaches a terminal status.";
        };
    }

    private Long elapsedMs(LocalizationJobVo job, Instant generatedAt) {
        Instant start = job.startedAt() == null ? job.createdAt() : job.startedAt();
        Instant end = terminalAt(job);
        if (start == null) {
            return null;
        }
        return Math.max(0L, Duration.between(start, end == null ? generatedAt : end).toMillis());
    }

    private Instant terminalAt(LocalizationJobVo job) {
        if (job.status() == LocalizationJobStatus.COMPLETED) {
            return job.completedAt();
        }
        if (job.status() == LocalizationJobStatus.FAILED) {
            return job.failedAt();
        }
        return null;
    }

    private String renderMarkdown(
            LocalizationJobVo job,
            Instant generatedAt,
            Long elapsedMs,
            JobPipelineProgressVo progress,
            String attentionLevel,
            String summary,
            String nextAction,
            List<DemoRunMonitorStageVo> stages,
            List<DemoRunMonitorLinkVo> links
    ) {
        List<String> lines = new ArrayList<>();
        lines.add("# LinguaFrame Demo Run Monitor");
        lines.add("");
        lines.add("- Job: " + job.jobId());
        lines.add("- Video: " + job.videoId());
        lines.add("- Generated at: " + generatedAt);
        lines.add("- Status: " + job.status());
        lines.add("- Dispatch status: " + (job.dispatchStatus() == null ? "N/A" : job.dispatchStatus()));
        lines.add("- Attention level: " + attentionLevel);
        lines.add("- Current stage: " + stageName(null, progress));
        lines.add("- Elapsed ms: " + (elapsedMs == null ? "N/A" : elapsedMs));
        lines.add("- Recommended next action: " + nextAction);
        lines.add("");
        lines.add(summary);
        lines.add("");
        lines.add("## Stage Timing");
        if (stages.isEmpty()) {
            lines.add("- No stage timing events have been recorded yet.");
        } else {
            for (DemoRunMonitorStageVo stage : stages) {
                lines.add("- %s: %s, attention=%s, durationMs=%s, runningForMs=%s".formatted(
                        stage.stage(),
                        stage.status(),
                        stage.attention(),
                        value(stage.durationMs()),
                        value(stage.runningForMs())
                ));
            }
        }
        lines.add("");
        lines.add("## Safe Links");
        for (DemoRunMonitorLinkVo link : links) {
            lines.add("- %s: %s".formatted(link.label(), link.url()));
        }
        return String.join("\n", lines) + "\n";
    }

    private List<DemoRunMonitorLinkVo> links(String jobId) {
        return List.of(
                new DemoRunMonitorLinkVo("JOB_DETAIL", "Job detail", "/api/jobs/" + jobId),
                new DemoRunMonitorLinkVo("EVENT_STREAM", "Job event stream", "/api/jobs/" + jobId + "/events"),
                new DemoRunMonitorLinkVo("DIAGNOSTICS_JSON", "Diagnostics JSON", "/api/jobs/" + jobId + "/diagnostics/download"),
                new DemoRunMonitorLinkVo("DEMO_SHARE_SHEET", "Demo share sheet", "/api/jobs/" + jobId + "/demo-share-sheet")
        );
    }

    private String stageName(LocalizationJobStage fallback, JobPipelineProgressVo progress) {
        LocalizationJobStage stage = progress == null ? fallback : progress.currentStage();
        if (stage == null) {
            stage = fallback;
        }
        return stage == null ? "N/A" : stage.name();
    }

    private String safeStageMessage(JobStageProgressVo stage) {
        if (stage.message() == null || stage.message().isBlank()) {
            return null;
        }
        return switch (stage.status()) {
            case FAILED -> "Stage failed. Open diagnostics for sanitized failure triage.";
            case STARTED -> "Stage is running.";
            case SUCCEEDED -> "Stage completed.";
            case CACHE_HIT -> "Stage reused cached provider or artifact output.";
            case SKIPPED -> "Stage skipped.";
        };
    }

    private String value(Long value) {
        return value == null ? "N/A" : value.toString();
    }
}
