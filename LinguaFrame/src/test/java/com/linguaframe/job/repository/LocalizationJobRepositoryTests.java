package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class LocalizationJobRepositoryTests {

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private LocalizationJobRepository jobRepository;

    @Test
    void savesAndFindsLocalizationJobRecord() {
        Instant createdAt = Instant.parse("2026-06-25T15:00:00Z");
        videoRepository.save(new VideoRecord(
                "video-job-1",
                "sample.mp4",
                "video/mp4",
                123L,
                "source-videos/video-job-1/sample.mp4",
                MediaUploadStatus.UPLOADED,
                createdAt
        ));
        LocalizationJobRecord record = new LocalizationJobRecord(
                "job-1",
                "video-job-1",
                "zh-CN",
                LocalizationJobStatus.QUEUED,
                createdAt
        );

        jobRepository.save(record);

        Optional<LocalizationJobRecord> found = jobRepository.findById("job-1");

        assertThat(found).contains(record);
    }

    @Test
    void returnsEmptyWhenJobDoesNotExist() {
        assertThat(jobRepository.findById("missing-job")).isEmpty();
    }
}
