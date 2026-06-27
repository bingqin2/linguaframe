package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.LocalizationJobExecutionResultVo;
import com.linguaframe.job.domain.vo.ProviderCacheHitVo;
import com.linguaframe.job.repository.JobTimelineEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.LocalizationJobExecutionService;
import com.linguaframe.job.service.LocalizationPipelineStage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class LocalizationJobExecutionServiceImpl implements LocalizationJobExecutionService {

    private final LocalizationJobRepository jobRepository;
    private final JobTimelineEventRepository timelineEventRepository;
    private final List<LocalizationPipelineStage> stages;
    private final Clock clock;
    private final AtomicLong timelineSequence = new AtomicLong();

    @Autowired
    public LocalizationJobExecutionServiceImpl(
            LocalizationJobRepository jobRepository,
            JobTimelineEventRepository timelineEventRepository,
            List<LocalizationPipelineStage> stages
    ) {
        this(jobRepository, timelineEventRepository, stages, Clock.systemUTC());
    }

    public LocalizationJobExecutionServiceImpl(
            LocalizationJobRepository jobRepository,
            JobTimelineEventRepository timelineEventRepository,
            List<LocalizationPipelineStage> stages,
            Clock clock
    ) {
        this.jobRepository = jobRepository;
        this.timelineEventRepository = timelineEventRepository;
        this.stages = stages.stream()
                .sorted(Comparator.comparing(stage -> stage.stage().ordinal()))
                .toList();
        this.clock = clock;
    }

    @Override
    public LocalizationJobExecutionResultVo execute(QueuedLocalizationJobMessage message) {
        LocalizationJobRecord job = jobRepository.findById(message.jobId())
                .orElseThrow(() -> new NoSuchElementException("Localization job not found."));
        Instant startedAt = Instant.now(clock);
        if (!jobRepository.claimForExecution(job.id(), startedAt)) {
            saveTimeline(job.id(), LocalizationJobStage.WORKER_RECEIVED, JobTimelineEventStatus.SKIPPED,
                    "Worker skipped stale localization job message.", null, null, startedAt);
            return new LocalizationJobExecutionResultVo(job.id(), false, job.status());
        }

        saveTimeline(job.id(), LocalizationJobStage.WORKER_RECEIVED, JobTimelineEventStatus.STARTED,
                "Worker received localization job.", null, null, startedAt);

        LocalizationJobRecord claimedJob = jobRepository.findById(job.id())
                .orElseThrow(() -> new NoSuchElementException("Localization job not found."));
        if (!claimedJob.videoId().equals(message.videoId())) {
            String error = "Queued message videoId does not match localization job.";
            return fail(claimedJob.id(), LocalizationJobStage.WORKER_RECEIVED, error, startedAt);
        }

        LocalizationJobExecutionContextBo context = new LocalizationJobExecutionContextBo(claimedJob, message, startedAt);
        for (LocalizationPipelineStage stage : stages) {
            Instant stageStartedAt = Instant.now(clock);
            if (isCancelled(job.id())) {
                return cancelled(job.id(), stage.stage(), stageStartedAt);
            }
            saveTimeline(job.id(), stage.stage(), JobTimelineEventStatus.STARTED,
                    stage.stage().name() + " started.", null, null, stageStartedAt);
            try {
                stage.execute(context);
            } catch (RuntimeException ex) {
                String error = safeError(ex);
                jobRepository.markFailed(job.id(), stage.stage(), error, Instant.now(clock));
                saveTimeline(job.id(), stage.stage(), JobTimelineEventStatus.FAILED,
                        stage.stage().name() + " failed.", durationMs(stageStartedAt, Instant.now(clock)), error, Instant.now(clock));
                return new LocalizationJobExecutionResultVo(job.id(), true, LocalizationJobStatus.FAILED);
            }
            for (JobArtifactVo artifact : context.consumeCacheHits()) {
                saveTimeline(job.id(), stage.stage(), JobTimelineEventStatus.CACHE_HIT,
                        "Reused cached %s artifact %s.".formatted(artifact.type().name(), artifact.artifactId()),
                        null, null, Instant.now(clock));
            }
            for (ProviderCacheHitVo hit : context.consumeProviderCacheHits()) {
                saveTimeline(job.id(), stage.stage(), JobTimelineEventStatus.CACHE_HIT,
                        "Reused cached %s provider result from job %s."
                                .formatted(hit.operation().name(), hit.sourceJobId()),
                        null, null, Instant.now(clock));
            }
            Instant stageFinishedAt = Instant.now(clock);
            saveTimeline(job.id(), stage.stage(), JobTimelineEventStatus.SUCCEEDED,
                    stage.stage().name() + " succeeded.", durationMs(stageStartedAt, stageFinishedAt), null, stageFinishedAt);
            if (isCancelled(job.id())) {
                return cancelled(job.id(), stage.stage(), stageFinishedAt);
            }
        }

        Instant completedAt = Instant.now(clock);
        jobRepository.markCompleted(job.id(), completedAt);
        saveTimeline(job.id(), LocalizationJobStage.COMPLETED, JobTimelineEventStatus.SUCCEEDED,
                "Localization job completed.", durationMs(startedAt, completedAt), null, completedAt);
        return new LocalizationJobExecutionResultVo(job.id(), true, LocalizationJobStatus.COMPLETED);
    }

    private LocalizationJobExecutionResultVo fail(
            String jobId,
            LocalizationJobStage stage,
            String error,
            Instant occurredAt
    ) {
        jobRepository.markFailed(jobId, stage, error, occurredAt);
        saveTimeline(jobId, stage, JobTimelineEventStatus.FAILED, stage.name() + " failed.", null, error, occurredAt);
        return new LocalizationJobExecutionResultVo(jobId, true, LocalizationJobStatus.FAILED);
    }

    private boolean isCancelled(String jobId) {
        return jobRepository.isCancelled(jobId);
    }

    private LocalizationJobExecutionResultVo cancelled(
            String jobId,
            LocalizationJobStage stage,
            Instant occurredAt
    ) {
        saveTimeline(jobId, stage, JobTimelineEventStatus.SKIPPED,
                "Localization job cancelled.", null, null, occurredAt);
        return new LocalizationJobExecutionResultVo(jobId, true, LocalizationJobStatus.CANCELLED);
    }

    private void saveTimeline(
            String jobId,
            LocalizationJobStage stage,
            JobTimelineEventStatus status,
            String message,
            Long durationMs,
            String errorSummary,
            Instant occurredAt
    ) {
        timelineEventRepository.save(new JobTimelineEventRecord(
                nextTimelineEventId(),
                jobId,
                stage,
                status,
                message,
                durationMs,
                errorSummary,
                occurredAt
        ));
    }

    private String nextTimelineEventId() {
        return "%012d-%s".formatted(timelineSequence.incrementAndGet(), UUID.randomUUID().toString().substring(0, 23));
    }

    private long durationMs(Instant startedAt, Instant finishedAt) {
        return Duration.between(startedAt, finishedAt).toMillis();
    }

    private String safeError(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message;
    }
}
