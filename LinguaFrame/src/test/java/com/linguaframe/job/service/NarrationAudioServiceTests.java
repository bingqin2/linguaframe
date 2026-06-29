package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.TtsRequestBo;
import com.linguaframe.job.domain.bo.TtsResultBo;
import com.linguaframe.job.domain.entity.NarrationSegmentRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.NarrationGenerationVo;
import com.linguaframe.job.repository.NarrationSegmentRepository;
import com.linguaframe.job.service.impl.NarrationAudioServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NarrationAudioServiceTests {

    @Test
    void generatesNarrationAudioArtifactFromSavedSegments() {
        RecordingNarrationSegmentRepository repository = new RecordingNarrationSegmentRepository(List.of(
                segment(1, "55.000", "70.500", "Explain the second scene.", "alloy"),
                segment(0, "15.000", "28.000", "Explain the first scene.", "alloy")
        ));
        RecordingTtsProvider ttsProvider = new RecordingTtsProvider();
        RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
        RecordingCostBudgetGuardService budgetGuard = new RecordingCostBudgetGuardService();
        NarrationAudioService service = new NarrationAudioServiceImpl(
                repository,
                new StaticLocalizationJobQueryService("zh-CN", "verse"),
                ttsProvider,
                artifactService,
                budgetGuard
        );

        NarrationGenerationVo result = service.generateAudio("job-narration");

        assertThat(budgetGuard.calls).containsExactly("job-narration:DUBBING_AUDIO_GENERATION");
        assertThat(ttsProvider.request.jobId()).isEqualTo("job-narration");
        assertThat(ttsProvider.request.language()).isEqualTo("zh-CN");
        assertThat(ttsProvider.request.voice()).isEqualTo("alloy");
        assertThat(ttsProvider.request.text()).isEqualTo("""
                [00:15.000-00:28.000]
                Explain the first scene.

                [00:55.000-01:10.500]
                Explain the second scene.""");
        assertThat(artifactService.commands).hasSize(1);
        CreateJobArtifactCommand command = artifactService.commands.getFirst();
        assertThat(command.type()).isEqualTo(JobArtifactType.NARRATION_AUDIO);
        assertThat(command.filename()).isEqualTo("narration-audio.mp3");
        assertThat(command.contentType()).isEqualTo("audio/mpeg");
        assertThat(command.content()).containsExactly(1, 2, 3);
        assertThat(result.jobId()).isEqualTo("job-narration");
        assertThat(result.artifactId()).isEqualTo("artifact-narration");
        assertThat(result.filename()).isEqualTo("narration-audio.mp3");
        assertThat(result.segmentCount()).isEqualTo(2);
        assertThat(result.totalCharacterCount()).isEqualTo(49);
        assertThat(result.totalTimelineDurationSeconds()).isEqualByComparingTo("28.500");
        assertThat(result.voiceSummary()).isEqualTo("alloy");
        assertThat(result.status()).isEqualTo("READY");
    }

    @Test
    void rejectsEmptyNarrationWorkspace() {
        NarrationAudioService service = new NarrationAudioServiceImpl(
                new RecordingNarrationSegmentRepository(List.of()),
                new StaticLocalizationJobQueryService("zh-CN", "verse"),
                new RecordingTtsProvider(),
                new RecordingJobArtifactService(),
                new RecordingCostBudgetGuardService()
        );

        assertThatThrownBy(() -> service.generateAudio("job-empty"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Narration workspace is empty");
    }

    @Test
    void usesJobVoiceWhenSegmentsHaveMixedOrBlankVoices() {
        RecordingTtsProvider ttsProvider = new RecordingTtsProvider();
        NarrationAudioService service = new NarrationAudioServiceImpl(
                new RecordingNarrationSegmentRepository(List.of(
                        segment(0, "1.000", "2.000", "First", "alloy"),
                        segment(1, "3.000", "4.000", "Second", null)
                )),
                new StaticLocalizationJobQueryService("en-US", "verse"),
                ttsProvider,
                new RecordingJobArtifactService(),
                new RecordingCostBudgetGuardService()
        );

        NarrationGenerationVo result = service.generateAudio("job-mixed");

        assertThat(ttsProvider.request.voice()).isEqualTo("verse");
        assertThat(result.voiceSummary()).isEqualTo("MIXED_OR_DEFAULT");
    }

    @Test
    void propagatesProviderFailureWithoutCreatingArtifact() {
        RecordingTtsProvider ttsProvider = new RecordingTtsProvider();
        ttsProvider.failure = new IllegalStateException("provider unavailable");
        RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
        NarrationAudioService service = new NarrationAudioServiceImpl(
                new RecordingNarrationSegmentRepository(List.of(segment(0, "1.000", "2.000", "First", "alloy"))),
                new StaticLocalizationJobQueryService("zh-CN", "verse"),
                ttsProvider,
                artifactService,
                new RecordingCostBudgetGuardService()
        );

        assertThatThrownBy(() -> service.generateAudio("job-failure"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provider unavailable");
        assertThat(artifactService.commands).isEmpty();
    }

    private static NarrationSegmentRecord segment(int index, String start, String end, String text, String voice) {
        return new NarrationSegmentRecord(
                "narration-" + index,
                "job-narration",
                index,
                new BigDecimal(start),
                new BigDecimal(end),
                text,
                voice,
                Instant.parse("2026-06-29T10:00:00Z"),
                Instant.parse("2026-06-29T10:00:00Z")
        );
    }

    private record RecordingNarrationSegmentRepository(List<NarrationSegmentRecord> records)
            implements NarrationSegmentRepository {

        @Override
        public void replaceSegments(String jobId, List<NarrationSegmentRecord> segments) {
        }

        @Override
        public List<NarrationSegmentRecord> findByJobId(String jobId) {
            return records;
        }

        @Override
        public void deleteByJobId(String jobId) {
        }
    }

    private static final class StaticLocalizationJobQueryService implements LocalizationJobQueryService {

        private final String targetLanguage;
        private final String ttsVoice;

        private StaticLocalizationJobQueryService(String targetLanguage, String ttsVoice) {
            this.targetLanguage = targetLanguage;
            this.ttsVoice = ttsVoice;
        }

        @Override
        public com.linguaframe.job.domain.vo.LocalizationJobListVo listJobs(
                LocalizationJobStatus status,
                Integer limit,
                Integer offset
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LocalizationJobVo getJob(String jobId) {
            return new LocalizationJobVo(
                    jobId,
                    "video-" + jobId,
                    targetLanguage,
                    ttsVoice,
                    LocalizationJobStatus.COMPLETED,
                    Instant.parse("2026-06-29T09:00:00Z"),
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    null,
                    0,
                    null,
                    List.of(),
                    null,
                    null,
                    List.of(),
                    null,
                    null,
                    null
            );
        }

        @Override
        public com.linguaframe.job.domain.vo.JobDiagnosticsReportVo getDiagnosticsReport(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingTtsProvider implements TtsProvider {

        private TtsRequestBo request;
        private RuntimeException failure;

        @Override
        public TtsResultBo synthesize(TtsRequestBo request) {
            this.request = request;
            if (failure != null) {
                throw failure;
            }
            return new TtsResultBo(new byte[] {1, 2, 3}, "provider-file.mp3", "audio/mpeg");
        }
    }

    private static final class RecordingJobArtifactService implements JobArtifactService {

        private final List<CreateJobArtifactCommand> commands = new ArrayList<>();

        @Override
        public JobArtifactVo createArtifact(CreateJobArtifactCommand command) {
            commands.add(command);
            return new JobArtifactVo(
                    "artifact-narration",
                    command.jobId(),
                    command.type(),
                    command.filename(),
                    command.contentType(),
                    command.content().length,
                    "hash",
                    false,
                    null,
                    Instant.parse("2026-06-29T10:30:00Z")
            );
        }

        @Override
        public JobArtifactVo createReusedArtifact(String jobId, com.linguaframe.job.domain.entity.JobArtifactRecord source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<JobArtifactVo> listArtifacts(String jobId) {
            return List.of();
        }

        @Override
        public com.linguaframe.job.domain.bo.StoredObjectResourceBo openArtifact(String jobId, String artifactId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingCostBudgetGuardService implements CostBudgetGuardService {

        private final List<String> calls = new ArrayList<>();

        @Override
        public void assertWithinBudget(String jobId, LocalizationJobStage stage) {
            calls.add(jobId + ":" + stage.name());
        }
    }
}
