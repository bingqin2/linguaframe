package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.entity.JobDispatchEventRecord;
import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.vo.JobTimelineEventVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.repository.JobDispatchEventRepository;
import com.linguaframe.job.repository.JobTimelineEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.job.service.ModelCallAuditService;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class LocalizationJobQueryServiceImpl implements LocalizationJobQueryService {

    private final LocalizationJobRepository jobRepository;
    private final JobDispatchEventRepository dispatchEventRepository;
    private final JobTimelineEventRepository timelineEventRepository;
    private final ModelCallAuditService modelCallAuditService;

    public LocalizationJobQueryServiceImpl(
            LocalizationJobRepository jobRepository,
            JobDispatchEventRepository dispatchEventRepository,
            JobTimelineEventRepository timelineEventRepository,
            ModelCallAuditService modelCallAuditService
    ) {
        this.jobRepository = jobRepository;
        this.dispatchEventRepository = dispatchEventRepository;
        this.timelineEventRepository = timelineEventRepository;
        this.modelCallAuditService = modelCallAuditService;
    }

    @Override
    public LocalizationJobVo getJob(String jobId) {
        LocalizationJobRecord record = jobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Localization job not found."));
        JobDispatchEventRecord dispatchEvent = dispatchEventRepository.findLatestByJobId(jobId).orElse(null);
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
                modelCallAuditService.listModelCalls(jobId)
        );
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
}
