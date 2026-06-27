package com.linguaframe.job.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.exception.JobStateConflictException;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.repository.JobTimelineEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.impl.LocalizationJobRetryServiceImpl;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.repository.VideoRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalizationJobRetryServiceTests {

    @Test
    void evictsCachedJobSnapshotBeforeReturningRetriedJob() {
        LocalizationJobRepository jobRepository = mock(LocalizationJobRepository.class);
        VideoRepository videoRepository = mock(VideoRepository.class);
        JobTimelineEventRepository timelineEventRepository = mock(JobTimelineEventRepository.class);
        JobDispatchOutboxService dispatchOutboxService = mock(JobDispatchOutboxService.class);
        LocalizationJobQueryService queryService = mock(LocalizationJobQueryService.class);
        LocalizationJobStatusCacheService cacheService = mock(LocalizationJobStatusCacheService.class);
        Instant now = Instant.parse("2026-06-27T06:00:00Z");
        LocalizationJobRecord failedJob = new LocalizationJobRecord(
                "retry-cache-job",
                "retry-cache-video",
                "zh-CN",
                LocalizationJobStatus.FAILED,
                now
        );
        LocalizationJobRecord retryingJob = new LocalizationJobRecord(
                "retry-cache-job",
                "retry-cache-video",
                "zh-CN",
                null,
                LocalizationJobStatus.RETRYING,
                now
        );
        VideoRecord video = new VideoRecord(
                "retry-cache-video",
                "sample.mp4",
                "video/mp4",
                123L,
                "source-videos/retry-cache-video/sample.mp4",
                MediaUploadStatus.UPLOADED,
                now
        );
        LocalizationJobVo retried = new LocalizationJobVo(
                "retry-cache-job",
                "retry-cache-video",
                "zh-CN",
                null,
                LocalizationJobStatus.RETRYING,
                now,
                null,
                null,
                null,
                null,
                null,
                1,
                null,
                0,
                null,
                java.util.List.of(),
                null,
                null,
                java.util.List.of(),
                null,
                null
        );
        when(jobRepository.findById("retry-cache-job"))
                .thenReturn(Optional.of(failedJob))
                .thenReturn(Optional.of(retryingJob));
        when(videoRepository.findById("retry-cache-video")).thenReturn(Optional.of(video));
        when(jobRepository.markRetrying("retry-cache-job", now.plusSeconds(1))).thenReturn(true);
        when(queryService.getJob("retry-cache-job")).thenReturn(retried);
        LocalizationJobRetryServiceImpl service = new LocalizationJobRetryServiceImpl(
                jobRepository,
                videoRepository,
                timelineEventRepository,
                dispatchOutboxService,
                queryService,
                cacheService,
                propertiesWithMaxRetries(2),
                Clock.fixed(now.plusSeconds(1), ZoneOffset.UTC)
        );

        LocalizationJobVo result = service.retryFailedJob("retry-cache-job");

        assertThat(result.status()).isEqualTo(LocalizationJobStatus.RETRYING);
        verify(cacheService).evict("retry-cache-job");
    }

    @Test
    void rejectsRetryWhenRetryCountHasReachedConfiguredLimit() {
        LocalizationJobRepository jobRepository = mock(LocalizationJobRepository.class);
        VideoRepository videoRepository = mock(VideoRepository.class);
        JobTimelineEventRepository timelineEventRepository = mock(JobTimelineEventRepository.class);
        JobDispatchOutboxService dispatchOutboxService = mock(JobDispatchOutboxService.class);
        LocalizationJobQueryService queryService = mock(LocalizationJobQueryService.class);
        LocalizationJobStatusCacheService cacheService = mock(LocalizationJobStatusCacheService.class);
        Instant now = Instant.parse("2026-06-27T06:30:00Z");
        LocalizationJobRecord failedJobAtLimit = new LocalizationJobRecord(
                "retry-limit-job",
                "retry-limit-video",
                "zh-CN",
                LocalizationJobStatus.FAILED,
                now.minusSeconds(120),
                now.minusSeconds(110),
                null,
                now.minusSeconds(60),
                null,
                "Forced failure",
                1,
                now.minusSeconds(60)
        );
        when(jobRepository.findById("retry-limit-job")).thenReturn(Optional.of(failedJobAtLimit));
        LocalizationJobRetryServiceImpl service = new LocalizationJobRetryServiceImpl(
                jobRepository,
                videoRepository,
                timelineEventRepository,
                dispatchOutboxService,
                queryService,
                cacheService,
                propertiesWithMaxRetries(1),
                Clock.fixed(now, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.retryFailedJob("retry-limit-job"))
                .isInstanceOf(JobStateConflictException.class)
                .hasMessage("Retry limit reached for this localization job.");
        verify(videoRepository, never()).findById("retry-limit-video");
        verify(jobRepository, never()).markRetrying("retry-limit-job", now);
        verify(dispatchOutboxService, never()).enqueueLocalizationJobQueued(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(cacheService, never()).evict("retry-limit-job");
    }

    @Test
    void allowsRetryWhenRetryCountIsBelowConfiguredLimit() {
        LocalizationJobRepository jobRepository = mock(LocalizationJobRepository.class);
        VideoRepository videoRepository = mock(VideoRepository.class);
        JobTimelineEventRepository timelineEventRepository = mock(JobTimelineEventRepository.class);
        JobDispatchOutboxService dispatchOutboxService = mock(JobDispatchOutboxService.class);
        LocalizationJobQueryService queryService = mock(LocalizationJobQueryService.class);
        LocalizationJobStatusCacheService cacheService = mock(LocalizationJobStatusCacheService.class);
        Instant now = Instant.parse("2026-06-27T06:45:00Z");
        LocalizationJobRecord failedJob = new LocalizationJobRecord(
                "retry-allowed-job",
                "retry-allowed-video",
                "zh-CN",
                LocalizationJobStatus.FAILED,
                now.minusSeconds(120)
        );
        LocalizationJobRecord retryingJob = new LocalizationJobRecord(
                "retry-allowed-job",
                "retry-allowed-video",
                "zh-CN",
                null,
                LocalizationJobStatus.RETRYING,
                now
        );
        VideoRecord video = new VideoRecord(
                "retry-allowed-video",
                "sample.mp4",
                "video/mp4",
                123L,
                "source-videos/retry-allowed-video/sample.mp4",
                MediaUploadStatus.UPLOADED,
                now.minusSeconds(120)
        );
        LocalizationJobVo retried = new LocalizationJobVo(
                "retry-allowed-job",
                "retry-allowed-video",
                "zh-CN",
                null,
                LocalizationJobStatus.RETRYING,
                now,
                null,
                null,
                null,
                null,
                null,
                1,
                null,
                0,
                null,
                java.util.List.of(),
                null,
                null,
                java.util.List.of(),
                null,
                null
        );
        when(jobRepository.findById("retry-allowed-job"))
                .thenReturn(Optional.of(failedJob))
                .thenReturn(Optional.of(retryingJob));
        when(videoRepository.findById("retry-allowed-video")).thenReturn(Optional.of(video));
        when(jobRepository.markRetrying("retry-allowed-job", now)).thenReturn(true);
        when(queryService.getJob("retry-allowed-job")).thenReturn(retried);
        LocalizationJobRetryServiceImpl service = new LocalizationJobRetryServiceImpl(
                jobRepository,
                videoRepository,
                timelineEventRepository,
                dispatchOutboxService,
                queryService,
                cacheService,
                propertiesWithMaxRetries(1),
                Clock.fixed(now, ZoneOffset.UTC)
        );

        LocalizationJobVo result = service.retryFailedJob("retry-allowed-job");

        assertThat(result.status()).isEqualTo(LocalizationJobStatus.RETRYING);
        verify(jobRepository).markRetrying("retry-allowed-job", now);
        verify(cacheService).evict("retry-allowed-job");
    }

    private static LinguaFrameProperties propertiesWithMaxRetries(int maxRetries) {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getWorker().setMaxRetries(maxRetries);
        return properties;
    }
}
