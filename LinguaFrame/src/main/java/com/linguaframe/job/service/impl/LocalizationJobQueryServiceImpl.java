package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.entity.JobDispatchEventRecord;
import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.JobTimelineEventVo;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.repository.JobArtifactRepository;
import com.linguaframe.job.repository.JobDispatchEventRepository;
import com.linguaframe.job.repository.JobTimelineEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.job.service.ModelCallAuditService;
import com.linguaframe.job.service.QualityEvaluationService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

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

    public LocalizationJobQueryServiceImpl(
            LocalizationJobRepository jobRepository,
            JobArtifactRepository artifactRepository,
            JobDispatchEventRepository dispatchEventRepository,
            JobTimelineEventRepository timelineEventRepository,
            ModelCallAuditService modelCallAuditService,
            QualityEvaluationService qualityEvaluationService
    ) {
        this.jobRepository = jobRepository;
        this.artifactRepository = artifactRepository;
        this.dispatchEventRepository = dispatchEventRepository;
        this.timelineEventRepository = timelineEventRepository;
        this.modelCallAuditService = modelCallAuditService;
        this.qualityEvaluationService = qualityEvaluationService;
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
        LocalizationJobRecord record = jobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Localization job not found."));
        JobDispatchEventRecord dispatchEvent = dispatchEventRepository.findLatestByJobId(jobId).orElse(null);
        List<JobArtifactRecord> artifacts = artifactRepository.findByJobId(jobId);
        return new LocalizationJobVo(
                record.id(),
                record.videoId(),
                record.targetLanguage(),
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
                timelineEventRepository.findByJobId(jobId).stream()
                        .map(this::toTimelineEventVo)
                        .toList(),
                modelCallAuditService.summarizeJob(jobId),
                cacheSummary(artifacts),
                modelCallAuditService.listModelCalls(jobId),
                qualityEvaluationService.latestForJob(jobId).orElse(null)
        );
    }

    private JobCacheSummaryVo cacheSummary(List<JobArtifactRecord> artifacts) {
        int cacheHitCount = (int) artifacts.stream()
                .filter(JobArtifactRecord::cacheHit)
                .count();
        return new JobCacheSummaryVo(cacheHitCount, artifacts.size() - cacheHitCount);
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
