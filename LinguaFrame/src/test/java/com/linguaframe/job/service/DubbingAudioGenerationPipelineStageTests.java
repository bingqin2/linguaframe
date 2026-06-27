package com.linguaframe.job.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.bo.TranslationResultBo;
import com.linguaframe.job.domain.bo.TtsRequestBo;
import com.linguaframe.job.domain.bo.TtsResultBo;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.exception.CostBudgetExceededException;
import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.service.impl.DubbingAudioGenerationPipelineStage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DubbingAudioGenerationPipelineStageTests {

    private final LinguaFrameProperties properties = new LinguaFrameProperties();
    private final RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
    private final RecordingSubtitleService subtitleService = new RecordingSubtitleService(List.of(
            new SubtitleSegmentVo("zh-CN", 1, 1_200L, 2_400L, " 第二句翻译 "),
            new SubtitleSegmentVo("zh-CN", 0, 0L, 1_200L, "第一句翻译")
    ));
    private final RecordingTtsProvider ttsProvider = new RecordingTtsProvider();

    @Test
    void stageReturnsDubbingAudioGeneration() {
        DubbingAudioGenerationPipelineStage stage = stage();

        assertThat(stage.stage()).isEqualTo(LocalizationJobStage.DUBBING_AUDIO_GENERATION);
    }

    @Test
    void skipsWhenTtsIsDisabled() {
        properties.getTts().setEnabled(false);

        stage().execute(context());

        assertThat(ttsProvider.request).isNull();
        assertThat(artifactService.commands).isEmpty();
    }

    @Test
    void createsDubbingAudioArtifactFromTargetSubtitles() {
        properties.getTts().setEnabled(true);

        stage().execute(context());

        assertThat(ttsProvider.request.jobId()).isEqualTo("dubbing-job-1");
        assertThat(ttsProvider.request.language()).isEqualTo("zh-CN");
        assertThat(ttsProvider.request.text()).isEqualTo("第一句翻译\n第二句翻译");
        assertThat(artifactService.commands).hasSize(1);
        CreateJobArtifactCommand command = artifactService.commands.getFirst();
        assertThat(command.jobId()).isEqualTo("dubbing-job-1");
        assertThat(command.type()).isEqualTo(JobArtifactType.DUBBING_AUDIO);
        assertThat(command.filename()).isEqualTo("dubbing-audio.mp3");
        assertThat(command.contentType()).isEqualTo("audio/mpeg");
        assertThat(command.content()).containsExactly(9, 8, 7);
    }

    @Test
    void failsWhenEnabledAndTargetSubtitlesAreMissing() {
        properties.getTts().setEnabled(true);
        DubbingAudioGenerationPipelineStage stage = new DubbingAudioGenerationPipelineStage(
                properties,
                artifactService,
                new RecordingSubtitleService(List.of()),
                ttsProvider,
                new NoopCostBudgetGuardService()
        );

        assertThatThrownBy(() -> stage.execute(context()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Target subtitles not found for dubbing audio generation.");
    }

    @Test
    void budgetGuardStopsBeforeTtsProviderCall() {
        properties.getTts().setEnabled(true);
        DubbingAudioGenerationPipelineStage stage = new DubbingAudioGenerationPipelineStage(
                properties,
                artifactService,
                subtitleService,
                ttsProvider,
                new FailingCostBudgetGuardService()
        );

        assertThatThrownBy(() -> stage.execute(context()))
                .isInstanceOf(CostBudgetExceededException.class)
                .hasMessageContaining("Job cost budget exceeded before DUBBING_AUDIO_GENERATION");
        assertThat(ttsProvider.request).isNull();
        assertThat(artifactService.commands).isEmpty();
    }

    private DubbingAudioGenerationPipelineStage stage() {
        return new DubbingAudioGenerationPipelineStage(
                properties,
                artifactService,
                subtitleService,
                ttsProvider,
                new NoopCostBudgetGuardService()
        );
    }

    private LocalizationJobExecutionContextBo context() {
        Instant now = Instant.parse("2026-06-26T23:00:00Z");
        return new LocalizationJobExecutionContextBo(
                new LocalizationJobRecord("dubbing-job-1", "dubbing-video-1", "zh-CN", LocalizationJobStatus.PROCESSING, now),
                new QueuedLocalizationJobMessage(
                        "dubbing-job-1",
                        "dubbing-video-1",
                        "source-videos/dubbing-video-1/sample.mp4",
                        "zh-CN",
                        now
                ),
                now
        );
    }

    private static class RecordingTtsProvider implements TtsProvider {

        private TtsRequestBo request;

        @Override
        public TtsResultBo synthesize(TtsRequestBo request) {
            this.request = request;
            return new TtsResultBo(new byte[] {9, 8, 7}, "dubbing-audio.mp3", "audio/mpeg");
        }
    }

    private static class RecordingSubtitleService implements SubtitleService {

        private final List<SubtitleSegmentVo> subtitles;

        private RecordingSubtitleService(List<SubtitleSegmentVo> subtitles) {
            this.subtitles = subtitles;
        }

        @Override
        public List<SubtitleSegmentVo> replaceSubtitles(String jobId, String language, TranslationResultBo result) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SubtitleSegmentVo> listSubtitles(String jobId, String language) {
            return subtitles;
        }
    }

    private static class RecordingJobArtifactService implements JobArtifactService {

        private final List<CreateJobArtifactCommand> commands = new ArrayList<>();

        @Override
        public JobArtifactVo createArtifact(CreateJobArtifactCommand command) {
            commands.add(command);
            return new JobArtifactVo(
                    "artifact-" + commands.size(),
                    command.jobId(),
                    command.type(),
                    command.filename(),
                    command.contentType(),
                    command.content().length,
                    "artifact-hash-" + commands.size(),
                    Instant.parse("2026-06-26T23:00:00Z")
            );
        }

        @Override
        public List<JobArtifactVo> listArtifacts(String jobId) {
            return List.of();
        }

        @Override
        public StoredObjectResourceBo openArtifact(String jobId, String artifactId) {
            return new StoredObjectResourceBo(
                    "dubbing-audio.mp3",
                    "audio/mpeg",
                    0L,
                    new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8))
            );
        }
    }

    private static class NoopCostBudgetGuardService implements CostBudgetGuardService {

        @Override
        public void assertWithinBudget(String jobId, LocalizationJobStage stage) {
        }
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
}
