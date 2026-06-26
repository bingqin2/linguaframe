package com.linguaframe.job.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.bo.QualityEvaluationResultBo;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.bo.TranslationResultBo;
import com.linguaframe.job.domain.bo.TranscriptionResultBo;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.exception.CostBudgetExceededException;
import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.service.impl.QualityEvaluationPipelineStage;
import com.linguaframe.job.service.impl.TargetSubtitleExportPipelineStage;
import com.linguaframe.job.service.impl.TranscriptSubtitleExportPipelineStage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CostBudgetedPipelineStageTests {

    private final LinguaFrameProperties properties = new LinguaFrameProperties();

    @Test
    void budgetGuardStopsTranscriptStageBeforeTranscriptionProviderCall() {
        properties.getTranscription().setEnabled(true);
        RecordingTranscriptionProvider transcriptionProvider = new RecordingTranscriptionProvider();

        TranscriptSubtitleExportPipelineStage stage = new TranscriptSubtitleExportPipelineStage(
                properties,
                new ExtractedAudioArtifactService(),
                transcriptionProvider,
                new RecordingTranscriptService(),
                new RecordingSubtitleExportService(),
                new FailingCostBudgetGuardService()
        );

        assertThatThrownBy(() -> stage.execute(context()))
                .isInstanceOf(CostBudgetExceededException.class)
                .hasMessageContaining("Job cost budget exceeded before TRANSCRIPT_SUBTITLE_EXPORT");
        assertThat(transcriptionProvider.called).isFalse();
    }

    @Test
    void budgetGuardStopsTargetSubtitleStageBeforeTranslationProviderCall() {
        properties.getTranslation().setEnabled(true);
        RecordingTranslationProvider translationProvider = new RecordingTranslationProvider();

        TargetSubtitleExportPipelineStage stage = new TargetSubtitleExportPipelineStage(
                properties,
                new RecordingJobArtifactService(),
                new RecordingTranscriptService(),
                translationProvider,
                new RecordingSubtitleService(),
                new RecordingSubtitleExportService(),
                new FailingCostBudgetGuardService()
        );

        assertThatThrownBy(() -> stage.execute(context()))
                .isInstanceOf(CostBudgetExceededException.class)
                .hasMessageContaining("Job cost budget exceeded before TARGET_SUBTITLE_EXPORT");
        assertThat(translationProvider.called).isFalse();
    }

    @Test
    void budgetGuardStopsQualityEvaluationStageBeforeEvaluationServiceCall() {
        properties.getEvaluation().setEnabled(true);
        RecordingQualityEvaluationService qualityEvaluationService = new RecordingQualityEvaluationService();

        QualityEvaluationPipelineStage stage = new QualityEvaluationPipelineStage(
                properties,
                new RecordingTranscriptService(),
                new RecordingSubtitleService(),
                qualityEvaluationService,
                new FailingCostBudgetGuardService()
        );

        assertThatThrownBy(() -> stage.execute(context()))
                .isInstanceOf(CostBudgetExceededException.class)
                .hasMessageContaining("Job cost budget exceeded before TRANSLATION_QUALITY_EVALUATION");
        assertThat(qualityEvaluationService.called).isFalse();
    }

    private LocalizationJobExecutionContextBo context() {
        Instant now = Instant.parse("2026-06-27T02:30:00Z");
        return new LocalizationJobExecutionContextBo(
                new LocalizationJobRecord("budgeted-stage-job", "budgeted-stage-video", "zh-CN", LocalizationJobStatus.PROCESSING, now),
                new QueuedLocalizationJobMessage(
                        "budgeted-stage-job",
                        "budgeted-stage-video",
                        "source-videos/budgeted-stage-video/sample.mp4",
                        "zh-CN",
                        now
                ),
                now
        );
    }

    private static class FailingCostBudgetGuardService implements CostBudgetGuardService {

        @Override
        public void assertWithinBudget(String jobId, LocalizationJobStage stage) {
            throw new CostBudgetExceededException(
                    "Job cost budget exceeded before " + stage
                            + ": current estimated cost 0.01 USD, limit 0.01 USD."
            );
        }
    }

    private static class RecordingTranscriptionProvider implements TranscriptionProvider {

        private boolean called;

        @Override
        public TranscriptionResultBo transcribe(String jobId, byte[] audioContent) {
            called = true;
            return new TranscriptionResultBo(List.of());
        }
    }

    private static class RecordingTranslationProvider implements TranslationProvider {

        private boolean called;

        @Override
        public TranslationResultBo translate(
                String jobId,
                String targetLanguage,
                List<TranscriptSegmentVo> transcriptSegments
        ) {
            called = true;
            return new TranslationResultBo(List.of());
        }
    }

    private static class RecordingQualityEvaluationService implements QualityEvaluationService {

        private boolean called;

        @Override
        public QualityEvaluationVo evaluateAndStore(
                String jobId,
                String language,
                List<TranscriptSegmentVo> sourceSegments,
                List<SubtitleSegmentVo> targetSegments
        ) {
            called = true;
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Optional<QualityEvaluationVo> latestForJob(String jobId) {
            return java.util.Optional.empty();
        }
    }

    private static class RecordingTranscriptService implements TranscriptService {

        @Override
        public List<TranscriptSegmentVo> replaceTranscript(String jobId, TranscriptionResultBo result) {
            return List.of();
        }

        @Override
        public List<TranscriptSegmentVo> listTranscript(String jobId) {
            return List.of(new TranscriptSegmentVo(0, 0L, 1_000L, "Hello."));
        }
    }

    private static class RecordingSubtitleService implements SubtitleService {

        @Override
        public List<SubtitleSegmentVo> replaceSubtitles(String jobId, String language, TranslationResultBo result) {
            return List.of();
        }

        @Override
        public List<SubtitleSegmentVo> listSubtitles(String jobId, String language) {
            return List.of(new SubtitleSegmentVo(language, 0, 0L, 1_000L, "你好。"));
        }
    }

    private static class RecordingSubtitleExportService implements SubtitleExportService {

        @Override
        public byte[] exportTranscriptJson(List<TranscriptSegmentVo> segments) {
            return new byte[0];
        }

        @Override
        public byte[] exportSrt(List<TranscriptSegmentVo> segments) {
            return new byte[0];
        }

        @Override
        public byte[] exportVtt(List<TranscriptSegmentVo> segments) {
            return new byte[0];
        }

        @Override
        public byte[] exportSubtitleJson(List<SubtitleSegmentVo> segments) {
            return new byte[0];
        }

        @Override
        public byte[] exportSubtitleSrt(List<SubtitleSegmentVo> segments) {
            return new byte[0];
        }

        @Override
        public byte[] exportSubtitleVtt(List<SubtitleSegmentVo> segments) {
            return new byte[0];
        }
    }

    private static class RecordingJobArtifactService implements JobArtifactService {

        @Override
        public JobArtifactVo createArtifact(CreateJobArtifactCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<JobArtifactVo> listArtifacts(String jobId) {
            return List.of();
        }

        @Override
        public StoredObjectResourceBo openArtifact(String jobId, String artifactId) {
            throw new UnsupportedOperationException();
        }
    }

    private static class ExtractedAudioArtifactService extends RecordingJobArtifactService {

        @Override
        public List<JobArtifactVo> listArtifacts(String jobId) {
            return List.of(new JobArtifactVo(
                    "audio-artifact",
                    jobId,
                    JobArtifactType.EXTRACTED_AUDIO,
                    "audio.wav",
                    "audio/wav",
                    3L,
                    Instant.parse("2026-06-27T02:30:00Z")
            ));
        }

        @Override
        public StoredObjectResourceBo openArtifact(String jobId, String artifactId) {
            return new StoredObjectResourceBo(
                    "audio.wav",
                    "audio/wav",
                    3L,
                    new ByteArrayInputStream(new byte[] {1, 2, 3})
            );
        }
    }
}
