package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.entity.QualityEvaluationRecord;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.QualityEvaluationStatus;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class QualityEvaluationRepositoryTests {

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private LocalizationJobRepository jobRepository;

    @Autowired
    private QualityEvaluationRepository qualityEvaluationRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM quality_evaluations").update();
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
    void savesAndFindsLatestEvaluationByJobAndLanguage() {
        Instant createdAt = Instant.parse("2026-06-27T10:00:00Z");
        createJob("quality-video-1", "quality-job-1", createdAt);
        createJob("quality-video-2", "quality-job-2", createdAt);
        QualityEvaluationRecord older = record(
                "quality-evaluation-older",
                "quality-job-1",
                "zh-CN",
                83,
                "NEEDS_REVIEW",
                List.of("One subtitle line is too literal."),
                List.of("Use a more natural expression."),
                QualityEvaluationStatus.SUCCEEDED,
                null,
                createdAt.plusSeconds(1)
        );
        QualityEvaluationRecord newer = record(
                "quality-evaluation-newer",
                "quality-job-1",
                "zh-CN",
                92,
                "GOOD",
                List.of("No blocking issues."),
                List.of("Keep timing as generated."),
                QualityEvaluationStatus.SUCCEEDED,
                null,
                createdAt.plusSeconds(2)
        );
        QualityEvaluationRecord otherLanguage = record(
                "quality-evaluation-other-language",
                "quality-job-1",
                "ja",
                88,
                "GOOD",
                List.of("Japanese subtitle is readable."),
                List.of("Review honorific tone."),
                QualityEvaluationStatus.SUCCEEDED,
                null,
                createdAt.plusSeconds(3)
        );
        QualityEvaluationRecord otherJob = record(
                "quality-evaluation-other-job",
                "quality-job-2",
                "zh-CN",
                50,
                "FAILED",
                List.of(),
                List.of(),
                QualityEvaluationStatus.FAILED,
                "OpenAI quality evaluation request failed with status 500",
                createdAt.plusSeconds(4)
        );

        qualityEvaluationRepository.save(newer);
        qualityEvaluationRepository.save(older);
        qualityEvaluationRepository.save(otherLanguage);
        qualityEvaluationRepository.save(otherJob);

        assertThat(qualityEvaluationRepository.findLatestByJobIdAndLanguage("quality-job-1", "zh-CN"))
                .contains(newer);
        assertThat(qualityEvaluationRepository.findByJobId("quality-job-1"))
                .containsExactly(older, newer, otherLanguage);
        assertThat(qualityEvaluationRepository.findLatestByJobIdAndLanguage("missing-job", "zh-CN"))
                .isEmpty();
    }

    private QualityEvaluationRecord record(
            String id,
            String jobId,
            String language,
            int score,
            String verdict,
            List<String> issues,
            List<String> suggestedFixes,
            QualityEvaluationStatus status,
            String safeErrorSummary,
            Instant createdAt
    ) {
        return new QualityEvaluationRecord(
                id,
                jobId,
                language,
                score,
                verdict,
                score - 1,
                score - 2,
                score - 3,
                score - 4,
                issues,
                suggestedFixes,
                status,
                safeErrorSummary,
                createdAt
        );
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
}
