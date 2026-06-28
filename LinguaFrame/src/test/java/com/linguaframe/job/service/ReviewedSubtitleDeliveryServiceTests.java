package com.linguaframe.job.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.dto.PublishReviewedSubtitlesRequest;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.SubtitleDraftExportFormat;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.ReviewedSubtitlePublishVo;
import com.linguaframe.job.service.impl.ReviewedSubtitleDeliveryServiceImpl;
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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewedSubtitleDeliveryServiceTests {

    @TempDir
    private Path tempDir;

    @Test
    void publishesReviewedSubtitleArtifactsFromDraftOverlay() {
        RecordingSubtitleDraftService draftService = new RecordingSubtitleDraftService();
        RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
        ReviewedSubtitleDeliveryService service = service(
                properties(false),
                draftService,
                artifactService,
                new RecordingObjectStorageService(new byte[] {1, 2, 3}),
                new RecordingMediaWorkDirectoryService(tempDir),
                new RecordingBurnInService()
        );

        ReviewedSubtitlePublishVo result = service.publish(
                "job-reviewed",
                new PublishReviewedSubtitlesRequest("zh-CN", false)
        );

        assertThat(result.jobId()).isEqualTo("job-reviewed");
        assertThat(result.targetLanguage()).isEqualTo("zh-CN");
        assertThat(result.burnedVideoRequested()).isFalse();
        assertThat(result.burnedVideoCreated()).isFalse();
        assertThat(result.artifacts()).extracting(JobArtifactVo::type).containsExactly(
                JobArtifactType.REVIEWED_SUBTITLE_JSON,
                JobArtifactType.REVIEWED_SUBTITLE_SRT,
                JobArtifactType.REVIEWED_SUBTITLE_VTT
        );
        assertThat(artifactService.commands).extracting(CreateJobArtifactCommand::filename).containsExactly(
                "reviewed-subtitles.zh-CN.json",
                "reviewed-subtitles.zh-CN.srt",
                "reviewed-subtitles.zh-CN.vtt"
        );
        assertThat(artifactService.commands).extracting(command -> new String(command.content(), StandardCharsets.UTF_8))
                .allMatch(content -> content.contains("修正后的第二行"));
        assertThat(draftService.exports).containsExactly(
                SubtitleDraftExportFormat.JSON,
                SubtitleDraftExportFormat.SRT,
                SubtitleDraftExportFormat.VTT
        );
    }

    @Test
    void repeatedPublishCreatesNewReviewedArtifacts() {
        RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
        ReviewedSubtitleDeliveryService service = service(
                properties(false),
                new RecordingSubtitleDraftService(),
                artifactService,
                new RecordingObjectStorageService(new byte[] {1, 2, 3}),
                new RecordingMediaWorkDirectoryService(tempDir),
                new RecordingBurnInService()
        );

        ReviewedSubtitlePublishVo first = service.publish(
                "job-reviewed",
                new PublishReviewedSubtitlesRequest("zh-CN", false)
        );
        ReviewedSubtitlePublishVo second = service.publish(
                "job-reviewed",
                new PublishReviewedSubtitlesRequest("zh-CN", false)
        );

        assertThat(first.artifacts()).hasSize(3);
        assertThat(second.artifacts()).hasSize(3);
        assertThat(first.artifacts()).extracting(JobArtifactVo::artifactId)
                .doesNotContainAnyElementsOf(second.artifacts().stream().map(JobArtifactVo::artifactId).toList());
        assertThat(artifactService.commands).hasSize(6);
    }

    @Test
    void skipsReviewedBurnInWhenFfmpegBurnInIsDisabled() {
        RecordingBurnInService burnInService = new RecordingBurnInService();
        ReviewedSubtitleDeliveryService service = service(
                properties(false),
                new RecordingSubtitleDraftService(),
                new RecordingJobArtifactService(),
                new RecordingObjectStorageService(new byte[] {1, 2, 3}),
                new RecordingMediaWorkDirectoryService(tempDir),
                burnInService
        );

        ReviewedSubtitlePublishVo result = service.publish(
                "job-reviewed",
                new PublishReviewedSubtitlesRequest("zh-CN", true)
        );

        assertThat(result.burnedVideoRequested()).isTrue();
        assertThat(result.burnedVideoCreated()).isFalse();
        assertThat(result.artifacts()).extracting(JobArtifactVo::type)
                .doesNotContain(JobArtifactType.REVIEWED_BURNED_VIDEO);
        assertThat(burnInService.commands).isEmpty();
    }

    @Test
    void createsReviewedBurnedVideoWhenRequestedAndEnabled() throws Exception {
        RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
        RecordingMediaWorkDirectoryService workDirectoryService = new RecordingMediaWorkDirectoryService(tempDir);
        RecordingBurnInService burnInService = new RecordingBurnInService();
        ReviewedSubtitleDeliveryService service = service(
                properties(true),
                new RecordingSubtitleDraftService(),
                artifactService,
                new RecordingObjectStorageService("source-video".getBytes(StandardCharsets.UTF_8)),
                workDirectoryService,
                burnInService
        );

        ReviewedSubtitlePublishVo result = service.publish(
                "job-reviewed",
                new PublishReviewedSubtitlesRequest("zh-CN", true)
        );

        assertThat(result.burnedVideoRequested()).isTrue();
        assertThat(result.burnedVideoCreated()).isTrue();
        assertThat(result.artifacts()).extracting(JobArtifactVo::type).containsExactly(
                JobArtifactType.REVIEWED_SUBTITLE_JSON,
                JobArtifactType.REVIEWED_SUBTITLE_SRT,
                JobArtifactType.REVIEWED_SUBTITLE_VTT,
                JobArtifactType.REVIEWED_BURNED_VIDEO
        );
        assertThat(artifactService.commands.getLast().filename()).isEqualTo("reviewed-burned-video.mp4");
        assertThat(new String(Files.readAllBytes(burnInService.commands.getFirst().subtitlePath()), StandardCharsets.UTF_8))
                .contains("修正后的第二行");
        assertThat(workDirectoryService.deletedDirectories).hasSize(1);
    }

    @Test
    void surfacesDraftNotFoundWhenGeneratedSubtitlesAreMissing() {
        RecordingSubtitleDraftService draftService = new RecordingSubtitleDraftService();
        draftService.failWithNotFound = true;
        ReviewedSubtitleDeliveryService service = service(
                properties(false),
                draftService,
                new RecordingJobArtifactService(),
                new RecordingObjectStorageService(new byte[] {1, 2, 3}),
                new RecordingMediaWorkDirectoryService(tempDir),
                new RecordingBurnInService()
        );

        assertThatThrownBy(() -> service.publish(
                "missing-job",
                new PublishReviewedSubtitlesRequest("zh-CN", false)
        ))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Target subtitles not found");
    }

    @Test
    void doesNotCreateReviewedBurnedVideoArtifactWhenBurnInFails() {
        RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
        RecordingBurnInService burnInService = new RecordingBurnInService();
        burnInService.fail = true;
        ReviewedSubtitleDeliveryService service = service(
                properties(true),
                new RecordingSubtitleDraftService(),
                artifactService,
                new RecordingObjectStorageService("source-video".getBytes(StandardCharsets.UTF_8)),
                new RecordingMediaWorkDirectoryService(tempDir),
                burnInService
        );

        assertThatThrownBy(() -> service.publish(
                "job-reviewed",
                new PublishReviewedSubtitlesRequest("zh-CN", true)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to create reviewed burned video");
        assertThat(artifactService.commands).extracting(CreateJobArtifactCommand::type)
                .doesNotContain(JobArtifactType.REVIEWED_BURNED_VIDEO);
    }

    private ReviewedSubtitleDeliveryService service(
            LinguaFrameProperties properties,
            SubtitleDraftService draftService,
            RecordingJobArtifactService artifactService,
            ObjectStorageService objectStorageService,
            MediaWorkDirectoryService workDirectoryService,
            FfmpegSubtitleBurnInService burnInService
    ) {
        return new ReviewedSubtitleDeliveryServiceImpl(
                properties,
                draftService,
                artifactService,
                objectStorageService,
                workDirectoryService,
                burnInService
        );
    }

    private LinguaFrameProperties properties(boolean burnInEnabled) {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getFfmpeg().setBurnInEnabled(burnInEnabled);
        return properties;
    }

    private static final class RecordingSubtitleDraftService implements SubtitleDraftService {
        private final List<SubtitleDraftExportFormat> exports = new ArrayList<>();
        private boolean failWithNotFound;

        @Override
        public com.linguaframe.job.domain.vo.SubtitleDraftSummaryVo getDraft(String jobId, String language) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.linguaframe.job.domain.vo.SubtitleDraftSummaryVo updateDraft(
                String jobId,
                String language,
                com.linguaframe.job.domain.dto.UpdateSubtitleDraftRequest request
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.linguaframe.job.domain.vo.SubtitleDraftSummaryVo clearDraft(String jobId, String language) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] exportDraft(String jobId, String language, SubtitleDraftExportFormat format) {
            if (failWithNotFound) {
                throw new NoSuchElementException("Target subtitles not found");
            }
            exports.add(format);
            String text = switch (format) {
                case JSON -> "{\"language\":\"%s\",\"text\":\"修正后的第二行\"}".formatted(language);
                case SRT -> "1\n00:00:00,000 --> 00:00:01,000\n修正后的第二行\n";
                case VTT -> "WEBVTT\n\n00:00:00.000 --> 00:00:01.000\n修正后的第二行\n";
            };
            return text.getBytes(StandardCharsets.UTF_8);
        }
    }

    private static final class RecordingJobArtifactService implements JobArtifactService {
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
                    "sha-" + commands.size(),
                    false,
                    null,
                    Instant.parse("2026-06-28T10:00:00Z")
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
        public com.linguaframe.job.domain.bo.StoredObjectResourceBo openArtifact(String jobId, String artifactId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingObjectStorageService implements ObjectStorageService {
        private final byte[] sourceBytes;

        private RecordingObjectStorageService(byte[] sourceBytes) {
            this.sourceBytes = sourceBytes;
        }

        @Override
        public StoredObjectBo store(StoreObjectCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream open(String objectKey) {
            return new ByteArrayInputStream(sourceBytes);
        }

        @Override
        public void delete(String objectKey) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingMediaWorkDirectoryService implements MediaWorkDirectoryService {
        private final Path root;
        private final List<Path> deletedDirectories = new ArrayList<>();

        private RecordingMediaWorkDirectoryService(Path root) {
            this.root = root;
        }

        @Override
        public Path createJobWorkDirectory(String jobId) {
            try {
                Path directory = root.resolve(jobId + "-" + deletedDirectories.size());
                Files.createDirectories(directory);
                return directory;
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public void deleteRecursively(Path directory) {
            deletedDirectories.add(directory);
        }
    }

    private static final class RecordingBurnInService implements FfmpegSubtitleBurnInService {
        private final List<BurnInSubtitlesCommand> commands = new ArrayList<>();
        private boolean fail;

        @Override
        public BurnedVideoBo burnInSubtitles(BurnInSubtitlesCommand command) {
            commands.add(command);
            if (fail) {
                throw new IllegalStateException("ffmpeg failed");
            }
            return new BurnedVideoBo(
                    "reviewed-burned-video.mp4",
                    "video/mp4",
                    "reviewed-video".getBytes(StandardCharsets.UTF_8)
            );
        }
    }
}
