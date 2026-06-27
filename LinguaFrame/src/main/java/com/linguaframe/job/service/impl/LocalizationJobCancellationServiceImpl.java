package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.exception.JobStateConflictException;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.repository.JobTimelineEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.LocalizationJobCancellationService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.job.service.LocalizationJobStatusCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class LocalizationJobCancellationServiceImpl implements LocalizationJobCancellationService {

    private static final String CANCELLATION_CONFLICT_MESSAGE =
            "Only queued, retrying, or processing localization jobs can be cancelled.";

    private final LocalizationJobRepository jobRepository;
    private final JobTimelineEventRepository timelineEventRepository;
    private final LocalizationJobQueryService queryService;
    private final LocalizationJobStatusCacheService jobStatusCacheService;
    private final Clock clock;

    @Autowired
    public LocalizationJobCancellationServiceImpl(
            LocalizationJobRepository jobRepository,
            JobTimelineEventRepository timelineEventRepository,
            LocalizationJobQueryService queryService,
            LocalizationJobStatusCacheService jobStatusCacheService
    ) {
        this(jobRepository, timelineEventRepository, queryService, jobStatusCacheService, Clock.systemUTC());
    }

    public LocalizationJobCancellationServiceImpl(
            LocalizationJobRepository jobRepository,
            JobTimelineEventRepository timelineEventRepository,
            LocalizationJobQueryService queryService,
            LocalizationJobStatusCacheService jobStatusCacheService,
            Clock clock
    ) {
        this.jobRepository = jobRepository;
        this.timelineEventRepository = timelineEventRepository;
        this.queryService = queryService;
        this.jobStatusCacheService = jobStatusCacheService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public LocalizationJobVo cancelJob(String jobId) {
        jobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Localization job not found."));
        Instant now = Instant.now(clock);
        if (!jobRepository.markCancelled(jobId, now)) {
            throw new JobStateConflictException(CANCELLATION_CONFLICT_MESSAGE);
        }

        timelineEventRepository.save(new JobTimelineEventRecord(
                UUID.randomUUID().toString(),
                jobId,
                LocalizationJobStage.WORKER_RECEIVED,
                JobTimelineEventStatus.SKIPPED,
                "Cancellation requested.",
                null,
                null,
                now
        ));
        evictCachedJob(jobId);
        return queryService.getJob(jobId);
    }

    private void evictCachedJob(String jobId) {
        try {
            jobStatusCacheService.evict(jobId);
        } catch (RuntimeException exception) {
            // Cache eviction is best-effort and must not roll back cancellation.
        }
    }
}
