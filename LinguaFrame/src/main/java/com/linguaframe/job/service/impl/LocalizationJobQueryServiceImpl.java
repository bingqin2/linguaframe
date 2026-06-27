package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.entity.JobDispatchEventRecord;
import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsArtifactVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.JobTimelineEventVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.repository.JobArtifactRepository;
import com.linguaframe.job.repository.JobDispatchEventRepository;
import com.linguaframe.job.repository.JobTimelineEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.job.service.LocalizationJobStatusCacheService;
import com.linguaframe.job.service.ModelCallAuditService;
import com.linguaframe.job.service.QualityEvaluationService;
import com.linguaframe.job.service.FailureTriageService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class LocalizationJobQueryServiceImpl implements LocalizationJobQueryService {

    private static final int DEFAULT_LIST_LIMIT = 20;
    private static final int MAX_LIST_LIMIT = 100;

    private final LocalizationJobRepository jobRepository;
    private final JobArtifactRepository artifactRepository;
    private final JobDispatchEventRepository dispatchEventRepository;
    private final JobTimelineEventRepository timelineEventRepository;
    private final ModelCallAuditService modelCallAuditService;
    private final QualityEvaluationService qualityEvaluationService;
    private final LocalizationJobStatusCacheService jobStatusCacheService;
    private final FailureTriageService failureTriageService;

    public LocalizationJobQueryServiceImpl(
            LocalizationJobRepository jobRepository,
            JobArtifactRepository artifactRepository,
            JobDispatchEventRepository dispatchEventRepository,
            JobTimelineEventRepository timelineEventRepository,
            ModelCallAuditService modelCallAuditService,
            QualityEvaluationService qualityEvaluationService,
            LocalizationJobStatusCacheService jobStatusCacheService,
            FailureTriageService failureTriageService
    ) {
        this.jobRepository = jobRepository;
        this.artifactRepository = artifactRepository;
        this.dispatchEventRepository = dispatchEventRepository;
        this.timelineEventRepository = timelineEventRepository;
        this.modelCallAuditService = modelCallAuditService;
        this.qualityEvaluationService = qualityEvaluationService;
        this.jobStatusCacheService = jobStatusCacheService;
        this.failureTriageService = failureTriageService;
    }

    @Override
    public LocalizationJobListVo listJobs(LocalizationJobStatus status, Integer limit, Integer offset) {
        int normalizedLimit = normalizeLimit(limit);
        int normalizedOffset = normalizeOffset(offset);
        return new LocalizationJobListVo(
                jobRepository.findSummaries(status, normalizedLimit, normalizedOffset),
                normalizedLimit,
                normalizedOffset,
                jobRepository.countSummaries(status)
        );
    }

    @Override
    public LocalizationJobVo getJob(String jobId) {
        Optional<LocalizationJobVo> cachedJob = cachedJob(jobId);
        if (cachedJob.isPresent()) {
            return cachedJob.get();
        }

        LocalizationJobRecord record = jobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Localization job not found."));
        JobDispatchEventRecord dispatchEvent = dispatchEventRepository.findLatestByJobId(jobId).orElse(null);
        List<JobArtifactRecord> artifacts = artifactRepository.findByJobId(jobId);
        List<JobTimelineEventRecord> timelineEvents = timelineEventRepository.findByJobId(jobId);
        var modelCalls = modelCallAuditService.listModelCalls(jobId);
        LocalizationJobVo job = new LocalizationJobVo(
                record.id(),
                record.videoId(),
                record.targetLanguage(),
                record.ttsVoice(),
                record.status(),
                record.createdAt(),
                record.startedAt(),
                record.completedAt(),
                record.failedAt(),
                record.failureStage(),
                record.failureReason(),
                record.retryCount(),
                dispatchEvent == null ? null : dispatchEvent.status(),
                dispatchEvent == null ? 0 : dispatchEvent.attempts(),
                dispatchEvent == null ? null : dispatchEvent.dispatchedAt(),
                timelineEvents.stream()
                        .map(this::toTimelineEventVo)
                        .toList(),
                modelCallAuditService.summarizeJob(jobId),
                cacheSummary(artifacts, timelineEvents),
                modelCalls,
                qualityEvaluationService.latestForJob(jobId).orElse(null),
                failureTriageService.triage(record, timelineEvents, modelCalls)
        );
        cacheJob(job);
        return job;
    }

    @Override
    public JobDiagnosticsReportVo getDiagnosticsReport(String jobId) {
        LocalizationJobVo job = getJob(jobId);
        List<JobDiagnosticsArtifactVo> artifacts = artifactRepository.findByJobId(jobId).stream()
                .map(this::toDiagnosticsArtifactVo)
                .toList();
        return new JobDiagnosticsReportVo(
                Instant.now(),
                job,
                artifacts,
                artifacts.size()
        );
    }

    private Optional<LocalizationJobVo> cachedJob(String jobId) {
        try {
            return jobStatusCacheService.get(jobId);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private void cacheJob(LocalizationJobVo job) {
        try {
            jobStatusCacheService.put(job);
        } catch (RuntimeException exception) {
            // Job detail reads must not depend on Redis availability.
        }
    }

    private JobCacheSummaryVo cacheSummary(
            List<JobArtifactRecord> artifacts,
            List<JobTimelineEventRecord> timelineEvents
    ) {
        int cacheHitCount = (int) artifacts.stream()
                .filter(JobArtifactRecord::cacheHit)
                .count();
        int providerCacheHitCount = (int) timelineEvents.stream()
                .filter(event -> event.status() == JobTimelineEventStatus.CACHE_HIT)
                .filter(event -> event.message() != null && event.message().contains("provider result"))
                .count();
        return new JobCacheSummaryVo(cacheHitCount, artifacts.size() - cacheHitCount, providerCacheHitCount);
    }

    private JobTimelineEventVo toTimelineEventVo(JobTimelineEventRecord record) {
        return new JobTimelineEventVo(
                record.id(),
                record.stage(),
                record.status(),
                record.message(),
                record.durationMs(),
                record.errorSummary(),
                record.occurredAt()
        );
    }

    private JobDiagnosticsArtifactVo toDiagnosticsArtifactVo(JobArtifactRecord record) {
        return new JobDiagnosticsArtifactVo(
                record.id(),
                record.type(),
                record.filename(),
                record.contentType(),
                record.sizeBytes(),
                record.contentSha256(),
                record.cacheHit(),
                record.sourceArtifactId(),
                record.createdAt()
        );
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1 || limit > MAX_LIST_LIMIT) {
            return DEFAULT_LIST_LIMIT;
        }
        return limit;
    }

    private int normalizeOffset(Integer offset) {
        if (offset == null || offset < 0) {
            return 0;
        }
        return offset;
    }
}
