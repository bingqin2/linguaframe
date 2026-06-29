package com.linguaframe.media.service;

import com.linguaframe.common.security.DemoOwnerIdentityService;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.media.domain.bo.UploadCostEstimateOptionsBo;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.domain.vo.UploadCostEstimateVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseVo;
import com.linguaframe.media.repository.VideoRepository;
import com.linguaframe.media.service.impl.UploadSourceReuseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class UploadSourceReuseServiceTests {

    private static final String FINGERPRINT = "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81";

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private LocalizationJobRepository jobRepository;

    @Autowired
    private SourceMediaFingerprintService fingerprintService;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM model_call_records").update();
        jdbcClient.sql("DELETE FROM subtitle_segments").update();
        jdbcClient.sql("DELETE FROM transcript_segments").update();
        jdbcClient.sql("DELETE FROM job_artifacts").update();
        jdbcClient.sql("DELETE FROM job_timeline_events").update();
        jdbcClient.sql("DELETE FROM job_dispatch_events").update();
        jdbcClient.sql("DELETE FROM localization_jobs").update();
        jdbcClient.sql("DELETE FROM videos").update();
    }

    @Test
    void recommendsCompletedSameOwnerRunForDuplicateSource() {
        UploadSourceReuseService service = serviceForOwner("owner-alpha");
        insertVideoAndJob("video-active", "job-active", "owner-alpha", LocalizationJobStatus.PROCESSING, "NATURAL", Instant.parse("2026-06-28T12:00:00Z"));
        insertVideoAndJob("video-completed", "job-completed", "owner-alpha", LocalizationJobStatus.COMPLETED, "FORMAL", Instant.parse("2026-06-28T11:00:00Z"));
        insertVideoAndJob("video-other-owner", "job-other-owner", "owner-beta", LocalizationJobStatus.COMPLETED, "FORMAL", Instant.parse("2026-06-28T13:00:00Z"));

        UploadSourceReuseVo reuse = service.evaluate(videoFile(), readyCostEstimate(), UploadCostEstimateOptionsBo.empty());

        assertThat(reuse.sourceContentSha256()).isEqualTo(FINGERPRINT);
        assertThat(reuse.candidateCount()).isEqualTo(2);
        assertThat(reuse.recommendedAction()).isEqualTo("REVIEW_EXISTING_COMPLETED_RUN");
        assertThat(reuse.recommendedExistingJobId()).isEqualTo("job-completed");
        assertThat(reuse.candidates())
                .extracting("jobId")
                .containsExactly("job-active", "job-completed");
    }

    @Test
    void recommendsWaitingForActiveRunWhenNoCompletedMatchExists() {
        UploadSourceReuseService service = serviceForOwner("owner-alpha");
        insertVideoAndJob("video-active", "job-active", "owner-alpha", LocalizationJobStatus.QUEUED, "FORMAL", Instant.parse("2026-06-28T12:00:00Z"));

        UploadSourceReuseVo reuse = service.evaluate(videoFile(), readyCostEstimate(), UploadCostEstimateOptionsBo.empty());

        assertThat(reuse.recommendedAction()).isEqualTo("WAIT_FOR_ACTIVE_RUN");
        assertThat(reuse.recommendedExistingJobId()).isEqualTo("job-active");
    }

    @Test
    void skipsFingerprintAndCandidatesForInvalidUploadEstimate() {
        UploadSourceReuseService service = serviceForOwner("owner-alpha");

        UploadSourceReuseVo reuse = service.evaluate(videoFile(), invalidCostEstimate(), UploadCostEstimateOptionsBo.empty());

        assertThat(reuse.sourceContentSha256()).isNull();
        assertThat(reuse.candidateCount()).isZero();
        assertThat(reuse.recommendedAction()).isEqualTo("UPLOAD_NEW_SOURCE");
    }

    private UploadSourceReuseService serviceForOwner(String ownerId) {
        DemoOwnerIdentityService ownerIdentityService = () -> ownerId;
        return new UploadSourceReuseServiceImpl(fingerprintService, videoRepository, jobRepository, ownerIdentityService);
    }

    private void insertVideoAndJob(
            String videoId,
            String jobId,
            String ownerId,
            LocalizationJobStatus status,
            String translationStyle,
            Instant createdAt
    ) {
        videoRepository.save(new VideoRecord(
                videoId,
                ownerId,
                videoId + ".mp4",
                "video/mp4",
                3,
                90,
                FINGERPRINT,
                "source-videos/" + videoId + "/" + videoId + ".mp4",
                MediaUploadStatus.UPLOADED,
                createdAt
        ));
        jobRepository.save(new LocalizationJobRecord(
                jobId,
                videoId,
                ownerId,
                "zh-CN",
                null,
                translationStyle,
                "HIGH_CONTRAST",
                "[]",
                "",
                0,
                "BALANCED",
                "tears-showcase",
                status,
                createdAt
        ));
    }

    private static MockMultipartFile videoFile() {
        return new MockMultipartFile("file", "sample.mp4", "video/mp4", new byte[] {1, 2, 3});
    }

    private static UploadCostEstimateVo readyCostEstimate() {
        return estimate(true, "READY", "File is ready for upload.");
    }

    private static UploadCostEstimateVo invalidCostEstimate() {
        return estimate(false, "DURATION_TOO_LONG", "The uploaded video exceeds the 300 second duration limit.");
    }

    private static UploadCostEstimateVo estimate(boolean valid, String validationCode, String validationMessage) {
        return new UploadCostEstimateVo(
                valid ? "READY" : "BLOCKED",
                valid ? "Upload can proceed with the selected profile and options." : "Replace the source video.",
                "sample.mp4",
                "video/mp4",
                3,
                104857600,
                valid ? 90 : 301,
                300,
                valid,
                validationCode,
                validationMessage,
                "zh-CN",
                null,
                "FORMAL",
                "HIGH_CONTRAST",
                0,
                "",
                "BALANCED",
                "tears-showcase",
                BigDecimal.ZERO.setScale(8),
                BigDecimal.ZERO.setScale(8),
                BigDecimal.ZERO.setScale(8),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
