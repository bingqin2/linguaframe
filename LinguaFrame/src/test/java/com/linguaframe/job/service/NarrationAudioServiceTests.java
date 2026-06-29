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
import com.linguaframe.media.domain.bo.CreateTimedAudioBedCommand;
import com.linguaframe.media.service.FfmpegTimedAudioBedService;
import com.linguaframe.media.service.MediaWorkDirectoryService;
import com.linguaframe.job.service.impl.NarrationAudioServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NarrationAudioServiceTests {

    @TempDir
    private Path tempDir;

    @Test
    void generatesTimedNarrationAudioBedFromSavedSegments() throws Exception {
        RecordingNarrationSegmentRepository repository = new RecordingNarrationSegmentRepository(List.of(
                segment(1, "55.000", "70.500", "Explain the second scene.", "alloy"),
                segment(0, "15.000", "28.000", "Explain the first scene.", "alloy")
        ));
        RecordingTtsProvider ttsProvider = new RecordingTtsProvider();
        RecordingTimedAudioBedService timedAudioBedService = new RecordingTimedAudioBedService();
        RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
        RecordingCostBudgetGuardService budgetGuard = new RecordingCostBudgetGuardService();
        NarrationAudioService service = new NarrationAudioServiceImpl(
                repository,
                new StaticLocalizationJobQueryService("zh-CN", "verse"),
                ttsProvider,
                artifactService,
                budgetGuard,
                timedAudioBedService,
                new RecordingMediaWorkDirectoryService(tempDir)
        );

        NarrationGenerationVo result = service.generateAudio("job-narration");

        assertThat(budgetGuard.calls).containsExactly("job-narration:DUBBING_AUDIO_GENERATION");
        assertThat(ttsProvider.requests).extracting(TtsRequestBo::jobId)
                .containsExactly("job-narration", "job-narration");
        assertThat(ttsProvider.requests).extracting(TtsRequestBo::language)
                .containsExactly("zh-CN", "zh-CN");
        assertThat(ttsProvider.requests).extracting(TtsRequestBo::voice)
                .containsExactly("alloy", "alloy");
        assertThat(ttsProvider.requests).extracting(TtsRequestBo::text)
                .containsExactly("Explain the first scene.", "Explain the second scene.");
        assertThat(timedAudioBedService.command.jobId()).isEqualTo("job-narration");
        assertThat(timedAudioBedService.command.outputFilename()).isEqualTo("narration-audio.mp3");
        assertThat(timedAudioBedService.command.outputAudioPath().getFileName().toString())
                .isEqualTo("narration-audio.mp3");
        assertThat(timedAudioBedService.command.segments()).hasSize(2);
        assertThat(timedAudioBedService.command.segments().get(0).startSeconds()).isEqualByComparingTo("15.000");
        assertThat(timedAudioBedService.command.segments().get(0).endSeconds()).isEqualByComparingTo("28.000");
        assertThat(Files.readAllBytes(timedAudioBedService.command.segments().get(0).inputAudioPath()))
                .containsExactly(10, 11, 12);
        assertThat(timedAudioBedService.command.segments().get(1).startSeconds()).isEqualByComparingTo("55.000");
        assertThat(timedAudioBedService.command.segments().get(1).endSeconds()).isEqualByComparingTo("70.500");
        assertThat(Files.readAllBytes(timedAudioBedService.command.segments().get(1).inputAudioPath()))
                .containsExactly(13, 14, 15);
        assertThat(artifactService.commands).hasSize(1);
        CreateJobArtifactCommand command = artifactService.commands.getFirst();
        assertThat(command.type()).isEqualTo(JobArtifactType.NARRATION_AUDIO);
        assertThat(command.filename()).isEqualTo("narration-audio.mp3");
        assertThat(command.contentType()).isEqualTo("audio/mpeg");
        assertThat(command.content()).containsExactly(21, 22, 23);
        assertThat(result.jobId()).isEqualTo("job-narration");
        assertThat(result.artifactId()).isEqualTo("artifact-narration");
        assertThat(result.filename()).isEqualTo("narration-audio.mp3");
        assertThat(result.segmentCount()).isEqualTo(2);
        assertThat(result.totalCharacterCount()).isEqualTo(49);
        assertThat(result.totalTimelineDurationSeconds()).isEqualByComparingTo("28.500");
        assertThat(result.voiceSummary()).isEqualTo("PRESET:alloy");
        assertThat(result.audioLayout()).isEqualTo("TIMED_AUDIO_BED");
        assertThat(result.timeAligned()).isTrue();
        assertThat(result.ttsCallCount()).isEqualTo(2);
        assertThat(result.status()).isEqualTo("READY");
    }

    @Test
    void rejectsEmptyNarrationWorkspace() {
        NarrationAudioService service = new NarrationAudioServiceImpl(
                new RecordingNarrationSegmentRepository(List.of()),
                new StaticLocalizationJobQueryService("zh-CN", "verse"),
                new RecordingTtsProvider(),
                new RecordingJobArtifactService(),
                new RecordingCostBudgetGuardService(),
                new RecordingTimedAudioBedService(),
                new RecordingMediaWorkDirectoryService(tempDir)
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
                new RecordingCostBudgetGuardService(),
                new RecordingTimedAudioBedService(),
                new RecordingMediaWorkDirectoryService(tempDir)
        );

        NarrationGenerationVo result = service.generateAudio("job-mixed");

        assertThat(ttsProvider.requests).extracting(TtsRequestBo::voice)
                .containsExactly("alloy", "verse");
        assertThat(result.voiceSummary()).isEqualTo("MIXED");
    }

    @Test
    void reportsDefaultVoiceSummaryWhenEverySegmentInheritsDefault() {
        NarrationAudioService service = new NarrationAudioServiceImpl(
                new RecordingNarrationSegmentRepository(List.of(
                        segment(0, "1.000", "2.000", "First", null),
                        segment(1, "3.000", "4.000", "Second", "")
                )),
                new StaticLocalizationJobQueryService("en-US", "verse"),
                new RecordingTtsProvider(),
                new RecordingJobArtifactService(),
                new RecordingCostBudgetGuardService(),
                new RecordingTimedAudioBedService(),
                new RecordingMediaWorkDirectoryService(tempDir)
        );

        NarrationGenerationVo result = service.generateAudio("job-default");

        assertThat(result.voiceSummary()).isEqualTo("DEFAULT:verse");
    }

    @Test
    void reportsSingleExplicitVoiceSummaryWhenEverySegmentUsesSamePreset() {
        NarrationAudioService service = new NarrationAudioServiceImpl(
                new RecordingNarrationSegmentRepository(List.of(
                        segment(0, "1.000", "2.000", "First", "alloy"),
                        segment(1, "3.000", "4.000", "Second", "alloy")
                )),
                new StaticLocalizationJobQueryService("en-US", "verse"),
                new RecordingTtsProvider(),
                new RecordingJobArtifactService(),
                new RecordingCostBudgetGuardService(),
                new RecordingTimedAudioBedService(),
                new RecordingMediaWorkDirectoryService(tempDir)
        );

        NarrationGenerationVo result = service.generateAudio("job-alloy");

        assertThat(result.voiceSummary()).isEqualTo("PRESET:alloy");
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
                new RecordingCostBudgetGuardService(),
                new RecordingTimedAudioBedService(),
                new RecordingMediaWorkDirectoryService(tempDir)
        );

        assertThatThrownBy(() -> service.generateAudio("job-failure"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provider unavailable");
        assertThat(artifactService.commands).isEmpty();
    }

    @Test
    void propagatesTimedAudioBedFailureWithoutCreatingArtifact() {
        RecordingTimedAudioBedService timedAudioBedService = new RecordingTimedAudioBedService();
        timedAudioBedService.failure = new IllegalStateException("ffmpeg timed bed failed");
        RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
        NarrationAudioService service = new NarrationAudioServiceImpl(
                new RecordingNarrationSegmentRepository(List.of(segment(0, "1.000", "2.000", "First", "alloy"))),
                new StaticLocalizationJobQueryService("zh-CN", "verse"),
                new RecordingTtsProvider(),
                artifactService,
                new RecordingCostBudgetGuardService(),
                timedAudioBedService,
                new RecordingMediaWorkDirectoryService(tempDir)
        );

        assertThatThrownBy(() -> service.generateAudio("job-failure"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ffmpeg timed bed failed");
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

        private final List<TtsRequestBo> requests = new ArrayList<>();
        private RuntimeException failure;
        private int callCount;

        @Override
        public TtsResultBo synthesize(TtsRequestBo request) {
            this.requests.add(request);
            if (failure != null) {
                throw failure;
            }
            byte[] content = callCount == 0 ? new byte[] {10, 11, 12} : new byte[] {13, 14, 15};
            callCount++;
            return new TtsResultBo(content, "provider-file.mp3", "audio/mpeg");
        }
    }

    private static final class RecordingTimedAudioBedService implements FfmpegTimedAudioBedService {

        private CreateTimedAudioBedCommand command;
        private RuntimeException failure;

        @Override
        public TtsResultBo createAudioBed(CreateTimedAudioBedCommand command) {
            this.command = command;
            if (failure != null) {
                throw failure;
            }
            return new TtsResultBo(new byte[] {21, 22, 23}, command.outputFilename(), "audio/mpeg");
        }
    }

    private static final class RecordingMediaWorkDirectoryService implements MediaWorkDirectoryService {

        private final Path root;
        private Path createdDirectory;
        private Path deletedDirectory;

        private RecordingMediaWorkDirectoryService(Path root) {
            this.root = root;
        }

        @Override
        public Path createJobWorkDirectory(String jobId) {
            createdDirectory = root.resolve(jobId);
            try {
                Files.createDirectories(createdDirectory);
            } catch (java.io.IOException ex) {
                throw new IllegalStateException(ex);
            }
            return createdDirectory;
        }

        @Override
        public void deleteRecursively(Path directory) {
            deletedDirectory = directory;
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
