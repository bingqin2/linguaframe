package com.linguaframe.job.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.bo.TranslationResultBo;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.service.impl.SubtitleBurnInPipelineStage;
import com.linguaframe.media.domain.bo.BurnInSubtitlesCommand;
import com.linguaframe.media.domain.bo.BurnedVideoBo;
import com.linguaframe.media.service.FfmpegSubtitleBurnInService;
import com.linguaframe.media.service.MediaWorkDirectoryService;
import com.linguaframe.storage.domain.bo.StoreObjectCommand;
import com.linguaframe.storage.domain.bo.StoredObjectBo;
import com.linguaframe.storage.service.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubtitleBurnInPipelineStageTests {

    private final LinguaFrameProperties properties = new LinguaFrameProperties();
    private final byte[] sourceBytes = new byte[] {1, 2, 3};
    private final RecordingObjectStorageService objectStorageService = new RecordingObjectStorageService(sourceBytes);
    private final RecordingSubtitleService subtitleService = new RecordingSubtitleService(List.of(
            new SubtitleSegmentVo("zh-CN", 0, 0L, 1_200L, "第一句翻译"),
            new SubtitleSegmentVo("zh-CN", 1, 1_200L, 2_400L, "第二句翻译")
    ));
    private final RecordingSubtitleExportService subtitleExportService = new RecordingSubtitleExportService();
    private final RecordingFfmpegSubtitleBurnInService burnInService = new RecordingFfmpegSubtitleBurnInService();
    private final RecordingJobArtifactService artifactService = new RecordingJobArtifactService();

    @TempDir
    private Path tempDir;

    @Test
    void stageReturnsSubtitleBurnIn() {
        SubtitleBurnInPipelineStage stage = stage(new RecordingMediaWorkDirectoryService(tempDir));

        assertThat(stage.stage()).isEqualTo(LocalizationJobStage.SUBTITLE_BURN_IN);
    }

    @Test
    void skipsWhenBurnInIsDisabled() {
        properties.getFfmpeg().setBurnInEnabled(false);
        RecordingMediaWorkDirectoryService workDirectoryService = new RecordingMediaWorkDirectoryService(tempDir);

        stage(workDirectoryService).execute(context());

        assertThat(objectStorageService.openedObjectKeys).isEmpty();
        assertThat(workDirectoryService.createdJobIds).isEmpty();
        assertThat(burnInService.command).isNull();
        assertThat(artifactService.commands).isEmpty();
    }

    @Test
    void createsBurnedVideoArtifactFromTargetSubtitles() throws IOException {
        properties.getFfmpeg().setBurnInEnabled(true);
        RecordingMediaWorkDirectoryService workDirectoryService = new RecordingMediaWorkDirectoryService(tempDir);

        stage(workDirectoryService).execute(context());

        assertThat(objectStorageService.openedObjectKeys)
                .containsExactly("source-videos/burn-in-video-1/sample.mp4");
        assertThat(workDirectoryService.createdJobIds).containsExactly("burn-in-job-1");
        assertThat(workDirectoryService.cleanedDirectories).containsExactly(workDirectoryService.workDirectory);
        assertThat(Files.readAllBytes(burnInService.command.inputVideoPath())).containsExactly(sourceBytes);
        assertThat(Files.readString(burnInService.command.subtitlePath()))
                .isEqualTo("target-subtitle-srt");
        assertThat(burnInService.command.jobId()).isEqualTo("burn-in-job-1");
        assertThat(burnInService.command.inputVideoPath())
                .isEqualTo(workDirectoryService.workDirectory.resolve("source-video.mp4"));
        assertThat(burnInService.command.subtitlePath())
                .isEqualTo(workDirectoryService.workDirectory.resolve("target-subtitles.srt"));
        assertThat(burnInService.command.outputVideoPath())
                .isEqualTo(workDirectoryService.workDirectory.resolve("burned-video.mp4"));
        assertThat(burnInService.command.subtitleStylePreset()).isEqualTo("HIGH_CONTRAST");
        assertThat(artifactService.commands).hasSize(1);
        CreateJobArtifactCommand command = artifactService.commands.getFirst();
        assertThat(command.jobId()).isEqualTo("burn-in-job-1");
        assertThat(command.type()).isEqualTo(JobArtifactType.BURNED_VIDEO);
        assertThat(command.filename()).isEqualTo("burned-video.mp4");
        assertThat(command.contentType()).isEqualTo("video/mp4");
        assertThat(command.content()).containsExactly(9, 8, 7);
    }

    @Test
    void reusesCachedBurnedVideoBeforeRunningFfmpeg() {
        properties.getFfmpeg().setBurnInEnabled(true);
        RecordingMediaWorkDirectoryService workDirectoryService = new RecordingMediaWorkDirectoryService(tempDir);
        RecordingArtifactCacheService cacheService = new RecordingArtifactCacheService(new JobArtifactVo(
                "cached-burned-artifact",
                "burn-in-job-1",
                JobArtifactType.BURNED_VIDEO,
                "burned-video.mp4",
                "video/mp4",
                456L,
                "cached-burned-hash",
                true,
                "source-burned-artifact",
                Instant.parse("2026-06-27T09:30:00Z")
        ));
        SubtitleBurnInPipelineStage stage = new SubtitleBurnInPipelineStage(
                properties,
                objectStorageService,
                workDirectoryService,
                burnInService,
                subtitleService,
                subtitleExportService,
                artifactService,
                cacheService
        );
        LocalizationJobExecutionContextBo context = context();

        stage.execute(context);

        assertThat(cacheService.requestedTypes).containsExactly(JobArtifactType.BURNED_VIDEO);
        assertThat(context.consumeCacheHits()).containsExactly(cacheService.artifact);
        assertThat(objectStorageService.openedObjectKeys).isEmpty();
        assertThat(workDirectoryService.createdJobIds).isEmpty();
        assertThat(burnInService.command).isNull();
        assertThat(artifactService.commands).isEmpty();
    }

    @Test
    void failsWhenEnabledAndTargetSubtitlesAreMissing() {
        properties.getFfmpeg().setBurnInEnabled(true);
        SubtitleBurnInPipelineStage stage = new SubtitleBurnInPipelineStage(
                properties,
                objectStorageService,
                new RecordingMediaWorkDirectoryService(tempDir),
                burnInService,
                new RecordingSubtitleService(List.of()),
                subtitleExportService,
                artifactService,
                new EmptyArtifactCacheService()
        );

        assertThatThrownBy(() -> stage.execute(context()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Target subtitles not found for subtitle burn-in.");
    }

    private SubtitleBurnInPipelineStage stage(RecordingMediaWorkDirectoryService workDirectoryService) {
        return new SubtitleBurnInPipelineStage(
                properties,
                objectStorageService,
                workDirectoryService,
                burnInService,
                subtitleService,
                subtitleExportService,
                artifactService,
                new EmptyArtifactCacheService()
        );
    }

    private LocalizationJobExecutionContextBo context() {
        Instant now = Instant.parse("2026-06-26T23:30:00Z");
        return new LocalizationJobExecutionContextBo(
                new LocalizationJobRecord(
                        "burn-in-job-1",
                        "burn-in-video-1",
                        "zh-CN",
                        null,
                        "NATURAL",
                        "HIGH_CONTRAST",
                        LocalizationJobStatus.PROCESSING,
                        now
                ),
                new QueuedLocalizationJobMessage(
                        "burn-in-job-1",
                        "burn-in-video-1",
                        "source-videos/burn-in-video-1/sample.mp4",
                        "zh-CN",
                        null,
                        "NATURAL",
                        "HIGH_CONTRAST",
                        now
                ),
                now
        );
    }

    private static class RecordingObjectStorageService implements ObjectStorageService {

        private final byte[] content;
        private final List<String> openedObjectKeys = new ArrayList<>();

        private RecordingObjectStorageService(byte[] content) {
            this.content = content;
        }

        @Override
        public StoredObjectBo store(StoreObjectCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream open(String objectKey) {
            openedObjectKeys.add(objectKey);
            return new ByteArrayInputStream(content);
        }

        @Override
        public void delete(String objectKey) {
            throw new UnsupportedOperationException();
        }
    }

    private static class RecordingMediaWorkDirectoryService implements MediaWorkDirectoryService {

        private final Path workDirectory;
        private final List<String> createdJobIds = new ArrayList<>();
        private final List<Path> cleanedDirectories = new ArrayList<>();

        private RecordingMediaWorkDirectoryService(Path rootDirectory) {
            this.workDirectory = rootDirectory.resolve("media-work");
        }

        @Override
        public Path createJobWorkDirectory(String jobId) {
            createdJobIds.add(jobId);
            try {
                Files.createDirectories(workDirectory);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to create test work directory.", ex);
            }
            return workDirectory;
        }

        @Override
        public void deleteRecursively(Path directory) {
            cleanedDirectories.add(directory);
        }
    }

    private static class RecordingFfmpegSubtitleBurnInService implements FfmpegSubtitleBurnInService {

        private BurnInSubtitlesCommand command;

        @Override
        public BurnedVideoBo burnInSubtitles(BurnInSubtitlesCommand command) {
            this.command = command;
            return new BurnedVideoBo("burned-video.mp4", "video/mp4", new byte[] {9, 8, 7});
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

    private static class RecordingSubtitleExportService implements SubtitleExportService {

        @Override
        public byte[] exportTranscriptJson(List<TranscriptSegmentVo> segments) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] exportSrt(List<TranscriptSegmentVo> segments) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] exportVtt(List<TranscriptSegmentVo> segments) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] exportSubtitleJson(List<SubtitleSegmentVo> segments) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] exportSubtitleSrt(List<SubtitleSegmentVo> segments) {
            return "target-subtitle-srt".getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] exportSubtitleVtt(List<SubtitleSegmentVo> segments) {
            throw new UnsupportedOperationException();
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
                    false,
                    null,
                    Instant.parse("2026-06-26T23:30:00Z")
            );
        }

        @Override
        public JobArtifactVo createReusedArtifact(
                String jobId,
                com.linguaframe.job.domain.entity.JobArtifactRecord source
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<JobArtifactVo> listArtifacts(String jobId) {
            return List.of();
        }

        @Override
        public StoredObjectResourceBo openArtifact(String jobId, String artifactId) {
            return new StoredObjectResourceBo(
                    "burned-video.mp4",
                    "video/mp4",
                    0L,
                    new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8))
            );
        }
    }

    private static class EmptyArtifactCacheService implements ArtifactCacheService {

        @Override
        public java.util.Optional<JobArtifactVo> tryReuseArtifact(
                LocalizationJobExecutionContextBo context,
                JobArtifactType type
        ) {
            return java.util.Optional.empty();
        }
    }

    private static class RecordingArtifactCacheService implements ArtifactCacheService {

        private final JobArtifactVo artifact;
        private final List<JobArtifactType> requestedTypes = new ArrayList<>();

        private RecordingArtifactCacheService(JobArtifactVo artifact) {
            this.artifact = artifact;
        }

        @Override
        public java.util.Optional<JobArtifactVo> tryReuseArtifact(
                LocalizationJobExecutionContextBo context,
                JobArtifactType type
        ) {
            requestedTypes.add(type);
            return java.util.Optional.of(artifact);
        }
    }
}
