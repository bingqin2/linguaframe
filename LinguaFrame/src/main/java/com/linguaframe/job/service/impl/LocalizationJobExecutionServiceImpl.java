package com.linguaframe.job.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.common.logging.LinguaFrameLogContext;
import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.WorkerRole;
import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.LocalizationJobExecutionResultVo;
import com.linguaframe.job.domain.vo.ProviderCacheHitVo;
import com.linguaframe.job.domain.vo.WorkerStagePlanVo;
import com.linguaframe.job.repository.JobTimelineEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.JobQueuePublisher;
import com.linguaframe.job.service.LocalizationJobExecutionService;
import com.linguaframe.job.service.LocalizationJobStatusCacheService;
import com.linguaframe.job.service.LocalizationPipelineStage;
import com.linguaframe.job.service.WorkerStageRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class LocalizationJobExecutionServiceImpl implements LocalizationJobExecutionService {

    private static final Logger log = LoggerFactory.getLogger(LocalizationJobExecutionServiceImpl.class);

    private static final LocalizationJobStatusCacheService NOOP_JOB_STATUS_CACHE =
            new LocalizationJobStatusCacheService() {
                @Override
                public Optional<com.linguaframe.job.domain.vo.LocalizationJobVo> get(String jobId) {
                    return Optional.empty();
                }

                @Override
                public void put(com.linguaframe.job.domain.vo.LocalizationJobVo job) {
                }

                @Override
                public void evict(String jobId) {
                }
            };

    private final LocalizationJobRepository jobRepository;
    private final JobTimelineEventRepository timelineEventRepository;
    private final List<LocalizationPipelineStage> stages;
    private final LinguaFrameProperties properties;
    private final WorkerStageRouter stageRouter;
    private final JobQueuePublisher publisher;
    private final LocalizationJobStatusCacheService jobStatusCacheService;
    private final Clock clock;
    private final AtomicLong timelineSequence = new AtomicLong();

    @Autowired
    public LocalizationJobExecutionServiceImpl(
            LocalizationJobRepository jobRepository,
            JobTimelineEventRepository timelineEventRepository,
            List<LocalizationPipelineStage> stages,
            LinguaFrameProperties properties,
            WorkerStageRouter stageRouter,
            JobQueuePublisher publisher,
            LocalizationJobStatusCacheService jobStatusCacheService
    ) {
        this(
                jobRepository,
                timelineEventRepository,
                stages,
                properties,
                stageRouter,
                publisher,
                jobStatusCacheService,
                Clock.systemUTC()
        );
    }

    public LocalizationJobExecutionServiceImpl(
            LocalizationJobRepository jobRepository,
            JobTimelineEventRepository timelineEventRepository,
            List<LocalizationPipelineStage> stages,
            Clock clock
    ) {
        this(
                jobRepository,
                timelineEventRepository,
                stages,
                new LinguaFrameProperties(),
                new WorkerStageRouterImpl(),
                message -> {
                },
                NOOP_JOB_STATUS_CACHE,
                clock
        );
    }

    public LocalizationJobExecutionServiceImpl(
            LocalizationJobRepository jobRepository,
            JobTimelineEventRepository timelineEventRepository,
            List<LocalizationPipelineStage> stages,
            LinguaFrameProperties properties,
            WorkerStageRouter stageRouter,
            JobQueuePublisher publisher,
            LocalizationJobStatusCacheService jobStatusCacheService,
            Clock clock
    ) {
        this.jobRepository = jobRepository;
        this.timelineEventRepository = timelineEventRepository;
        this.stages = stages.stream()
                .sorted(Comparator.comparing(stage -> stage.stage().ordinal()))
                .toList();
        this.properties = properties;
        this.stageRouter = stageRouter;
        this.publisher = publisher;
        this.jobStatusCacheService = jobStatusCacheService;
        this.clock = clock;
    }

    @Override
    public LocalizationJobExecutionResultVo execute(QueuedLocalizationJobMessage message) {
        try (AutoCloseable ignored = LinguaFrameLogContext.withJob(message.jobId(), message.videoId())) {
            LocalizationJobRecord job = jobRepository.findById(message.jobId())
                    .orElseThrow(() -> new NoSuchElementException("Localization job not found."));
            Instant startedAt = Instant.now(clock);
            LocalizationJobStage startStage = message.startStage();
            boolean firstSegment = startStage == LocalizationJobStage.WORKER_SMOKE;
            log.info("Worker received localization job message startStage={}", startStage);

            if (firstSegment && !jobRepository.claimForExecution(job.id(), startedAt)) {
                log.info("Worker skipped stale localization job message status={}", job.status());
                saveTimeline(job.id(), LocalizationJobStage.WORKER_RECEIVED, JobTimelineEventStatus.SKIPPED,
                        "Worker skipped stale localization job message.", null, null, startedAt);
                return new LocalizationJobExecutionResultVo(job.id(), false, job.status());
            }
            if (firstSegment) {
                evictCachedJob(job.id());
            }
            if (!firstSegment && job.status() != LocalizationJobStatus.PROCESSING) {
                log.info("Worker skipped stale localization job handoff message status={} startStage={}", job.status(), startStage);
                saveTimeline(job.id(), startStage, JobTimelineEventStatus.SKIPPED,
                        "Worker skipped stale localization job handoff message.", null, null, startedAt);
                return new LocalizationJobExecutionResultVo(job.id(), false, job.status());
            }

            if (firstSegment) {
                saveTimeline(job.id(), LocalizationJobStage.WORKER_RECEIVED, JobTimelineEventStatus.STARTED,
                        "Worker received localization job.", null, null, startedAt);
            }

            LocalizationJobRecord claimedJob = jobRepository.findById(job.id())
                    .orElseThrow(() -> new NoSuchElementException("Localization job not found."));
            if (!claimedJob.videoId().equals(message.videoId())) {
                String error = "Queued message videoId does not match localization job.";
                log.warn("Worker failed localization job because queued video id did not match stored job");
                return fail(claimedJob.id(), LocalizationJobStage.WORKER_RECEIVED, error, startedAt);
            }

            WorkerStagePlanVo plan;
            WorkerRole role = properties.getWorker().getRole();
            try {
                plan = stageRouter.plan(role, startStage, stages);
                log.info(
                        "Worker selected stage plan executableStageCount={} nextStage={} finalSegment={}",
                        plan.executableStages().size(),
                        plan.nextStage(),
                        plan.finalSegment()
                );
            } catch (RuntimeException ex) {
                log.warn("Worker failed to plan localization job stages role={} startStage={}", role, startStage);
                return fail(claimedJob.id(), startStage, safeError(ex), startedAt);
            }

            LocalizationJobExecutionContextBo context = new LocalizationJobExecutionContextBo(claimedJob, message, startedAt);
            for (LocalizationPipelineStage stage : plan.executableStages()) {
                Instant stageStartedAt = Instant.now(clock);
                if (isCancelled(job.id())) {
                    return cancelled(job.id(), stage.stage(), stageStartedAt);
                }
                saveTimeline(job.id(), stage.stage(), JobTimelineEventStatus.STARTED,
                        stage.stage().name() + " started.", null, null, stageStartedAt);
                try (AutoCloseable stageScope = LinguaFrameLogContext.withStage(stage.stage().name(), role.name())) {
                    log.info("Worker stage started");
                    try {
                        stage.execute(context);
                    } catch (RuntimeException ex) {
                        String error = safeError(ex);
                        jobRepository.markFailed(job.id(), stage.stage(), error, Instant.now(clock));
                        saveTimeline(job.id(), stage.stage(), JobTimelineEventStatus.FAILED,
                                stage.stage().name() + " failed.", durationMs(stageStartedAt, Instant.now(clock)), error, Instant.now(clock));
                        evictCachedJob(job.id());
                        log.warn("Worker stage failed");
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
                    log.info("Worker stage succeeded durationMs={}", durationMs(stageStartedAt, stageFinishedAt));
                    if (isCancelled(job.id())) {
                        return cancelled(job.id(), stage.stage(), stageFinishedAt);
                    }
                }
            }

            if (!plan.finalSegment()) {
                handoff(message, plan.nextStage(), startedAt);
                return new LocalizationJobExecutionResultVo(job.id(), true, LocalizationJobStatus.PROCESSING);
            } else {
                Instant completedAt = Instant.now(clock);
                jobRepository.markCompleted(job.id(), completedAt);
                saveTimeline(job.id(), LocalizationJobStage.COMPLETED, JobTimelineEventStatus.SUCCEEDED,
                        "Localization job completed.", durationMs(startedAt, completedAt), null, completedAt);
                evictCachedJob(job.id());
                log.info("Worker completed localization job durationMs={}", durationMs(startedAt, completedAt));
                return new LocalizationJobExecutionResultVo(job.id(), true, LocalizationJobStatus.COMPLETED);
            }
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to clear localization job log context.", ex);
        }
    }

    private void handoff(QueuedLocalizationJobMessage currentMessage, LocalizationJobStage nextStage, Instant startedAt) {
        QueuedLocalizationJobMessage nextMessage = new QueuedLocalizationJobMessage(
                currentMessage.jobId(),
                currentMessage.videoId(),
                currentMessage.sourceObjectKey(),
                currentMessage.targetLanguage(),
                currentMessage.ttsVoice(),
                currentMessage.translationStyle(),
                currentMessage.subtitleStylePreset(),
                currentMessage.translationGlossaryJson(),
                currentMessage.translationGlossaryHash(),
                currentMessage.translationGlossaryEntryCount(),
                currentMessage.subtitlePolishingMode(),
                currentMessage.demoProfileId(),
                currentMessage.createdAt(),
                nextStage
        );
        publisher.publish(nextMessage);
        log.info("Worker handed off localization job nextStage={}", nextStage);
        saveTimeline(currentMessage.jobId(), nextStage, JobTimelineEventStatus.SKIPPED,
                "Worker handed off localization job to " + nextStage.name() + ".",
                durationMs(startedAt, Instant.now(clock)), null, Instant.now(clock));
    }

    private LocalizationJobExecutionResultVo fail(
            String jobId,
            LocalizationJobStage stage,
            String error,
            Instant occurredAt
    ) {
        jobRepository.markFailed(jobId, stage, error, occurredAt);
        log.warn("Worker marked localization job failed stage={}", stage);
        saveTimeline(jobId, stage, JobTimelineEventStatus.FAILED, stage.name() + " failed.", null, error, occurredAt);
        evictCachedJob(jobId);
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
        log.info("Worker stopped localization job because it was cancelled stage={}", stage);
        evictCachedJob(jobId);
        return new LocalizationJobExecutionResultVo(jobId, true, LocalizationJobStatus.CANCELLED);
    }

    private void evictCachedJob(String jobId) {
        try {
            jobStatusCacheService.evict(jobId);
        } catch (RuntimeException exception) {
            // Cache eviction is best-effort and must not roll back worker state changes.
        }
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
