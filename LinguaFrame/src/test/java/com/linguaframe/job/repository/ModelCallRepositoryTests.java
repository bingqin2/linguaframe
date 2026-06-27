package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.entity.ModelCallRecord;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.domain.enums.ModelCallStatus;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ModelCallRepositoryTests {

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private LocalizationJobRepository jobRepository;

    @Autowired
    private ModelCallRepository modelCallRepository;

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
    void savesAndFindsModelCallsByJobOrderedByCreatedAt() {
        Instant createdAt = Instant.parse("2026-06-26T10:00:00Z");
        createJob("model-call-video-1", "model-call-job-1", createdAt);
        createJob("model-call-video-2", "model-call-job-2", createdAt);
        ModelCallRecord later = new ModelCallRecord(
                "call-1",
                "model-call-job-1",
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSLATION,
                ModelCallProvider.OPENAI,
                "gpt-test",
                "openai-subtitle-translation-v1",
                ModelCallStatus.SUCCEEDED,
                125L,
                1000,
                500,
                null,
                null,
                "target=zh-CN, segments=3, sourceChars=42",
                "segments=3, targetChars=50",
                "demo-owner",
                new BigDecimal("0.00045000"),
                null,
                createdAt.plusSeconds(2)
        );
        ModelCallRecord earlier = new ModelCallRecord(
                "call-2",
                "model-call-job-1",
                LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSCRIPTION,
                ModelCallProvider.OPENAI,
                "whisper-test",
                "openai-audio-transcriptions-v1",
                ModelCallStatus.SUCCEEDED,
                250L,
                null,
                null,
                new BigDecimal("120.000"),
                null,
                "audioSeconds=120.000",
                "segments=8, transcriptChars=320",
                "demo-owner",
                new BigDecimal("0.01200000"),
                null,
                createdAt.plusSeconds(1)
        );
        ModelCallRecord otherJob = new ModelCallRecord(
                "call-other-job",
                "model-call-job-2",
                LocalizationJobStage.DUBBING_AUDIO_GENERATION,
                ModelCallOperation.TTS,
                ModelCallProvider.DEMO,
                "demo-tts",
                "demo-tts-v1",
                ModelCallStatus.FAILED,
                10L,
                null,
                null,
                null,
                2000,
                "characters=2000",
                null,
                "demo-owner",
                new BigDecimal("0.00000000"),
                "Demo TTS failed safely.",
                createdAt.plusSeconds(3)
        );

        modelCallRepository.save(later);
        modelCallRepository.save(earlier);
        modelCallRepository.save(otherJob);

        assertThat(modelCallRepository.findByJobId("model-call-job-1"))
                .containsExactly(earlier, later);
        assertThat(modelCallRepository.findByJobId("model-call-job-1").getFirst().inputSummary())
                .isEqualTo("audioSeconds=120.000");
        assertThat(modelCallRepository.findByJobId("model-call-job-1").getFirst().outputSummary())
                .isEqualTo("segments=8, transcriptChars=320");
        assertThat(modelCallRepository.findByJobId("model-call-job-1").get(1).inputSummary())
                .isEqualTo("target=zh-CN, segments=3, sourceChars=42");
        assertThat(modelCallRepository.findByJobId("model-call-job-1").get(1).outputSummary())
                .isEqualTo("segments=3, targetChars=50");
        assertThat(modelCallRepository.findByJobId("missing-job")).isEmpty();
    }

    @Test
    void sumsEstimatedCostByBudgetIdentitySinceInstant() {
        Instant createdAt = Instant.parse("2026-06-26T10:00:00Z");
        Instant since = Instant.parse("2026-06-26T00:00:00Z");
        createJob("budget-video-1", "budget-job-1", createdAt);
        createJob("budget-video-2", "budget-job-2", createdAt);
        createJob("budget-video-3", "budget-job-3", createdAt);
        modelCallRepository.save(modelCall(
                "budget-call-current-1",
                "budget-job-1",
                "demo-owner",
                new BigDecimal("0.00010000"),
                since.plusSeconds(60)
        ));
        modelCallRepository.save(modelCall(
                "budget-call-current-2",
                "budget-job-2",
                "demo-owner",
                new BigDecimal("0.00020000"),
                since.plusSeconds(120)
        ));
        modelCallRepository.save(modelCall(
                "budget-call-old",
                "budget-job-1",
                "demo-owner",
                new BigDecimal("0.99990000"),
                since.minusSeconds(1)
        ));
        modelCallRepository.save(modelCall(
                "budget-call-other-identity",
                "budget-job-3",
                "other-demo-owner",
                new BigDecimal("0.55550000"),
                since.plusSeconds(180)
        ));

        assertThat(modelCallRepository.sumEstimatedCostByBudgetIdentitySince("demo-owner", since))
                .isEqualByComparingTo("0.00030000");
        assertThat(modelCallRepository.sumEstimatedCostByBudgetIdentitySince("missing-owner", since))
                .isEqualByComparingTo("0.00000000");
    }

    private void createJob(String videoId, String jobId, Instant createdAt) {
        videoRepository.save(new VideoRecord(
                videoId,
                "sample.mp4",
                "video/mp4",
                123L,
                "source-videos/" + videoId + "/sample.mp4",
                MediaUploadStatus.UPLOADED,
                createdAt
        ));
        jobRepository.save(new LocalizationJobRecord(
                jobId,
                videoId,
                "zh-CN",
                LocalizationJobStatus.QUEUED,
                createdAt
        ));
    }

    private ModelCallRecord modelCall(
            String id,
            String jobId,
            String budgetIdentity,
            BigDecimal estimatedCostUsd,
            Instant createdAt
    ) {
        return new ModelCallRecord(
                id,
                jobId,
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSLATION,
                ModelCallProvider.OPENAI,
                "gpt-test",
                "openai-subtitle-translation-v1",
                ModelCallStatus.SUCCEEDED,
                125L,
                1000,
                500,
                null,
                null,
                "target=zh-CN, segments=3, sourceChars=42",
                "segments=3, targetChars=50",
                budgetIdentity,
                estimatedCostUsd,
                null,
                createdAt
        );
    }
}
