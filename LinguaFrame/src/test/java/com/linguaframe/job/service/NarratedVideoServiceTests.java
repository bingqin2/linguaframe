package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.service.impl.NarratedVideoServiceImpl;
import com.linguaframe.media.domain.bo.DubbedVideoBo;
import com.linguaframe.media.domain.bo.ReplaceVideoAudioCommand;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.domain.vo.MediaUploadDetailVo;
import com.linguaframe.media.service.FfmpegAudioReplacementService;
import com.linguaframe.media.service.MediaUploadService;
import com.linguaframe.media.service.MediaWorkDirectoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NarratedVideoServiceTests {

    private final RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
    private final RecordingLocalizationJobQueryService queryService = new RecordingLocalizationJobQueryService();
    private final RecordingMediaUploadService mediaUploadService = new RecordingMediaUploadService();
    private final RecordingFfmpegAudioReplacementService audioReplacementService = new RecordingFfmpegAudioReplacementService();

    @TempDir
    private Path tempDir;

    @Test
    void generatesNarratedVideoFromBurnedVideoAndNarrationAudio() throws IOException {
        artifactService.artifacts.add(artifact("burned", JobArtifactType.BURNED_VIDEO, "burned-video.mp4", 1));
        artifactService.artifacts.add(artifact("narration", JobArtifactType.NARRATION_AUDIO, "narration-audio.mp3", 2));
        RecordingMediaWorkDirectoryService workDirectoryService = new RecordingMediaWorkDirectoryService(tempDir);

        var result = service(workDirectoryService).generateVideo("job-narrated");

        assertThat(workDirectoryService.createdJobIds).containsExactly("job-narrated");
        assertThat(workDirectoryService.cleanedDirectories).containsExactly(workDirectoryService.workDirectory);
        assertThat(Files.readAllBytes(audioReplacementService.command.inputVideoPath())).containsExactly(1, 2, 3);
        assertThat(Files.readAllBytes(audioReplacementService.command.inputAudioPath())).containsExactly(4, 5, 6);
        assertThat(audioReplacementService.command.outputVideoPath()).isEqualTo(workDirectoryService.workDirectory.resolve("narrated-video.mp4"));
        assertThat(audioReplacementService.command.outputFilename()).isEqualTo("narrated-video.mp4");
        assertThat(result.jobId()).isEqualTo("job-narrated");
        assertThat(result.filename()).isEqualTo("narrated-video.mp4");
        assertThat(result.contentType()).isEqualTo("video/mp4");
        assertThat(result.baseVideoType()).isEqualTo("BURNED_VIDEO");
        assertThat(result.narrationAudioArtifactId()).isEqualTo("narration");
        assertThat(result.status()).isEqualTo("READY");
        assertThat(artifactService.createdCommands).hasSize(1);
        CreateJobArtifactCommand created = artifactService.createdCommands.getFirst();
        assertThat(created.type()).isEqualTo(JobArtifactType.NARRATED_VIDEO);
        assertThat(created.filename()).isEqualTo("narrated-video.mp4");
        assertThat(created.content()).containsExactly(7, 8, 9);
    }

    @Test
    void prefersReviewedBurnedVideoAsBase() {
        artifactService.artifacts.add(artifact("burned", JobArtifactType.BURNED_VIDEO, "burned-video.mp4", 1));
        artifactService.artifacts.add(artifact("reviewed", JobArtifactType.REVIEWED_BURNED_VIDEO, "reviewed-burned-video.mp4", 3));
        artifactService.artifacts.add(artifact("narration", JobArtifactType.NARRATION_AUDIO, "narration-audio.mp3", 2));

        var result = service(new RecordingMediaWorkDirectoryService(tempDir)).generateVideo("job-narrated");

        assertThat(result.baseVideoType()).isEqualTo("REVIEWED_BURNED_VIDEO");
        assertThat(audioReplacementService.command.inputVideoPath().getFileName().toString()).isEqualTo("base-video.mp4");
    }

    @Test
    void fallsBackToSourceVideoWhenGeneratedBaseIsMissing() throws IOException {
        artifactService.artifacts.add(artifact("narration", JobArtifactType.NARRATION_AUDIO, "narration-audio.mp3", 2));
        RecordingMediaWorkDirectoryService workDirectoryService = new RecordingMediaWorkDirectoryService(tempDir);

        var result = service(workDirectoryService).generateVideo("job-narrated");

        assertThat(result.baseVideoType()).isEqualTo("SOURCE_VIDEO");
        assertThat(Files.readAllBytes(audioReplacementService.command.inputVideoPath())).containsExactly(9, 8, 7);
    }

    @Test
    void rejectsMissingNarrationAudio() {
        artifactService.artifacts.add(artifact("burned", JobArtifactType.BURNED_VIDEO, "burned-video.mp4", 1));

        assertThatThrownBy(() -> service(new RecordingMediaWorkDirectoryService(tempDir)).generateVideo("job-narrated"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Narration audio is required before generating narrated video.");
        assertThat(audioReplacementService.command).isNull();
        assertThat(artifactService.createdCommands).isEmpty();
    }

    @Test
    void rejectsWhenNoBaseVideoIsAvailable() {
        artifactService.artifacts.add(artifact("narration", JobArtifactType.NARRATION_AUDIO, "narration-audio.mp3", 2));
        mediaUploadService.sourceAvailable = false;

        assertThatThrownBy(() -> service(new RecordingMediaWorkDirectoryService(tempDir)).generateVideo("job-narrated"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A source or generated video is required before generating narrated video.");
        assertThat(audioReplacementService.command).isNull();
        assertThat(artifactService.createdCommands).isEmpty();
    }

    @Test
    void cleansWorkDirectoryWhenFfmpegFails() {
        artifactService.artifacts.add(artifact("burned", JobArtifactType.BURNED_VIDEO, "burned-video.mp4", 1));
        artifactService.artifacts.add(artifact("narration", JobArtifactType.NARRATION_AUDIO, "narration-audio.mp3", 2));
        audioReplacementService.failure = new IllegalStateException("ffmpeg failed");
        RecordingMediaWorkDirectoryService workDirectoryService = new RecordingMediaWorkDirectoryService(tempDir);

        assertThatThrownBy(() -> service(workDirectoryService).generateVideo("job-narrated"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ffmpeg failed");
        assertThat(workDirectoryService.cleanedDirectories).containsExactly(workDirectoryService.workDirectory);
        assertThat(artifactService.createdCommands).isEmpty();
    }

    private NarratedVideoService service(RecordingMediaWorkDirectoryService workDirectoryService) {
        return new NarratedVideoServiceImpl(
                artifactService,
                queryService,
                mediaUploadService,
                workDirectoryService,
                audioReplacementService
        );
    }

    private JobArtifactVo artifact(String id, JobArtifactType type, String filename, int createdSecond) {
        return new JobArtifactVo(
                id,
                "job-narrated",
                type,
                filename,
                type == JobArtifactType.NARRATION_AUDIO ? "audio/mpeg" : "video/mp4",
                3L,
                id + "-hash",
                false,
                null,
                Instant.parse("2026-06-29T12:00:0" + createdSecond + "Z")
        );
    }

    private static final class RecordingJobArtifactService implements JobArtifactService {

        private final List<JobArtifactVo> artifacts = new ArrayList<>();
        private final List<CreateJobArtifactCommand> createdCommands = new ArrayList<>();

        @Override
        public JobArtifactVo createArtifact(CreateJobArtifactCommand command) {
            createdCommands.add(command);
            return new JobArtifactVo(
                    "narrated-video-artifact",
                    command.jobId(),
                    command.type(),
                    command.filename(),
                    command.contentType(),
                    command.content().length,
                    "narrated-video-hash",
                    false,
                    null,
                    Instant.parse("2026-06-29T12:00:10Z")
            );
        }

        @Override
        public JobArtifactVo createReusedArtifact(String jobId, JobArtifactRecord source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<JobArtifactVo> listArtifacts(String jobId) {
            return artifacts.stream()
                    .sorted(Comparator.comparing(JobArtifactVo::createdAt))
                    .toList();
        }

        @Override
        public StoredObjectResourceBo openArtifact(String jobId, String artifactId) {
            byte[] content = switch (artifactId) {
                case "burned" -> new byte[] {1, 2, 3};
                case "reviewed" -> new byte[] {3, 2, 1};
                case "narration" -> new byte[] {4, 5, 6};
                default -> throw new IllegalArgumentException("Unexpected artifact id " + artifactId);
            };
            return new StoredObjectResourceBo(
                    artifactId + ".bin",
                    "application/octet-stream",
                    content.length,
                    new ByteArrayInputStream(content)
            );
        }
    }

    private static final class RecordingLocalizationJobQueryService implements LocalizationJobQueryService {

        @Override
        public LocalizationJobVo getJob(String jobId) {
            return new LocalizationJobVo(
                    jobId,
                    "video-narrated",
                    "zh-CN",
                    "alloy",
                    LocalizationJobStatus.COMPLETED,
                    Instant.parse("2026-06-29T12:00:00Z"),
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
        public LocalizationJobListVo listJobs(LocalizationJobStatus status, Integer limit, Integer offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JobDiagnosticsReportVo getDiagnosticsReport(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingMediaUploadService implements MediaUploadService {

        private boolean sourceAvailable = true;

        @Override
        public com.linguaframe.media.domain.vo.MediaUploadVo createUpload(
                MultipartFile file,
                String targetLanguage,
                String ttsVoice,
                String translationStyle,
                String subtitleStylePreset,
                String translationGlossary,
                String subtitlePolishingMode,
                String demoProfileId
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MediaUploadDetailVo getUpload(String videoId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StoredObjectResourceBo openSourceMedia(String videoId) {
            if (!sourceAvailable) {
                throw new java.util.NoSuchElementException("Media upload not found.");
            }
            return new StoredObjectResourceBo(
                    "source-video.mp4",
                    "video/mp4",
                    3L,
                    new ByteArrayInputStream(new byte[] {9, 8, 7})
            );
        }
    }

    private static final class RecordingMediaWorkDirectoryService implements MediaWorkDirectoryService {

        private final Path workDirectory;
        private final List<String> createdJobIds = new ArrayList<>();
        private final List<Path> cleanedDirectories = new ArrayList<>();

        private RecordingMediaWorkDirectoryService(Path rootDirectory) {
            this.workDirectory = rootDirectory.resolve("narrated-video-work");
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

    private static final class RecordingFfmpegAudioReplacementService implements FfmpegAudioReplacementService {

        private ReplaceVideoAudioCommand command;
        private RuntimeException failure;

        @Override
        public DubbedVideoBo replaceAudio(ReplaceVideoAudioCommand command) {
            this.command = command;
            if (failure != null) {
                throw failure;
            }
            return new DubbedVideoBo(command.outputFilename(), "video/mp4", new byte[] {7, 8, 9});
        }
    }
}
