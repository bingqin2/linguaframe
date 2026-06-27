package com.linguaframe.job.service;

import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.repository.JobArtifactRepository;
import com.linguaframe.job.repository.JobDispatchEventRepository;
import com.linguaframe.job.repository.JobTimelineEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.impl.LocalizationJobQueryServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalizationJobQueryServiceTests {

    private final LocalizationJobRepository jobRepository = mock(LocalizationJobRepository.class);
    private final JobArtifactRepository artifactRepository = mock(JobArtifactRepository.class);
    private final JobDispatchEventRepository dispatchEventRepository = mock(JobDispatchEventRepository.class);
    private final JobTimelineEventRepository timelineEventRepository = mock(JobTimelineEventRepository.class);
    private final ModelCallAuditService modelCallAuditService = mock(ModelCallAuditService.class);
    private final QualityEvaluationService qualityEvaluationService = mock(QualityEvaluationService.class);
    private final LocalizationJobStatusCacheService cacheService = mock(LocalizationJobStatusCacheService.class);

    @Test
    void getJobReturnsCachedSnapshotWithoutRepositoryReads() {
        LocalizationJobVo cachedJob = job("job-query-cache-hit", LocalizationJobStatus.PROCESSING);
        when(cacheService.get("job-query-cache-hit")).thenReturn(Optional.of(cachedJob));
        LocalizationJobQueryServiceImpl service = service();

        LocalizationJobVo result = service.getJob("job-query-cache-hit");

        assertThat(result).isEqualTo(cachedJob);
        verify(jobRepository, never()).findById("job-query-cache-hit");
        verify(cacheService, never()).put(cachedJob);
    }

    @Test
    void getJobCachesSnapshotAfterDatabaseReadOnMiss() {
        Instant createdAt = Instant.parse("2026-06-27T05:30:00Z");
        when(cacheService.get("job-query-cache-miss")).thenReturn(Optional.empty());
        when(jobRepository.findById("job-query-cache-miss")).thenReturn(Optional.of(new LocalizationJobRecord(
                "job-query-cache-miss",
                "video-query-cache-miss",
                "zh-CN",
                LocalizationJobStatus.QUEUED,
                createdAt
        )));
        when(dispatchEventRepository.findLatestByJobId("job-query-cache-miss")).thenReturn(Optional.empty());
        when(artifactRepository.findByJobId("job-query-cache-miss")).thenReturn(List.of());
        when(timelineEventRepository.findByJobId("job-query-cache-miss")).thenReturn(List.of());
        when(modelCallAuditService.summarizeJob("job-query-cache-miss")).thenReturn(emptyUsage());
        when(modelCallAuditService.listModelCalls("job-query-cache-miss")).thenReturn(List.of());
        when(qualityEvaluationService.latestForJob("job-query-cache-miss")).thenReturn(Optional.empty());
        LocalizationJobQueryServiceImpl service = service();

        LocalizationJobVo result = service.getJob("job-query-cache-miss");

        assertThat(result.jobId()).isEqualTo("job-query-cache-miss");
        assertThat(result.status()).isEqualTo(LocalizationJobStatus.QUEUED);
        verify(cacheService).put(result);
    }

    @Test
    void cacheReadFailureFallsBackToDatabaseReadAndCachesFreshSnapshot() {
        Instant createdAt = Instant.parse("2026-06-27T05:30:00Z");
        when(cacheService.get("job-query-cache-error")).thenThrow(new IllegalStateException("cache unavailable"));
        when(jobRepository.findById("job-query-cache-error")).thenReturn(Optional.of(new LocalizationJobRecord(
                "job-query-cache-error",
                "video-query-cache-error",
                "zh-CN",
                LocalizationJobStatus.PROCESSING,
                createdAt
        )));
        when(dispatchEventRepository.findLatestByJobId("job-query-cache-error")).thenReturn(Optional.empty());
        when(artifactRepository.findByJobId("job-query-cache-error")).thenReturn(List.of());
        when(timelineEventRepository.findByJobId("job-query-cache-error")).thenReturn(List.of());
        when(modelCallAuditService.summarizeJob("job-query-cache-error")).thenReturn(emptyUsage());
        when(modelCallAuditService.listModelCalls("job-query-cache-error")).thenReturn(List.of());
        when(qualityEvaluationService.latestForJob("job-query-cache-error")).thenReturn(Optional.empty());
        LocalizationJobQueryServiceImpl service = service();

        LocalizationJobVo result = service.getJob("job-query-cache-error");

        assertThat(result.jobId()).isEqualTo("job-query-cache-error");
        assertThat(result.status()).isEqualTo(LocalizationJobStatus.PROCESSING);
        verify(cacheService).put(result);
    }

    @Test
    void cacheWriteFailureDoesNotBreakJobRead() {
        Instant createdAt = Instant.parse("2026-06-27T05:30:00Z");
        when(cacheService.get("job-query-cache-write-error")).thenReturn(Optional.empty());
        when(jobRepository.findById("job-query-cache-write-error")).thenReturn(Optional.of(new LocalizationJobRecord(
                "job-query-cache-write-error",
                "video-query-cache-write-error",
                "zh-CN",
                LocalizationJobStatus.COMPLETED,
                createdAt
        )));
        when(dispatchEventRepository.findLatestByJobId("job-query-cache-write-error")).thenReturn(Optional.empty());
        when(artifactRepository.findByJobId("job-query-cache-write-error")).thenReturn(List.of());
        when(timelineEventRepository.findByJobId("job-query-cache-write-error")).thenReturn(List.of());
        when(modelCallAuditService.summarizeJob("job-query-cache-write-error")).thenReturn(emptyUsage());
        when(modelCallAuditService.listModelCalls("job-query-cache-write-error")).thenReturn(List.of());
        when(qualityEvaluationService.latestForJob("job-query-cache-write-error")).thenReturn(Optional.empty());
        LocalizationJobQueryServiceImpl service = service();
        doThrow(new IllegalStateException("cache unavailable")).when(cacheService).put(org.mockito.Mockito.any());

        LocalizationJobVo result = service.getJob("job-query-cache-write-error");

        assertThat(result.jobId()).isEqualTo("job-query-cache-write-error");
        assertThat(result.status()).isEqualTo(LocalizationJobStatus.COMPLETED);
    }

    private LocalizationJobQueryServiceImpl service() {
        return new LocalizationJobQueryServiceImpl(
                jobRepository,
                artifactRepository,
                dispatchEventRepository,
                timelineEventRepository,
                modelCallAuditService,
                qualityEvaluationService,
                cacheService
        );
    }

    private LocalizationJobVo job(String jobId, LocalizationJobStatus status) {
        return new LocalizationJobVo(
                jobId,
                "video-" + jobId,
                "zh-CN",
                status,
                Instant.parse("2026-06-27T05:30:00Z"),
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                0,
                null,
                List.of(),
                emptyUsage(),
                new JobCacheSummaryVo(0, 0, 0),
                List.of(),
                null
        );
    }

    private JobUsageSummaryVo emptyUsage() {
        return new JobUsageSummaryVo(0, 0, 0, BigDecimal.ZERO, null, null, null, null);
    }
}
