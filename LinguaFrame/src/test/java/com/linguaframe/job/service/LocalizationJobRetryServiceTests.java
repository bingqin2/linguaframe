package com.linguaframe.job.service;

import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
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
import static org.mockito.Mockito.mock;
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
                Clock.fixed(now.plusSeconds(1), ZoneOffset.UTC)
        );

        LocalizationJobVo result = service.retryFailedJob("retry-cache-job");

        assertThat(result.status()).isEqualTo(LocalizationJobStatus.RETRYING);
        verify(cacheService).evict("retry-cache-job");
    }
}
