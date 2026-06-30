package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.service.impl.NarrationWaveformServiceImpl;
import com.linguaframe.media.domain.bo.AudioWaveformAnalyzeCommand;
import com.linguaframe.media.domain.bo.AudioWaveformBo;
import com.linguaframe.media.domain.bo.AudioWaveformBucketBo;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.domain.vo.MediaUploadDetailVo;
import com.linguaframe.media.service.FfmpegAudioWaveformService;
import com.linguaframe.media.service.MediaUploadService;
import com.linguaframe.media.service.MediaWorkDirectoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NarrationWaveformServiceTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    private Path tempDir;

    private final RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
    private final RecordingLocalizationJobQueryService queryService = new RecordingLocalizationJobQueryService();
    private final RecordingMediaUploadService mediaUploadService = new RecordingMediaUploadService();
    private final RecordingFfmpegAudioWaveformService waveformService = new RecordingFfmpegAudioWaveformService();

    @Test
    void prefersNarrationAudioThenNarratedVideoThenBurnedVideoThenSourceMedia() throws IOException {
        artifactService.artifacts.add(artifact("burned", JobArtifactType.BURNED_VIDEO, 1));
        artifactService.artifacts.add(artifact("narrated", JobArtifactType.NARRATED_VIDEO, 2));
        artifactService.artifacts.add(artifact("narration", JobArtifactType.NARRATION_AUDIO, 3));

        var result = service().getWaveform("job-waveform", 96);

        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.sourceType()).isEqualTo("NARRATION_AUDIO");
        assertThat(result.bucketCount()).isEqualTo(96);
        assertThat(result.buckets()).hasSize(1);
        assertThat(Files.readAllBytes(waveformService.command.inputMediaPath())).containsExactly(3, 3, 3);
        assertThat(result.artifactId()).isEqualTo("created-waveform-1");
        assertThat(result.sourceArtifactId()).isEqualTo("narration");
        assertThat(result.cacheHit()).isFalse();
        assertThat(result.contentSha256()).isNotBlank();
        assertThat(result.generatedAt()).isEqualTo(Instant.parse("2026-06-30T01:10:00Z"));
        assertThat(artifactService.createdCommands).hasSize(1);
        assertThat(artifactService.createdCommands.get(0).type()).isEqualTo(JobArtifactType.NARRATION_WAVEFORM);
    }

    @Test
    void fallsBackThroughGeneratedVideosToSourceMedia() throws IOException {
        artifactService.artifacts.add(artifact("burned", JobArtifactType.BURNED_VIDEO, 1));
        var withBurned = service().getWaveform("job-waveform", 96);
        assertThat(withBurned.sourceType()).isEqualTo("BURNED_VIDEO");
        assertThat(Files.readAllBytes(waveformService.command.inputMediaPath())).containsExactly(1, 1, 1);

        artifactService.artifacts.clear();
        waveformService.command = null;
        var withSource = service().getWaveform("job-waveform", 96);
        assertThat(withSource.sourceType()).isEqualTo("SOURCE_MEDIA");
        assertThat(Files.readAllBytes(waveformService.command.inputMediaPath())).containsExactly(9, 8, 7);
    }

    @Test
    void reusesMatchingPersistedWaveformArtifactWithoutRunningAnalyzer() {
        artifactService.artifacts.add(artifact("narration", JobArtifactType.NARRATION_AUDIO, 3));
        artifactService.artifacts.add(waveformArtifact(
                "waveform-cached",
                "narration",
                "narration-hash",
                96,
                9,
                false
        ));

        var result = service().getWaveform("job-waveform", 96);

        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.artifactId()).isEqualTo("waveform-cached");
        assertThat(result.sourceArtifactId()).isEqualTo("narration");
        assertThat(result.cacheHit()).isTrue();
        assertThat(result.generatedAt()).isEqualTo(Instant.parse("2026-06-30T01:10:00Z"));
        assertThat(result.buckets()).hasSize(1);
        assertThat(waveformService.command).isNull();
        assertThat(artifactService.createdCommands).isEmpty();
    }

    @Test
    void regeneratesWhenPersistedWaveformSourceHashIsStale() {
        artifactService.artifacts.add(artifact("narration", JobArtifactType.NARRATION_AUDIO, 3));
        artifactService.artifacts.add(waveformArtifact(
                "waveform-stale",
                "narration",
                "old-source-hash",
                96,
                8,
                false
        ));

        var result = service().getWaveform("job-waveform", 96);

        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.artifactId()).isEqualTo("created-waveform-1");
        assertThat(result.sourceArtifactId()).isEqualTo("narration");
        assertThat(result.cacheHit()).isFalse();
        assertThat(waveformService.command).isNotNull();
        assertThat(artifactService.createdCommands).hasSize(1);
    }

    @Test
    void separatesPersistedWaveformsByBucketCount() {
        artifactService.artifacts.add(artifact("narration", JobArtifactType.NARRATION_AUDIO, 3));
        artifactService.artifacts.add(waveformArtifact(
                "waveform-48",
                "narration",
                "narration-hash",
                48,
                8,
                false
        ));

        var result = service().getWaveform("job-waveform", 96);

        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.artifactId()).isEqualTo("created-waveform-1");
        assertThat(result.bucketCount()).isEqualTo(96);
        assertThat(waveformService.command).isNotNull();
        assertThat(artifactService.createdCommands).hasSize(1);
    }

    @Test
    void returnsUnavailableWhenNoSourceCanBeOpened() {
        mediaUploadService.sourceAvailable = false;

        var result = service().getWaveform("job-waveform", 96);

        assertThat(result.status()).isEqualTo("UNAVAILABLE");
        assertThat(result.sourceType()).isEqualTo("NONE");
        assertThat(result.buckets()).isEmpty();
        assertThat(result.fallbackReason()).isEqualTo("No decoded audio source is available for this job.");
        assertThat(result.artifactId()).isBlank();
        assertThat(result.cacheHit()).isFalse();
        assertThat(artifactService.createdCommands).isEmpty();
    }

    @Test
    void returnsFailedSafeWhenAnalyzerFailsWithoutLeakingPaths() {
        artifactService.artifacts.add(artifact("narration", JobArtifactType.NARRATION_AUDIO, 1));
        waveformService.failure = new IllegalStateException("FFmpeg waveform analysis failed: bad /tmp/private/source.mp4");

        var result = service().getWaveform("job-waveform", 96);

        assertThat(result.status()).isEqualTo("FAILED_SAFE");
        assertThat(result.sourceType()).isEqualTo("NARRATION_AUDIO");
        assertThat(result.buckets()).isEmpty();
        assertThat(result.fallbackReason()).contains("waveform analysis failed");
        assertThat(result.fallbackReason()).doesNotContain("/tmp/private/source.mp4");
        assertThat(result.artifactId()).isBlank();
        assertThat(result.cacheHit()).isFalse();
        assertThat(artifactService.createdCommands).isEmpty();
    }

    @Test
    void capsRequestedBucketCount() {
        artifactService.artifacts.add(artifact("narration", JobArtifactType.NARRATION_AUDIO, 1));

        assertThat(service().getWaveform("job-waveform", 2).bucketCount()).isEqualTo(24);
        assertThat(service().getWaveform("job-waveform", 999).bucketCount()).isEqualTo(192);
    }

    private NarrationWaveformService service() {
        return new NarrationWaveformServiceImpl(
                artifactService,
                queryService,
                mediaUploadService,
                new RecordingMediaWorkDirectoryService(tempDir),
                waveformService
        );
    }

    private JobArtifactVo artifact(String id, JobArtifactType type, int createdSecond) {
        return new JobArtifactVo(
                id,
                "job-waveform",
                type,
                id + ".bin",
                type == JobArtifactType.NARRATION_AUDIO ? "audio/mpeg" : "video/mp4",
                3L,
                id + "-hash",
                false,
                null,
                Instant.parse("2026-06-30T01:00:0" + createdSecond + "Z")
        );
    }

    private JobArtifactVo waveformArtifact(
            String id,
            String sourceArtifactId,
            String sourceContentSha256,
            int bucketCount,
            int createdSecond,
            boolean cacheHit
    ) {
        artifactService.waveformSourceHashes.put(id, sourceContentSha256);
        return new JobArtifactVo(
                id,
                "job-waveform",
                JobArtifactType.NARRATION_WAVEFORM,
                id + ".json",
                "application/json",
                300L,
                id + "-hash",
                cacheHit,
                sourceArtifactId,
                Instant.parse("2026-06-30T01:00:0" + createdSecond + "Z")
        );
    }

    private static byte[] waveformJson(
            String jobId,
            String artifactId,
            String sourceArtifactId,
            String sourceContentSha256,
            int bucketCount
    ) {
        try {
            Map<String, Object> waveform = new LinkedHashMap<>();
            waveform.put("jobId", jobId);
            waveform.put("status", "READY");
            waveform.put("sourceType", "NARRATION_AUDIO");
            waveform.put("bucketCount", bucketCount);
            waveform.put("durationSeconds", "120.000");
            waveform.put("buckets", List.of(Map.of(
                    "index", 0,
                    "startSeconds", "0.000",
                    "endSeconds", "1.000",
                    "peak", "0.500",
                    "rms", "0.250"
            )));
            waveform.put("fallbackReason", "");
            waveform.put("artifactId", artifactId);
            waveform.put("sourceArtifactId", sourceArtifactId);
            waveform.put("sourceContentSha256", sourceContentSha256);
            waveform.put("cacheHit", false);
            waveform.put("contentSha256", artifactId + "-hash");
            waveform.put("generatedAt", "2026-06-30T01:10:00Z");
            return OBJECT_MAPPER.writeValueAsBytes(waveform);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static final class RecordingJobArtifactService implements JobArtifactService {

        private final List<JobArtifactVo> artifacts = new ArrayList<>();
        private final List<CreateJobArtifactCommand> createdCommands = new ArrayList<>();
        private final Map<String, String> waveformSourceHashes = new java.util.HashMap<>();

        @Override
        public JobArtifactVo createArtifact(CreateJobArtifactCommand command) {
            createdCommands.add(command);
            JobArtifactVo artifact = new JobArtifactVo(
                    "created-waveform-" + createdCommands.size(),
                    command.jobId(),
                    command.type(),
                    command.filename(),
                    command.contentType(),
                    (long) command.content().length,
                    "created-waveform-hash-" + createdCommands.size(),
                    false,
                    null,
                    Instant.parse("2026-06-30T01:10:00Z")
            );
            artifacts.add(artifact);
            return artifact;
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
            JobArtifactVo artifact = artifacts.stream()
                    .filter(candidate -> candidate.artifactId().equals(artifactId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unexpected artifact id " + artifactId));
            if (artifact.type() == JobArtifactType.NARRATION_WAVEFORM) {
                byte[] content = waveformJson(
                        jobId,
                        artifact.artifactId(),
                        artifact.sourceArtifactId(),
                        waveformSourceHashes.getOrDefault(artifact.artifactId(), artifact.sourceArtifactId() + "-hash"),
                        artifact.filename().contains("48") ? 48 : 96
                );
                return new StoredObjectResourceBo(artifact.filename(), artifact.contentType(), content.length, new ByteArrayInputStream(content));
            }
            byte[] content = switch (artifactId) {
                case "burned" -> new byte[] {1, 1, 1};
                case "narrated" -> new byte[] {2, 2, 2};
                case "narration" -> new byte[] {3, 3, 3};
                default -> throw new IllegalArgumentException("Unexpected artifact id " + artifactId);
            };
            return new StoredObjectResourceBo(artifactId + ".bin", "application/octet-stream", content.length, new ByteArrayInputStream(content));
        }
    }

    private static final class RecordingLocalizationJobQueryService implements LocalizationJobQueryService {

        @Override
        public LocalizationJobVo getJob(String jobId) {
            return new LocalizationJobVo(
                    jobId,
                    "video-waveform",
                    "zh-CN",
                    "alloy",
                    "NATURAL",
                    "STANDARD",
                    0,
                    "",
                    "OFF",
                    null,
                    LocalizationJobStatus.COMPLETED,
                    Instant.parse("2026-06-30T01:00:00Z"),
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
            return new MediaUploadDetailVo(
                    videoId,
                    "source.mp4",
                    "video/mp4",
                    3L,
                    120,
                    MediaUploadStatus.UPLOADED,
                    Instant.parse("2026-06-30T01:00:00Z")
            );
        }

        @Override
        public StoredObjectResourceBo openSourceMedia(String videoId) {
            if (!sourceAvailable) {
                throw new java.util.NoSuchElementException("Media upload not found.");
            }
            return new StoredObjectResourceBo("source.mp4", "video/mp4", 3L, new ByteArrayInputStream(new byte[] {9, 8, 7}));
        }
    }

    private static final class RecordingMediaWorkDirectoryService implements MediaWorkDirectoryService {

        private final Path workDirectory;

        private RecordingMediaWorkDirectoryService(Path rootDirectory) {
            this.workDirectory = rootDirectory.resolve("waveform-work");
        }

        @Override
        public Path createJobWorkDirectory(String jobId) {
            try {
                return Files.createDirectories(workDirectory);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to create test work directory.", ex);
            }
        }

        @Override
        public void deleteRecursively(Path directory) {
        }
    }

    private static final class RecordingFfmpegAudioWaveformService implements FfmpegAudioWaveformService {

        private AudioWaveformAnalyzeCommand command;
        private RuntimeException failure;

        @Override
        public AudioWaveformBo analyze(AudioWaveformAnalyzeCommand command) {
            this.command = command;
            if (failure != null) {
                throw failure;
            }
            return new AudioWaveformBo(
                    command.bucketCount(),
                    BigDecimal.valueOf(command.durationSeconds()).setScale(3),
                    List.of(new AudioWaveformBucketBo(0, new BigDecimal("0.000"), new BigDecimal("1.000"), new BigDecimal("0.500"), new BigDecimal("0.250")))
            );
        }
    }
}
