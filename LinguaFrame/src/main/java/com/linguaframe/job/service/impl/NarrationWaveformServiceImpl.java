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
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public NarrationWaveformServiceImpl(
            JobArtifactService artifactService,
            LocalizationJobQueryService queryService,
            MediaUploadService mediaUploadService,
            MediaWorkDirectoryService workDirectoryService,
            FfmpegAudioWaveformService waveformService
    ) {
        this.artifactService = artifactService;
        this.queryService = queryService;
        this.mediaUploadService = mediaUploadService;
        this.workDirectoryService = workDirectoryService;
        this.waveformService = waveformService;
    }

    @Override
    public NarrationWaveformVo getWaveform(String jobId, Integer requestedBucketCount) {
        int bucketCount = capBucketCount(requestedBucketCount);
        LocalizationJobVo job = queryService.getJob(jobId);
        Path workDirectory = workDirectoryService.createJobWorkDirectory(jobId);
        try {
            Optional<WaveformSource> source = openSource(job, workDirectory);
            if (source.isEmpty()) {
                return empty(jobId, "UNAVAILABLE", NarrationWaveformSourceType.NONE, bucketCount,
                        "No decoded audio source is available for this job.");
            }
            try {
                AudioWaveformBo waveform = waveformService.analyze(new AudioWaveformAnalyzeCommand(
                        source.get().path(),
                        bucketCount,
                        source.get().durationSeconds()
                ));
                return new NarrationWaveformVo(
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
                        ""
                );
            } catch (RuntimeException ex) {
                return empty(jobId, "FAILED_SAFE", source.get().type(), bucketCount, safeFallbackReason(ex));
            }
        } finally {
            workDirectoryService.deleteRecursively(workDirectory);
        }
    }

    private Optional<WaveformSource> openSource(LocalizationJobVo job, Path workDirectory) {
        List<JobArtifactVo> artifacts = artifactService.listArtifacts(job.jobId());
        for (JobArtifactType type : List.of(JobArtifactType.NARRATION_AUDIO, JobArtifactType.NARRATED_VIDEO, JobArtifactType.BURNED_VIDEO)) {
            Optional<JobArtifactVo> artifact = latestArtifact(artifacts, type);
            if (artifact.isPresent()) {
                return Optional.of(writeResource(
                        artifactService.openArtifact(job.jobId(), artifact.get().artifactId()),
                        workDirectory.resolve("waveform-" + artifact.get().artifactId() + "-" + safeFilename(artifact.get().filename())),
                        sourceType(type),
                        durationSeconds(job)
                ));
            }
        }
        try {
            return Optional.of(writeResource(
                    mediaUploadService.openSourceMedia(job.videoId()),
                    workDirectory.resolve("waveform-source-media"),
                    NarrationWaveformSourceType.SOURCE_MEDIA,
                    durationSeconds(job)
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
            double durationSeconds
    ) {
        try {
            Files.createDirectories(destination.getParent());
            try (var inputStream = resource.inputStream()) {
                Files.copy(inputStream, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return new WaveformSource(type, destination, durationSeconds);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to prepare waveform source.", ex);
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
                fallbackReason
        );
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
            double durationSeconds
    ) {
    }
}
