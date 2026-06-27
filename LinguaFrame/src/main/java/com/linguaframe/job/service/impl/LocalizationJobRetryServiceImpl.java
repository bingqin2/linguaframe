package com.linguaframe.job.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.exception.JobStateConflictException;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.repository.JobTimelineEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.JobDispatchOutboxService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.job.service.LocalizationJobRetryService;
import com.linguaframe.job.service.LocalizationJobStatusCacheService;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.repository.VideoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class LocalizationJobRetryServiceImpl implements LocalizationJobRetryService {

    private final LocalizationJobRepository jobRepository;
    private final VideoRepository videoRepository;
    private final JobTimelineEventRepository timelineEventRepository;
    private final JobDispatchOutboxService dispatchOutboxService;
    private final LocalizationJobQueryService queryService;
    private final LocalizationJobStatusCacheService jobStatusCacheService;
    private final LinguaFrameProperties properties;
    private final Clock clock;

    @Autowired
    public LocalizationJobRetryServiceImpl(
            LocalizationJobRepository jobRepository,
            VideoRepository videoRepository,
            JobTimelineEventRepository timelineEventRepository,
            JobDispatchOutboxService dispatchOutboxService,
            LocalizationJobQueryService queryService,
            LocalizationJobStatusCacheService jobStatusCacheService,
            LinguaFrameProperties properties
    ) {
        this(
                jobRepository,
                videoRepository,
                timelineEventRepository,
                dispatchOutboxService,
                queryService,
                jobStatusCacheService,
                properties,
                Clock.systemUTC()
        );
    }

    public LocalizationJobRetryServiceImpl(
            LocalizationJobRepository jobRepository,
            VideoRepository videoRepository,
            JobTimelineEventRepository timelineEventRepository,
            JobDispatchOutboxService dispatchOutboxService,
            LocalizationJobQueryService queryService,
            LocalizationJobStatusCacheService jobStatusCacheService,
            LinguaFrameProperties properties,
            Clock clock
    ) {
        this.jobRepository = jobRepository;
        this.videoRepository = videoRepository;
        this.timelineEventRepository = timelineEventRepository;
        this.dispatchOutboxService = dispatchOutboxService;
        this.queryService = queryService;
        this.jobStatusCacheService = jobStatusCacheService;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public LocalizationJobVo retryFailedJob(String jobId) {
        LocalizationJobRecord job = jobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Localization job not found."));
        if (job.status() != LocalizationJobStatus.FAILED) {
            throw new JobStateConflictException("Only failed localization jobs can be retried.");
        }
        if (job.retryCount() >= properties.getWorker().getMaxRetries()) {
            throw new JobStateConflictException("Retry limit reached for this localization job.");
        }

        VideoRecord video = videoRepository.findById(job.videoId())
                .orElseThrow(() -> new NoSuchElementException("Source video not found."));
        Instant now = Instant.now(clock);
        if (!jobRepository.markRetrying(jobId, now)) {
            throw new JobStateConflictException("Only failed localization jobs can be retried.");
        }

        LocalizationJobRecord retryingJob = jobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Localization job not found."));
        timelineEventRepository.save(new JobTimelineEventRecord(
                UUID.randomUUID().toString(),
                jobId,
                LocalizationJobStage.WORKER_RECEIVED,
                JobTimelineEventStatus.STARTED,
                "Retry requested.",
                null,
                null,
                now
        ));
        dispatchOutboxService.enqueueLocalizationJobQueued(video, retryingJob);
        evictCachedJob(jobId);
        return queryService.getJob(jobId);
    }

    private void evictCachedJob(String jobId) {
        try {
            jobStatusCacheService.evict(jobId);
        } catch (RuntimeException exception) {
            // Cache eviction is best-effort and must not roll back retry.
        }
    }
}
