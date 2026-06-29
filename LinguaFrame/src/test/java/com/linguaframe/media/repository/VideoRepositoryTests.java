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
                "b94d27b9934d3e08a52e52d7da7dabfadeadbeef000000000000000000000000",
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
        assertThat(found).get().extracting(VideoRecord::sourceContentSha256)
                .isEqualTo("b94d27b9934d3e08a52e52d7da7dabfadeadbeef000000000000000000000000");
    }

    @Test
    void returnsEmptyWhenVideoDoesNotExist() {
        assertThat(videoRepository.findById("missing-video")).isEmpty();
    }

    @Test
    void findsRecentVideosByOwnerAndSourceFingerprint() {
        String fingerprint = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        videoRepository.save(new VideoRecord(
                "video-old",
                "owner-alpha",
                "old.mp4",
                "video/mp4",
                100L,
                10,
                fingerprint,
                "source-videos/video-old/old.mp4",
                MediaUploadStatus.UPLOADED,
                Instant.parse("2026-06-25T10:00:00Z")
        ));
        videoRepository.save(new VideoRecord(
                "video-new",
                "owner-alpha",
                "new.mp4",
                "video/mp4",
                100L,
                10,
                fingerprint,
                "source-videos/video-new/new.mp4",
                MediaUploadStatus.UPLOADED,
                Instant.parse("2026-06-25T11:00:00Z")
        ));
        videoRepository.save(new VideoRecord(
                "video-other-owner",
                "owner-beta",
                "other.mp4",
                "video/mp4",
                100L,
                10,
                fingerprint,
                "source-videos/video-other-owner/other.mp4",
                MediaUploadStatus.UPLOADED,
                Instant.parse("2026-06-25T12:00:00Z")
        ));

        assertThat(videoRepository.findRecentByOwnerIdAndSourceContentSha256("owner-alpha", fingerprint, 10))
                .extracting(VideoRecord::id)
                .containsExactly("video-new", "video-old");
        assertThat(videoRepository.findRecentByOwnerIdAndSourceContentSha256("owner-alpha", fingerprint, 1))
                .extracting(VideoRecord::id)
                .containsExactly("video-new");
        assertThat(videoRepository.findRecentByOwnerIdAndSourceContentSha256("owner-alpha", null, 10)).isEmpty();
    }
}
