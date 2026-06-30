package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.NarrationWaveformSourceType;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.NarrationWaveformBucketVo;
import com.linguaframe.job.domain.vo.NarrationWaveformVo;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.job.service.NarrationWaveformService;
import com.linguaframe.media.domain.bo.AudioWaveformAnalyzeCommand;
import com.linguaframe.media.domain.bo.AudioWaveformBo;
import com.linguaframe.media.service.FfmpegAudioWaveformService;
import com.linguaframe.media.service.MediaUploadService;
import com.linguaframe.media.service.MediaWorkDirectoryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class NarrationWaveformServiceImpl implements NarrationWaveformService {

    private static final int DEFAULT_BUCKET_COUNT = 96;
    private static final int MIN_BUCKET_COUNT = 24;
    private static final int MAX_BUCKET_COUNT = 192;

    private final JobArtifactService artifactService;
    private final LocalizationJobQueryService queryService;
    private final MediaUploadService mediaUploadService;
    private final MediaWorkDirectoryService workDirectoryService;
    private final FfmpegAudioWaveformService waveformService;
    private final ObjectMapper objectMapper;

    @Autowired
    public NarrationWaveformServiceImpl(
            JobArtifactService artifactService,
            LocalizationJobQueryService queryService,
            MediaUploadService mediaUploadService,
            MediaWorkDirectoryService workDirectoryService,
            FfmpegAudioWaveformService waveformService
    ) {
        this(artifactService, queryService, mediaUploadService, workDirectoryService, waveformService, new ObjectMapper());
    }

    NarrationWaveformServiceImpl(
            JobArtifactService artifactService,
            LocalizationJobQueryService queryService,
            MediaUploadService mediaUploadService,
            MediaWorkDirectoryService workDirectoryService,
            FfmpegAudioWaveformService waveformService,
            ObjectMapper objectMapper
    ) {
        this.artifactService = artifactService;
        this.queryService = queryService;
        this.mediaUploadService = mediaUploadService;
        this.workDirectoryService = workDirectoryService;
        this.waveformService = waveformService;
        this.objectMapper = objectMapper.findAndRegisterModules();
    }

    @Override
    public NarrationWaveformVo getWaveform(String jobId, Integer requestedBucketCount) {
        int bucketCount = capBucketCount(requestedBucketCount);
        LocalizationJobVo job = queryService.getJob(jobId);
        Path workDirectory = workDirectoryService.createJobWorkDirectory(jobId);
        try {
            List<JobArtifactVo> artifacts = artifactService.listArtifacts(job.jobId());
            Optional<WaveformSource> source = openSource(job, artifacts, workDirectory);
            if (source.isEmpty()) {
                return empty(jobId, "UNAVAILABLE", NarrationWaveformSourceType.NONE, bucketCount,
                        "No decoded audio source is available for this job.");
            }
            Optional<NarrationWaveformVo> cached = findReusableWaveform(jobId, artifacts, source.get(), bucketCount);
            if (cached.isPresent()) {
                return cached.get();
            }
            try {
                AudioWaveformBo waveform = waveformService.analyze(new AudioWaveformAnalyzeCommand(
                        source.get().path(),
                        bucketCount,
                        source.get().durationSeconds()
                ));
                NarrationWaveformVo generated = new NarrationWaveformVo(
                        jobId,
                        "READY",
                        source.get().type().name(),
                        waveform.bucketCount(),
                        waveform.durationSeconds(),
                        waveform.buckets().stream()
                                .map(bucket -> new NarrationWaveformBucketVo(
                                        bucket.index(),
                                        bucket.startSeconds(),
                                        bucket.endSeconds(),
                                        bucket.peak(),
                                        bucket.rms()
                                ))
                                .toList(),
                        "",
                        "",
                        source.get().artifactId(),
                        source.get().contentSha256(),
                        false,
                        "",
                        null
                );
                return persistWaveform(jobId, generated);
            } catch (RuntimeException ex) {
                return empty(jobId, "FAILED_SAFE", source.get().type(), bucketCount, safeFallbackReason(ex));
            }
        } finally {
            workDirectoryService.deleteRecursively(workDirectory);
        }
    }

    private Optional<WaveformSource> openSource(LocalizationJobVo job, List<JobArtifactVo> artifacts, Path workDirectory) {
        for (JobArtifactType type : List.of(JobArtifactType.NARRATION_AUDIO, JobArtifactType.NARRATED_VIDEO, JobArtifactType.BURNED_VIDEO)) {
            Optional<JobArtifactVo> artifact = latestArtifact(artifacts, type);
            if (artifact.isPresent()) {
                return Optional.of(writeResource(
                        artifactService.openArtifact(job.jobId(), artifact.get().artifactId()),
                        workDirectory.resolve("waveform-" + artifact.get().artifactId() + "-" + safeFilename(artifact.get().filename())),
                        sourceType(type),
                        durationSeconds(job),
                        artifact.get().artifactId(),
                        artifact.get().contentSha256()
                ));
            }
        }
        try {
            return Optional.of(writeResource(
                    mediaUploadService.openSourceMedia(job.videoId()),
                    workDirectory.resolve("waveform-source-media"),
                    NarrationWaveformSourceType.SOURCE_MEDIA,
                    durationSeconds(job),
                    "",
                    sourceMediaSignature(job)
            ));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private Optional<JobArtifactVo> latestArtifact(List<JobArtifactVo> artifacts, JobArtifactType type) {
        return artifacts.stream()
                .filter(artifact -> artifact.type() == type)
                .max(Comparator.comparing(JobArtifactVo::createdAt));
    }

    private WaveformSource writeResource(
            StoredObjectResourceBo resource,
            Path destination,
            NarrationWaveformSourceType type,
            double durationSeconds,
            String artifactId,
            String contentSha256
    ) {
        try {
            Files.createDirectories(destination.getParent());
            try (var inputStream = resource.inputStream()) {
                Files.copy(inputStream, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return new WaveformSource(type, destination, durationSeconds, artifactId, contentSha256);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to prepare waveform source.", ex);
        }
    }

    private Optional<NarrationWaveformVo> findReusableWaveform(
            String jobId,
            List<JobArtifactVo> artifacts,
            WaveformSource source,
            int bucketCount
    ) {
        return artifacts.stream()
                .filter(artifact -> artifact.type() == JobArtifactType.NARRATION_WAVEFORM)
                .sorted(Comparator.comparing(JobArtifactVo::createdAt).reversed())
                .map(artifact -> readWaveformArtifact(jobId, artifact))
                .flatMap(Optional::stream)
                .filter(waveform -> waveform.bucketCount() == bucketCount)
                .filter(waveform -> waveform.sourceType().equals(source.type().name()))
                .filter(waveform -> normalize(waveform.sourceArtifactId()).equals(normalize(source.artifactId())))
                .filter(waveform -> normalize(waveform.sourceContentSha256()).equals(normalize(source.contentSha256())))
                .findFirst()
                .map(waveform -> new NarrationWaveformVo(
                        waveform.jobId(),
                        waveform.status(),
                        waveform.sourceType(),
                        waveform.bucketCount(),
                        waveform.durationSeconds(),
                        waveform.buckets(),
                        waveform.fallbackReason(),
                        waveform.artifactId(),
                        waveform.sourceArtifactId(),
                        waveform.sourceContentSha256(),
                        true,
                        waveform.contentSha256(),
                        waveform.generatedAt()
                ));
    }

    private Optional<NarrationWaveformVo> readWaveformArtifact(String jobId, JobArtifactVo artifact) {
        try {
            StoredObjectResourceBo resource = artifactService.openArtifact(jobId, artifact.artifactId());
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            resource.inputStream().transferTo(outputStream);
            NarrationWaveformVo waveform = objectMapper.readValue(outputStream.toByteArray(), NarrationWaveformVo.class);
            return Optional.of(new NarrationWaveformVo(
                    waveform.jobId(),
                    waveform.status(),
                    waveform.sourceType(),
                    waveform.bucketCount(),
                    waveform.durationSeconds(),
                    waveform.buckets(),
                    waveform.fallbackReason(),
                    artifact.artifactId(),
                    waveform.sourceArtifactId(),
                    waveform.sourceContentSha256(),
                    artifact.cacheHit(),
                    artifact.contentSha256(),
                    waveform.generatedAt() == null ? artifact.createdAt() : waveform.generatedAt()
            ));
        } catch (IOException | RuntimeException ex) {
            return Optional.empty();
        }
    }

    private NarrationWaveformVo persistWaveform(String jobId, NarrationWaveformVo waveform) {
        byte[] content = toArtifactBytes(waveform);
        JobArtifactVo artifact = artifactService.createArtifact(new com.linguaframe.job.domain.bo.CreateJobArtifactCommand(
                jobId,
                JobArtifactType.NARRATION_WAVEFORM,
                "narration-waveform-%d-buckets.json".formatted(waveform.bucketCount()),
                "application/json",
                content
        ));
        return new NarrationWaveformVo(
                waveform.jobId(),
                waveform.status(),
                waveform.sourceType(),
                waveform.bucketCount(),
                waveform.durationSeconds(),
                waveform.buckets(),
                waveform.fallbackReason(),
                artifact.artifactId(),
                waveform.sourceArtifactId(),
                waveform.sourceContentSha256(),
                artifact.cacheHit(),
                artifact.contentSha256(),
                artifact.createdAt()
        );
    }

    private byte[] toArtifactBytes(NarrationWaveformVo waveform) {
        try {
            return objectMapper.writeValueAsBytes(waveform);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize narration waveform artifact.", ex);
        }
    }

    private double durationSeconds(LocalizationJobVo job) {
        try {
            Integer durationSeconds = mediaUploadService.getUpload(job.videoId()).durationSeconds();
            if (durationSeconds != null && durationSeconds > 0) {
                return durationSeconds.doubleValue();
            }
        } catch (RuntimeException ignored) {
            return 1.0;
        }
        return 1.0;
    }

    private String sourceMediaSignature(LocalizationJobVo job) {
        try {
            var upload = mediaUploadService.getUpload(job.videoId());
            return "%s:%s:%s:%s".formatted(
                    upload.videoId(),
                    upload.fileSizeBytes(),
                    upload.durationSeconds(),
                    upload.createdAt()
            );
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private NarrationWaveformSourceType sourceType(JobArtifactType type) {
        return switch (type) {
            case NARRATION_AUDIO -> NarrationWaveformSourceType.NARRATION_AUDIO;
            case NARRATED_VIDEO -> NarrationWaveformSourceType.NARRATED_VIDEO;
            case BURNED_VIDEO -> NarrationWaveformSourceType.BURNED_VIDEO;
            default -> NarrationWaveformSourceType.NONE;
        };
    }

    private NarrationWaveformVo empty(
            String jobId,
            String status,
            NarrationWaveformSourceType sourceType,
            int bucketCount,
            String fallbackReason
    ) {
        return new NarrationWaveformVo(
                jobId,
                status,
                sourceType.name(),
                bucketCount,
                BigDecimal.ZERO.setScale(3),
                List.of(),
                fallbackReason,
                "",
                "",
                "",
                false,
                "",
                null
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value;
    }

    private int capBucketCount(Integer requestedBucketCount) {
        int value = requestedBucketCount == null ? DEFAULT_BUCKET_COUNT : requestedBucketCount;
        return Math.max(MIN_BUCKET_COUNT, Math.min(MAX_BUCKET_COUNT, value));
    }

    private String safeFallbackReason(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "Waveform analysis failed safely.";
        }
        return message.replaceAll("(/[A-Za-z0-9._ -]+)+", "[path]");
    }

    private String safeFilename(String filename) {
        String value = filename == null || filename.isBlank() ? "artifact" : filename;
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private record WaveformSource(
            NarrationWaveformSourceType type,
            Path path,
            double durationSeconds,
            String artifactId,
            String contentSha256
    ) {
    }
}
