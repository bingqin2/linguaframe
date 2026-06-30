package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.entity.JobDispatchEventRecord;
import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.JobDispatchEventType;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.exception.JobStateConflictException;
import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.domain.vo.StuckJobRecoveryActionVo;
import com.linguaframe.job.domain.vo.StuckJobRecoveryCheckVo;
import com.linguaframe.job.domain.vo.StuckJobRecoveryLinkVo;
import com.linguaframe.job.domain.vo.StuckJobRecoveryVo;
import com.linguaframe.job.repository.JobDispatchEventRepository;
import com.linguaframe.job.repository.JobTimelineEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.LocalizationJobCancellationService;
import com.linguaframe.job.service.LocalizationJobRetryService;
import com.linguaframe.job.service.LocalizationJobStatusCacheService;
import com.linguaframe.job.service.StuckJobRecoveryService;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.repository.VideoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class StuckJobRecoveryServiceImpl implements StuckJobRecoveryService {

    private static final String ACTION_REQUEUE_DISPATCH = "REQUEUE_DISPATCH";
    private static final String ACTION_CANCEL_JOB = "CANCEL_JOB";
    private static final String ACTION_RETRY_FAILED_JOB = "RETRY_FAILED_JOB";

    private final LocalizationJobRepository jobRepository;
    private final VideoRepository videoRepository;
    private final JobDispatchEventRepository dispatchEventRepository;
    private final JobTimelineEventRepository timelineEventRepository;
    private final LocalizationJobCancellationService cancellationService;
    private final LocalizationJobRetryService retryService;
    private final LocalizationJobStatusCacheService jobStatusCacheService;
    private final ObjectMapper objectMapper;
    private final LinguaFrameProperties properties;
    private final Clock clock;

    @Autowired
    public StuckJobRecoveryServiceImpl(
            LocalizationJobRepository jobRepository,
            VideoRepository videoRepository,
            JobDispatchEventRepository dispatchEventRepository,
            JobTimelineEventRepository timelineEventRepository,
            LocalizationJobCancellationService cancellationService,
            LocalizationJobRetryService retryService,
            LocalizationJobStatusCacheService jobStatusCacheService,
            ObjectMapper objectMapper,
            LinguaFrameProperties properties
    ) {
        this(
                jobRepository,
                videoRepository,
                dispatchEventRepository,
                timelineEventRepository,
                cancellationService,
                retryService,
                jobStatusCacheService,
                objectMapper,
                properties,
                Clock.systemUTC()
        );
    }

    public StuckJobRecoveryServiceImpl(
            LocalizationJobRepository jobRepository,
            VideoRepository videoRepository,
            JobDispatchEventRepository dispatchEventRepository,
            JobTimelineEventRepository timelineEventRepository,
            LocalizationJobCancellationService cancellationService,
            LocalizationJobRetryService retryService,
            LocalizationJobStatusCacheService jobStatusCacheService,
            ObjectMapper objectMapper,
            LinguaFrameProperties properties,
            Clock clock
    ) {
        this.jobRepository = jobRepository;
        this.videoRepository = videoRepository;
        this.dispatchEventRepository = dispatchEventRepository;
        this.timelineEventRepository = timelineEventRepository;
        this.cancellationService = cancellationService;
        this.retryService = retryService;
        this.jobStatusCacheService = jobStatusCacheService;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public StuckJobRecoveryVo recovery(String jobId) {
        LocalizationJobRecord job = findJob(jobId);
        JobDispatchEventRecord dispatch = dispatchEventRepository.findLatestByJobId(jobId).orElse(null);
        JobTimelineEventRecord latestTimeline = timelineEventRepository.findLatestByJobId(jobId).orElse(null);
        Instant now = Instant.now(clock);
        long thresholdSeconds = Math.max(1, properties.getWorker().getStageTimeoutSeconds());
        long ageSeconds = secondsBetween(job.createdAt(), now);
        long staleSeconds = staleSeconds(job, dispatch, latestTimeline, now);
        String classification = classification(job, dispatch, latestTimeline, staleSeconds, thresholdSeconds);
        String attentionLevel = attentionLevel(classification);
        String status = status(attentionLevel);
        List<StuckJobRecoveryCheckVo> checks = checks(job, dispatch, latestTimeline, classification, staleSeconds, thresholdSeconds);
        List<StuckJobRecoveryActionVo> actions = actions(job.id(), classification);
        List<StuckJobRecoveryLinkVo> links = links(job.id());
        String headline = headline(classification);
        String nextAction = recommendedNextAction(classification);
        String markdown = markdown(job, dispatch, latestTimeline, now, status, attentionLevel, classification,
                headline, nextAction, ageSeconds, staleSeconds, checks, actions, links);
        return new StuckJobRecoveryVo(
                job.id(),
                job.videoId(),
                now,
                status,
                attentionLevel,
                classification,
                headline,
                nextAction,
                job.status(),
                dispatch == null ? null : dispatch.status(),
                dispatch == null ? 0 : dispatch.attempts(),
                dispatch == null ? null : dispatch.dispatchedAt(),
                latestTimeline == null ? null : latestTimeline.occurredAt(),
                ageSeconds,
                staleSeconds,
                checks,
                actions,
                links,
                List.of(
                        "Recovery output is metadata-only and excludes external request bodies, media text, storage keys, local paths, and secrets.",
                        "Recovery actions require explicit confirmation and do not call OpenAI, FFmpeg, Docker, or object storage."
                ),
                markdown
        );
    }

    @Override
    public String recoveryMarkdown(String jobId) {
        return recovery(jobId).markdown();
    }

    @Override
    @Transactional
    public StuckJobRecoveryVo runAction(String jobId, String actionId, String confirmation) {
        if (actionId == null || !actionId.equals(confirmation)) {
            throw new JobStateConflictException("Recovery action confirmation must match the action id.");
        }
        StuckJobRecoveryVo current = recovery(jobId);
        if (ACTION_REQUEUE_DISPATCH.equals(actionId)) {
            if (!"QUEUED_STALE_DISPATCH".equals(current.classification())) {
                throw new JobStateConflictException("Dispatch can only be requeued for stale queued jobs.");
            }
            requeueDispatch(findJob(jobId));
            return recovery(jobId);
        }
        if (ACTION_CANCEL_JOB.equals(actionId)) {
            cancellationService.cancelJob(jobId);
            return recovery(jobId);
        }
        if (ACTION_RETRY_FAILED_JOB.equals(actionId)) {
            if (!"FAILED_RETRYABLE".equals(current.classification())) {
                throw new JobStateConflictException("Only retryable failed jobs can be retried from stuck job recovery.");
            }
            retryService.retryFailedJob(jobId);
            return recovery(jobId);
        }
        throw new JobStateConflictException("Unsupported stuck job recovery action.");
    }

    private LocalizationJobRecord findJob(String jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Localization job not found."));
    }

    private String classification(
            LocalizationJobRecord job,
            JobDispatchEventRecord dispatch,
            JobTimelineEventRecord latestTimeline,
            long staleSeconds,
            long thresholdSeconds
    ) {
        if (job.status() == LocalizationJobStatus.COMPLETED) {
            return "READY";
        }
        if (job.status() == LocalizationJobStatus.CANCELLED) {
            return "CANCELLED";
        }
        if (job.status() == LocalizationJobStatus.FAILED) {
            return job.retryCount() < properties.getWorker().getMaxRetries() ? "FAILED_RETRYABLE" : "FAILED_BLOCKED";
        }
        if ((job.status() == LocalizationJobStatus.QUEUED || job.status() == LocalizationJobStatus.RETRYING)) {
            if (dispatch == null || dispatch.status() != JobDispatchEventStatus.DISPATCHED) {
                return staleSeconds >= thresholdSeconds ? "QUEUED_STALE_DISPATCH" : "QUEUED_WAITING";
            }
            return "QUEUED_WAITING";
        }
        if (job.status() == LocalizationJobStatus.PROCESSING) {
            if (latestTimeline != null
                    && latestTimeline.status() == JobTimelineEventStatus.STARTED
                    && staleSeconds >= thresholdSeconds) {
                return "PROCESSING_STALE_STAGE";
            }
            return "UNKNOWN";
        }
        return "UNKNOWN";
    }

    private long staleSeconds(
            LocalizationJobRecord job,
            JobDispatchEventRecord dispatch,
            JobTimelineEventRecord latestTimeline,
            Instant now
    ) {
        Instant anchor = job.updatedAt();
        if (job.status() == LocalizationJobStatus.QUEUED || job.status() == LocalizationJobStatus.RETRYING) {
            if (dispatch != null) {
                anchor = dispatch.dispatchedAt() == null ? dispatch.createdAt() : dispatch.dispatchedAt();
            }
        } else if (job.status() == LocalizationJobStatus.PROCESSING && latestTimeline != null) {
            anchor = latestTimeline.occurredAt();
        }
        return secondsBetween(anchor, now);
    }

    private List<StuckJobRecoveryCheckVo> checks(
            LocalizationJobRecord job,
            JobDispatchEventRecord dispatch,
            JobTimelineEventRecord latestTimeline,
            String classification,
            long staleSeconds,
            long thresholdSeconds
    ) {
        List<StuckJobRecoveryCheckVo> checks = new ArrayList<>();
        checks.add(new StuckJobRecoveryCheckVo(
                "job-state",
                "Job state",
                checkStatus("READY".equals(classification), "FAILED_BLOCKED".equals(classification)),
                "Job is " + job.status() + " with retry count " + job.retryCount() + ".",
                job.status() == LocalizationJobStatus.FAILED ? "Use retry only when the failure is understood." : "Review dispatch and timeline freshness.",
                "FAILED_BLOCKED".equals(classification)
        ));
        checks.add(new StuckJobRecoveryCheckVo(
                "dispatch-outbox",
                "Dispatch outbox",
                "QUEUED_STALE_DISPATCH".equals(classification) ? "BLOCKED" : "READY",
                dispatch == null ? "No dispatch event is recorded." : "Latest dispatch event is " + dispatch.status() + " with " + dispatch.attempts() + " attempts.",
                "QUEUED_STALE_DISPATCH".equals(classification) ? "Requeue dispatch after confirming worker readiness." : "No dispatch requeue is currently recommended.",
                "QUEUED_STALE_DISPATCH".equals(classification)
        ));
        checks.add(new StuckJobRecoveryCheckVo(
                "timeline-freshness",
                "Timeline freshness",
                "PROCESSING_STALE_STAGE".equals(classification) ? "ATTENTION" : "READY",
                latestTimeline == null ? "No timeline events recorded." : "Latest timeline event is " + latestTimeline.status() + " at " + latestTimeline.occurredAt() + ".",
                staleSeconds >= thresholdSeconds ? "Inspect worker logs and live checks before waiting longer." : "Continue monitoring.",
                "PROCESSING_STALE_STAGE".equals(classification)
        ));
        checks.add(new StuckJobRecoveryCheckVo(
                "safety",
                "Recovery safety",
                "READY",
                "Actions require explicit confirmation and reuse existing job state transitions.",
                "Do not use recovery actions as a substitute for fixing broken runtime dependencies.",
                false
        ));
        return List.copyOf(checks);
    }

    private List<StuckJobRecoveryActionVo> actions(String jobId, String classification) {
        String route = "/api/jobs/" + jobId + "/stuck-job-recovery/actions";
        return List.of(
                new StuckJobRecoveryActionVo(
                        ACTION_REQUEUE_DISPATCH,
                        "Requeue dispatch",
                        "POST",
                        route,
                        "QUEUED_STALE_DISPATCH".equals(classification),
                        true,
                        "Create a fresh dispatch outbox event for a stale queued or retrying job."
                ),
                new StuckJobRecoveryActionVo(
                        ACTION_CANCEL_JOB,
                        "Cancel job",
                        "POST",
                        route,
                        "QUEUED_STALE_DISPATCH".equals(classification) || "PROCESSING_STALE_STAGE".equals(classification) || "QUEUED_WAITING".equals(classification),
                        true,
                        "Cancel an active job using the existing cancellation state transition."
                ),
                new StuckJobRecoveryActionVo(
                        ACTION_RETRY_FAILED_JOB,
                        "Retry failed job",
                        "POST",
                        route,
                        "FAILED_RETRYABLE".equals(classification),
                        true,
                        "Retry a failed job using the existing retry limit and dispatch behavior."
                )
        );
    }

    private List<StuckJobRecoveryLinkVo> links(String jobId) {
        return List.of(
                new StuckJobRecoveryLinkVo("JOB_DETAIL", "Job detail", "/api/jobs/" + jobId, "application/json", "Current job detail."),
                new StuckJobRecoveryLinkVo("RUN_MONITOR", "Demo run monitor", "/api/jobs/" + jobId + "/demo-run-monitor", "application/json", "Stage timing monitor."),
                new StuckJobRecoveryLinkVo("DIAGNOSTICS", "Diagnostics", "/api/jobs/" + jobId + "/diagnostics/download", "application/json", "Safe diagnostics report."),
                new StuckJobRecoveryLinkVo("MARKDOWN", "Recovery Markdown", "/api/jobs/" + jobId + "/stuck-job-recovery/markdown/download", "text/markdown", "Downloadable recovery notes.")
        );
    }

    private void requeueDispatch(LocalizationJobRecord job) {
        VideoRecord video = videoRepository.findById(job.videoId())
                .orElseThrow(() -> new NoSuchElementException("Source video not found."));
        Instant now = Instant.now(clock);
        try {
            dispatchEventRepository.save(new JobDispatchEventRecord(
                    UUID.randomUUID().toString(),
                    job.id(),
                    JobDispatchEventType.LOCALIZATION_JOB_QUEUED,
                    objectMapper.writeValueAsString(message(video, job)),
                    JobDispatchEventStatus.PENDING,
                    0,
                    now,
                    null,
                    null,
                    now,
                    now
            ));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to create stuck job recovery dispatch payload.", ex);
        }
        timelineEventRepository.save(timeline(job.id(), JobTimelineEventStatus.STARTED, "Stale dispatch requeued by operator.", now));
        evictCachedJob(job.id());
    }

    private void evictCachedJob(String jobId) {
        try {
            jobStatusCacheService.evict(jobId);
        } catch (RuntimeException exception) {
            // Cache eviction is best-effort and must not roll back recovery.
        }
    }

    private QueuedLocalizationJobMessage message(VideoRecord video, LocalizationJobRecord job) {
        return new QueuedLocalizationJobMessage(
                job.id(),
                video.id(),
                video.sourceObjectKey(),
                job.targetLanguage(),
                job.ttsVoice(),
                job.translationStyle(),
                job.subtitleStylePreset(),
                job.translationGlossaryJson(),
                job.translationGlossaryHash(),
                job.translationGlossaryEntryCount(),
                job.subtitlePolishingMode(),
                job.demoProfileId(),
                job.createdAt(),
                LocalizationJobStage.WORKER_SMOKE
        );
    }

    private JobTimelineEventRecord timeline(String jobId, JobTimelineEventStatus status, String message, Instant now) {
        return new JobTimelineEventRecord(
                UUID.randomUUID().toString(),
                jobId,
                LocalizationJobStage.WORKER_RECEIVED,
                status,
                message,
                null,
                null,
                now
        );
    }

    private String markdown(
            LocalizationJobRecord job,
            JobDispatchEventRecord dispatch,
            JobTimelineEventRecord latestTimeline,
            Instant generatedAt,
            String status,
            String attentionLevel,
            String classification,
            String headline,
            String nextAction,
            long ageSeconds,
            long staleSeconds,
            List<StuckJobRecoveryCheckVo> checks,
            List<StuckJobRecoveryActionVo> actions,
            List<StuckJobRecoveryLinkVo> links
    ) {
        List<String> lines = new ArrayList<>();
        lines.add("# LinguaFrame Stuck Job Recovery");
        lines.add("");
        lines.add("- Job: " + job.id());
        lines.add("- Video: " + job.videoId());
        lines.add("- Generated at: " + generatedAt);
        lines.add("- Status: " + status);
        lines.add("- Attention: " + attentionLevel);
        lines.add("- Classification: " + classification);
        lines.add("- Job status: " + job.status());
        lines.add("- Dispatch status: " + (dispatch == null ? "N/A" : dispatch.status()));
        lines.add("- Last timeline at: " + (latestTimeline == null ? "N/A" : latestTimeline.occurredAt()));
        lines.add("- Age seconds: " + ageSeconds);
        lines.add("- Stale seconds: " + staleSeconds);
        lines.add("- Recommended next action: " + nextAction);
        lines.add("");
        lines.add(headline);
        lines.add("");
        lines.add("## Checks");
        checks.forEach(check -> lines.add("- %s: %s. %s Next: %s".formatted(
                check.key(),
                check.status(),
                check.detail(),
                check.nextAction()
        )));
        lines.add("");
        lines.add("## Actions");
        actions.forEach(action -> lines.add("- %s: enabled=%s, confirmation=%s. %s".formatted(
                action.id(),
                action.enabled(),
                action.requiresConfirmation(),
                action.description()
        )));
        lines.add("");
        lines.add("## Safe Links");
        links.forEach(link -> lines.add("- %s: %s".formatted(link.label(), link.href())));
        lines.add("");
        lines.add("This report is metadata-only and excludes media text, storage keys, local paths, external request bodies, and secrets.");
        return String.join("\n", lines);
    }

    private String headline(String classification) {
        return switch (classification) {
            case "READY" -> "Job is complete and does not need stuck-job recovery.";
            case "FAILED_RETRYABLE" -> "Job failed and can be retried after diagnostics review.";
            case "FAILED_BLOCKED" -> "Job failed and retry is blocked by the configured retry limit.";
            case "QUEUED_WAITING" -> "Job is queued and still within the expected dispatch window.";
            case "QUEUED_STALE_DISPATCH" -> "Job appears stuck before worker pickup and can be requeued after runtime checks.";
            case "PROCESSING_STALE_STAGE" -> "Job is processing but the current stage appears stale.";
            case "CANCELLED" -> "Job is cancelled and has no active recovery action.";
            default -> "Job recovery state needs manual inspection.";
        };
    }

    private String recommendedNextAction(String classification) {
        return switch (classification) {
            case "READY" -> "Open handoff evidence or the demo presenter pack.";
            case "FAILED_RETRYABLE" -> "Review diagnostics, then retry the failed job if the cause is resolved.";
            case "FAILED_BLOCKED" -> "Create a new run after fixing the underlying issue; retry limit is reached.";
            case "QUEUED_WAITING" -> "Wait for dispatch or run private-demo preflight if it remains queued.";
            case "QUEUED_STALE_DISPATCH" -> "Run live checks, confirm worker readiness, then requeue dispatch if appropriate.";
            case "PROCESSING_STALE_STAGE" -> "Inspect worker logs and live checks; cancel only if the job is no longer progressing.";
            case "CANCELLED" -> "No recovery action is available for a cancelled job.";
            default -> "Inspect diagnostics and runtime live checks before taking action.";
        };
    }

    private String attentionLevel(String classification) {
        return switch (classification) {
            case "READY" -> "READY";
            case "FAILED_RETRYABLE", "FAILED_BLOCKED", "QUEUED_STALE_DISPATCH" -> "BLOCKED";
            case "PROCESSING_STALE_STAGE", "CANCELLED", "UNKNOWN" -> "ATTENTION";
            default -> "WATCH";
        };
    }

    private String status(String attentionLevel) {
        return switch (attentionLevel) {
            case "READY" -> "READY";
            case "BLOCKED" -> "BLOCKED";
            case "ATTENTION" -> "ATTENTION";
            default -> "WATCH";
        };
    }

    private String checkStatus(boolean ready, boolean blocked) {
        if (blocked) {
            return "BLOCKED";
        }
        return ready ? "READY" : "ATTENTION";
    }

    private long secondsBetween(Instant start, Instant end) {
        if (start == null || end == null) {
            return 0;
        }
        return Math.max(0, Duration.between(start, end).toSeconds());
    }
}
