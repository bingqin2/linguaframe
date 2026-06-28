package com.linguaframe.media.repository;

import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class VideoRepositoryTests {

    @Autowired
    private VideoRepository videoRepository;

    @Test
    void savesAndFindsVideoRecord() {
        Instant createdAt = Instant.parse("2026-06-25T15:00:00Z");
        VideoRecord record = new VideoRecord(
                "video-1",
                "owner-alpha",
                "sample.mp4",
                "video/mp4",
                123L,
                42,
                "source-videos/video-1/sample.mp4",
                MediaUploadStatus.UPLOADED,
                createdAt
        );

        videoRepository.save(record);

        Optional<VideoRecord> found = videoRepository.findById("video-1");

        assertThat(found).contains(record);
        assertThat(videoRepository.findByIdAndOwnerId("video-1", "owner-alpha")).contains(record);
        assertThat(videoRepository.findByIdAndOwnerId("video-1", "owner-beta")).isEmpty();
        assertThat(found).get().extracting(VideoRecord::durationSeconds).isEqualTo(42);
    }

    @Test
    void returnsEmptyWhenVideoDoesNotExist() {
        assertThat(videoRepository.findById("missing-video")).isEmpty();
    }
}
