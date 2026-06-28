package com.linguaframe.job.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.service.impl.DubbedVideoPipelineStage;
import com.linguaframe.media.domain.bo.DubbedVideoBo;
import com.linguaframe.media.domain.bo.ReplaceVideoAudioCommand;
import com.linguaframe.media.service.FfmpegAudioReplacementService;
import com.linguaframe.media.service.MediaWorkDirectoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DubbedVideoPipelineStageTests {

    private final LinguaFrameProperties properties = new LinguaFrameProperties();
    private final RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
    private final RecordingFfmpegAudioReplacementService replacementService = new RecordingFfmpegAudioReplacementService();

    @TempDir
    private Path tempDir;

    @Test
    void stageReturnsDubbedVideoDelivery() {
        DubbedVideoPipelineStage stage = stage(new RecordingMediaWorkDirectoryService(tempDir));

        assertThat(stage.stage()).isEqualTo(LocalizationJobStage.DUBBED_VIDEO_DELIVERY);
    }

    @Test
    void skipsWhenTtsIsDisabled() {
        properties.getTts().setEnabled(false);
        properties.getFfmpeg().setBurnInEnabled(true);
        RecordingMediaWorkDirectoryService workDirectoryService = new RecordingMediaWorkDirectoryService(tempDir);

        stage(workDirectoryService).execute(context());

        assertThat(workDirectoryService.createdJobIds).isEmpty();
        assertThat(replacementService.command).isNull();
        assertThat(artifactService.commands).isEmpty();
    }

    @Test
    void skipsWhenDubbingAudioIsMissing() {
        properties.getTts().setEnabled(true);
        properties.getFfmpeg().setBurnInEnabled(true);
        artifactService.artifacts = List.of(burnedVideoArtifact());
        RecordingMediaWorkDirectoryService workDirectoryService = new RecordingMediaWorkDirectoryService(tempDir);

        stage(workDirectoryService).execute(context());

        assertThat(workDirectoryService.createdJobIds).isEmpty();
        assertThat(replacementService.command).isNull();
        assertThat(artifactService.commands).isEmpty();
    }

    @Test
    void createsDubbedVideoFromBurnedVideoAndDubbingAudio() throws IOException {
        properties.getTts().setEnabled(true);
        properties.getFfmpeg().setBurnInEnabled(true);
        artifactService.artifacts = List.of(burnedVideoArtifact(), dubbingAudioArtifact());
        RecordingMediaWorkDirectoryService workDirectoryService = new RecordingMediaWorkDirectoryService(tempDir);

        stage(workDirectoryService).execute(context());

        assertThat(workDirectoryService.createdJobIds).containsExactly("dubbed-video-job-1");
        assertThat(workDirectoryService.cleanedDirectories).containsExactly(workDirectoryService.workDirectory);
        assertThat(Files.readAllBytes(replacementService.command.inputVideoPath())).containsExactly(1, 2, 3);
        assertThat(Files.readAllBytes(replacementService.command.inputAudioPath())).containsExactly(4, 5, 6);
        assertThat(replacementService.command.outputVideoPath())
                .isEqualTo(workDirectoryService.workDirectory.resolve("dubbed-video.mp4"));
        assertThat(artifactService.commands).hasSize(1);
        CreateJobArtifactCommand command = artifactService.commands.getFirst();
        assertThat(command.jobId()).isEqualTo("dubbed-video-job-1");
        assertThat(command.type()).isEqualTo(JobArtifactType.DUBBED_VIDEO);
        assertThat(command.filename()).isEqualTo("dubbed-video.mp4");
        assertThat(command.contentType()).isEqualTo("video/mp4");
        assertThat(command.content()).containsExactly(7, 8, 9);
    }

    @Test
    void cleansWorkDirectoryWhenAudioReplacementFails() {
        properties.getTts().setEnabled(true);
        properties.getFfmpeg().setBurnInEnabled(true);
        artifactService.artifacts = List.of(burnedVideoArtifact(), dubbingAudioArtifact());
        replacementService.failure = new IllegalStateException("ffmpeg failed");
        RecordingMediaWorkDirectoryService workDirectoryService = new RecordingMediaWorkDirectoryService(tempDir);

        assertThatThrownBy(() -> stage(workDirectoryService).execute(context()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ffmpeg failed");
        assertThat(workDirectoryService.cleanedDirectories).containsExactly(workDirectoryService.workDirectory);
        assertThat(artifactService.commands).isEmpty();
    }

    private DubbedVideoPipelineStage stage(RecordingMediaWorkDirectoryService workDirectoryService) {
        return new DubbedVideoPipelineStage(
                properties,
                workDirectoryService,
                artifactService,
                replacementService
        );
    }

    private LocalizationJobExecutionContextBo context() {
        Instant now = Instant.parse("2026-06-28T12:00:00Z");
        return new LocalizationJobExecutionContextBo(
                new LocalizationJobRecord(
                        "dubbed-video-job-1",
                        "dubbed-video-source-1",
                        "zh-CN",
                        "verse",
                        "NATURAL",
                        "HIGH_CONTRAST",
                        LocalizationJobStatus.PROCESSING,
                        now
                ),
                new QueuedLocalizationJobMessage(
                        "dubbed-video-job-1",
                        "dubbed-video-source-1",
                        "source-videos/dubbed-video-source-1/sample.mp4",
                        "zh-CN",
                        "verse",
                        "NATURAL",
                        "HIGH_CONTRAST",
                        now
                ),
                now
        );
    }

    private JobArtifactVo burnedVideoArtifact() {
        return new JobArtifactVo(
                "burned-video-artifact",
                "dubbed-video-job-1",
                JobArtifactType.BURNED_VIDEO,
                "burned-video.mp4",
                "video/mp4",
                3L,
                "burned-video-hash",
                false,
                null,
                Instant.parse("2026-06-28T12:00:01Z")
        );
    }

    private JobArtifactVo dubbingAudioArtifact() {
        return new JobArtifactVo(
                "dubbing-audio-artifact",
                "dubbed-video-job-1",
                JobArtifactType.DUBBING_AUDIO,
                "dubbing-audio.mp3",
                "audio/mpeg",
                3L,
                "dubbing-audio-hash",
                false,
                null,
                Instant.parse("2026-06-28T12:00:02Z")
        );
    }

    private static final class RecordingMediaWorkDirectoryService implements MediaWorkDirectoryService {

        private final Path workDirectory;
        private final List<String> createdJobIds = new ArrayList<>();
        private final List<Path> cleanedDirectories = new ArrayList<>();

        private RecordingMediaWorkDirectoryService(Path rootDirectory) {
            this.workDirectory = rootDirectory.resolve("dubbed-video-work");
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

    private static final class RecordingJobArtifactService implements JobArtifactService {

        private List<JobArtifactVo> artifacts = List.of();
        private final List<CreateJobArtifactCommand> commands = new ArrayList<>();

        @Override
        public JobArtifactVo createArtifact(CreateJobArtifactCommand command) {
            commands.add(command);
            return new JobArtifactVo(
                    "dubbed-video-artifact",
                    command.jobId(),
                    command.type(),
                    command.filename(),
                    command.contentType(),
                    command.content().length,
                    "dubbed-video-hash",
                    false,
                    null,
                    Instant.parse("2026-06-28T12:00:03Z")
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
            return artifacts;
        }

        @Override
        public StoredObjectResourceBo openArtifact(String jobId, String artifactId) {
            byte[] content = switch (artifactId) {
                case "burned-video-artifact" -> new byte[] {1, 2, 3};
                case "dubbing-audio-artifact" -> new byte[] {4, 5, 6};
                default -> throw new IllegalArgumentException("Unexpected artifact id " + artifactId);
            };
            return new StoredObjectResourceBo(
                    artifactId,
                    "application/octet-stream",
                    content.length,
                    new ByteArrayInputStream(content)
            );
        }
    }

    private static final class RecordingFfmpegAudioReplacementService implements FfmpegAudioReplacementService {

        private ReplaceVideoAudioCommand command;
        private RuntimeException failure;

        @Override
        public DubbedVideoBo replaceAudio(ReplaceVideoAudioCommand command) {
            this.command = command;
            if (failure != null) {
                throw failure;
            }
            return new DubbedVideoBo("dubbed-video.mp4", "video/mp4", new byte[] {7, 8, 9});
        }
    }
}
