package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.NarratedVideoGenerationVo;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.job.service.NarratedVideoService;
import com.linguaframe.media.domain.bo.DubbedVideoBo;
import com.linguaframe.media.domain.bo.ReplaceVideoAudioCommand;
import com.linguaframe.media.service.FfmpegAudioReplacementService;
import com.linguaframe.media.service.MediaUploadService;
import com.linguaframe.media.service.MediaWorkDirectoryService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class NarratedVideoServiceImpl implements NarratedVideoService {

    private final JobArtifactService artifactService;
    private final LocalizationJobQueryService queryService;
    private final MediaUploadService mediaUploadService;
    private final MediaWorkDirectoryService workDirectoryService;
    private final FfmpegAudioReplacementService audioReplacementService;

    public NarratedVideoServiceImpl(
            JobArtifactService artifactService,
            LocalizationJobQueryService queryService,
            MediaUploadService mediaUploadService,
            MediaWorkDirectoryService workDirectoryService,
            FfmpegAudioReplacementService audioReplacementService
    ) {
        this.artifactService = artifactService;
        this.queryService = queryService;
        this.mediaUploadService = mediaUploadService;
        this.workDirectoryService = workDirectoryService;
        this.audioReplacementService = audioReplacementService;
    }

    @Override
    public NarratedVideoGenerationVo generateVideo(String jobId) {
        LocalizationJobVo job = queryService.getJob(jobId);
        List<JobArtifactVo> artifacts = artifactService.listArtifacts(jobId);
        JobArtifactVo narrationAudio = latestArtifact(artifacts, JobArtifactType.NARRATION_AUDIO)
                .orElseThrow(() -> new IllegalArgumentException("Narration audio is required before generating narrated video."));
        BaseVideoSelection baseVideo = selectBaseVideo(job, artifacts);

        Path workDirectory = workDirectoryService.createJobWorkDirectory(jobId);
        try {
            Path inputVideoPath = workDirectory.resolve("base-video.mp4");
            Path inputAudioPath = workDirectory.resolve("narration-audio.mp3");
            Path outputVideoPath = workDirectory.resolve("narrated-video.mp4");
            copyBaseVideo(jobId, baseVideo, inputVideoPath);
            copyArtifact(jobId, narrationAudio, inputAudioPath);
            DubbedVideoBo narratedVideo = audioReplacementService.replaceAudio(new ReplaceVideoAudioCommand(
                    jobId,
                    inputVideoPath,
                    inputAudioPath,
                    outputVideoPath,
                    "narrated-video.mp4"
            ));
            JobArtifactVo artifact = artifactService.createArtifact(new CreateJobArtifactCommand(
                    jobId,
                    JobArtifactType.NARRATED_VIDEO,
                    narratedVideo.filename(),
                    narratedVideo.contentType(),
                    narratedVideo.content()
            ));
            return new NarratedVideoGenerationVo(
                    jobId,
                    artifact.artifactId(),
                    artifact.filename(),
                    artifact.contentType(),
                    artifact.sizeBytes(),
                    baseVideo.type(),
                    narrationAudio.artifactId(),
                    "READY"
            );
        } finally {
            workDirectoryService.deleteRecursively(workDirectory);
        }
    }

    private BaseVideoSelection selectBaseVideo(LocalizationJobVo job, List<JobArtifactVo> artifacts) {
        Optional<JobArtifactVo> reviewed = latestArtifact(artifacts, JobArtifactType.REVIEWED_BURNED_VIDEO);
        if (reviewed.isPresent()) {
            return new BaseVideoSelection("REVIEWED_BURNED_VIDEO", reviewed.get(), null);
        }
        Optional<JobArtifactVo> burned = latestArtifact(artifacts, JobArtifactType.BURNED_VIDEO);
        if (burned.isPresent()) {
            return new BaseVideoSelection("BURNED_VIDEO", burned.get(), null);
        }
        Optional<JobArtifactVo> dubbed = latestArtifact(artifacts, JobArtifactType.DUBBED_VIDEO);
        if (dubbed.isPresent()) {
            return new BaseVideoSelection("DUBBED_VIDEO", dubbed.get(), null);
        }
        try {
            return new BaseVideoSelection("SOURCE_VIDEO", null, mediaUploadService.openSourceMedia(job.videoId()));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("A source or generated video is required before generating narrated video.", ex);
        }
    }

    private Optional<JobArtifactVo> latestArtifact(List<JobArtifactVo> artifacts, JobArtifactType type) {
        return artifacts.stream()
                .filter(artifact -> artifact.type() == type)
                .max(Comparator.comparing(JobArtifactVo::createdAt));
    }

    private void copyBaseVideo(String jobId, BaseVideoSelection baseVideo, Path targetPath) {
        if (baseVideo.artifact() != null) {
            copyArtifact(jobId, baseVideo.artifact(), targetPath);
            return;
        }
        copyResource(baseVideo.sourceMedia(), targetPath, "Failed to prepare source video for narrated video.");
    }

    private void copyArtifact(String jobId, JobArtifactVo artifact, Path targetPath) {
        StoredObjectResourceBo resource = artifactService.openArtifact(jobId, artifact.artifactId());
        copyResource(resource, targetPath, "Failed to prepare media artifact for narrated video.");
    }

    private void copyResource(StoredObjectResourceBo resource, Path targetPath, String failureMessage) {
        try (InputStream inputStream = resource.inputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException(failureMessage, ex);
        }
    }

    private record BaseVideoSelection(
            String type,
            JobArtifactVo artifact,
            StoredObjectResourceBo sourceMedia
    ) {
    }
}
