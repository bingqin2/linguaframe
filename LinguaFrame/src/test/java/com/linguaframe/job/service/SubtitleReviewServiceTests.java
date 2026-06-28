package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.QualityEvaluationResultBo;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.bo.TranscriptionResultBo;
import com.linguaframe.job.domain.bo.TranslationResultBo;
import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.QualityEvaluationStatus;
import com.linguaframe.job.domain.enums.SubtitleReviewSegmentStatus;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.domain.vo.SubtitleReviewSegmentVo;
import com.linguaframe.job.domain.vo.SubtitleReviewSummaryVo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.service.impl.SubtitleReviewServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SubtitleReviewServiceTests {

    @Test
    void summarizesAlignedSubtitleReviewWithQualityAndArtifacts() {
        SubtitleReviewService service = service(
                List.of(
                        new TranscriptSegmentVo(0, 0L, 1_000L, "Hello."),
                        new TranscriptSegmentVo(1, 1_000L, 2_600L, "Welcome.")
                ),
                List.of(
                        new SubtitleSegmentVo("zh-CN", 0, 0L, 1_000L, "你好。"),
                        new SubtitleSegmentVo("zh-CN", 1, 1_050L, 2_650L, "欢迎。")
                ),
                Optional.of(quality()),
                List.of(
                        artifact(JobArtifactType.TARGET_SUBTITLE_JSON),
                        artifact(JobArtifactType.TARGET_SUBTITLE_SRT),
                        artifact(JobArtifactType.TARGET_SUBTITLE_VTT),
                        artifact(JobArtifactType.BURNED_VIDEO)
                )
        );

        SubtitleReviewSummaryVo result = service.buildReview("review-job", "zh-CN");

        assertThat(result.jobId()).isEqualTo("review-job");
        assertThat(result.targetLanguage()).isEqualTo("zh-CN");
        assertThat(result.segmentCount()).isEqualTo(2);
        assertThat(result.missingTargetCount()).isZero();
        assertThat(result.timingMismatchCount()).isZero();
        assertThat(result.averageDurationMs()).isEqualTo(1_300L);
        assertThat(result.maxDurationMs()).isEqualTo(1_600L);
        assertThat(result.qualityScore()).isEqualTo(92);
        assertThat(result.qualityVerdict()).isEqualTo("GOOD");
        assertThat(result.qualityIssueCount()).isEqualTo(1);
        assertThat(result.qualitySuggestedFixCount()).isEqualTo(1);
        assertThat(result.downloadableSubtitleArtifactCount()).isEqualTo(3);
        assertThat(result.segments())
                .extracting(segment -> segment.index() + ":" + segment.targetText() + ":" + segment.timingDeltaMs() + ":" + segment.status())
                .containsExactly(
                        "0:你好。:0:ALIGNED",
                        "1:欢迎。:50:ALIGNED"
                );
    }

    @Test
    void marksMissingTargetAndTimingMismatch() {
        SubtitleReviewService service = service(
                List.of(
                        new TranscriptSegmentVo(0, 0L, 1_000L, "Hello."),
                        new TranscriptSegmentVo(1, 1_000L, 2_000L, "Late line.")
                ),
                List.of(new SubtitleSegmentVo("zh-CN", 1, 1_400L, 2_400L, "延迟字幕。")),
                Optional.empty(),
                List.of()
        );

        SubtitleReviewSummaryVo result = service.buildReview("review-job", "zh-CN");

        assertThat(result.segmentCount()).isEqualTo(2);
        assertThat(result.missingTargetCount()).isEqualTo(1);
        assertThat(result.timingMismatchCount()).isEqualTo(1);
        assertThat(result.qualityScore()).isNull();
        assertThat(result.qualityVerdict()).isNull();
        assertThat(result.downloadableSubtitleArtifactCount()).isZero();
        assertThat(result.segments())
                .extracting(SubtitleReviewSegmentVo::status)
                .containsExactly(
                        SubtitleReviewSegmentStatus.MISSING_TARGET,
                        SubtitleReviewSegmentStatus.TIMING_MISMATCH
                );
        assertThat(result.segments().getFirst().targetText()).isNull();
        assertThat(result.segments().get(1).timingDeltaMs()).isEqualTo(400L);
    }

    private SubtitleReviewService service(
            List<TranscriptSegmentVo> transcriptSegments,
            List<SubtitleSegmentVo> subtitleSegments,
            Optional<QualityEvaluationVo> qualityEvaluation,
            List<JobArtifactVo> artifacts
    ) {
        return new SubtitleReviewServiceImpl(
                new FakeTranscriptService(transcriptSegments),
                new FakeSubtitleService(subtitleSegments),
                new FakeQualityEvaluationService(qualityEvaluation),
                new FakeJobArtifactService(artifacts)
        );
    }

    private record FakeTranscriptService(List<TranscriptSegmentVo> segments) implements TranscriptService {

        @Override
        public List<TranscriptSegmentVo> replaceTranscript(String jobId, TranscriptionResultBo result) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TranscriptSegmentVo> listTranscript(String jobId) {
            return segments;
        }
    }

    private record FakeSubtitleService(List<SubtitleSegmentVo> segments) implements SubtitleService {

        @Override
        public List<SubtitleSegmentVo> replaceSubtitles(String jobId, String language, TranslationResultBo result) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SubtitleSegmentVo> listSubtitles(String jobId, String language) {
            return segments;
        }
    }

    private QualityEvaluationVo quality() {
        return new QualityEvaluationVo(
                "quality-review",
                "review-job",
                "zh-CN",
                92,
                "GOOD",
                95,
                90,
                93,
                91,
                List.of("One literal line."),
                List.of("Review tone."),
                QualityEvaluationStatus.SUCCEEDED,
                null,
                Instant.parse("2026-06-28T08:00:00Z")
        );
    }

    private JobArtifactVo artifact(JobArtifactType type) {
        return new JobArtifactVo(
                "artifact-" + type.name(),
                "review-job",
                type,
                type.name().toLowerCase() + ".txt",
                "text/plain",
                12L,
                "0123456789abcdef",
                false,
                null,
                Instant.parse("2026-06-28T08:00:00Z")
        );
    }

    private record FakeQualityEvaluationService(Optional<QualityEvaluationVo> evaluation)
            implements QualityEvaluationService {

        @Override
        public QualityEvaluationVo evaluateAndStore(
                String jobId,
                String language,
                List<TranscriptSegmentVo> sourceSegments,
                List<SubtitleSegmentVo> targetSegments
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public QualityEvaluationVo storeCachedEvaluation(String jobId, String language, QualityEvaluationResultBo result) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<QualityEvaluationVo> latestForJob(String jobId) {
            return evaluation;
        }
    }

    private record FakeJobArtifactService(List<JobArtifactVo> artifacts) implements JobArtifactService {

        @Override
        public JobArtifactVo createArtifact(CreateJobArtifactCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JobArtifactVo createReusedArtifact(String jobId, JobArtifactRecord source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<JobArtifactVo> listArtifacts(String jobId) {
            return artifacts;
        }

        @Override
        public StoredObjectResourceBo openArtifact(String jobId, String artifactId) {
            return new StoredObjectResourceBo("empty", "application/octet-stream", 0, new ByteArrayInputStream(new byte[0]));
        }
    }
}
