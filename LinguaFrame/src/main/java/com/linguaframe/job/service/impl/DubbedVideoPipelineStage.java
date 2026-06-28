package com.linguaframe.job.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.LocalizationPipelineStage;
import com.linguaframe.media.domain.bo.DubbedVideoBo;
import com.linguaframe.media.domain.bo.ReplaceVideoAudioCommand;
import com.linguaframe.media.service.FfmpegAudioReplacementService;
import com.linguaframe.media.service.MediaWorkDirectoryService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class DubbedVideoPipelineStage implements LocalizationPipelineStage {

    private final LinguaFrameProperties properties;
    private final MediaWorkDirectoryService workDirectoryService;
    private final JobArtifactService artifactService;
    private final FfmpegAudioReplacementService audioReplacementService;

    public DubbedVideoPipelineStage(
            LinguaFrameProperties properties,
            MediaWorkDirectoryService workDirectoryService,
            JobArtifactService artifactService,
            FfmpegAudioReplacementService audioReplacementService
    ) {
        this.properties = properties;
        this.workDirectoryService = workDirectoryService;
        this.artifactService = artifactService;
        this.audioReplacementService = audioReplacementService;
    }

    @Override
    public LocalizationJobStage stage() {
        return LocalizationJobStage.DUBBED_VIDEO_DELIVERY;
    }

    @Override
    public void execute(LocalizationJobExecutionContextBo context) {
        if (!properties.getTts().isEnabled() || !properties.getFfmpeg().isBurnInEnabled()) {
            return;
        }

        String jobId = context.job().id();
        List<JobArtifactVo> artifacts = artifactService.listArtifacts(jobId);
        Optional<JobArtifactVo> videoArtifact = latestArtifact(artifacts, JobArtifactType.BURNED_VIDEO);
        Optional<JobArtifactVo> audioArtifact = latestArtifact(artifacts, JobArtifactType.DUBBING_AUDIO);
        if (videoArtifact.isEmpty() || audioArtifact.isEmpty()) {
            return;
        }

        Path workDirectory = workDirectoryService.createJobWorkDirectory(jobId);
        try {
            Path inputVideoPath = workDirectory.resolve("burned-video.mp4");
            Path inputAudioPath = workDirectory.resolve("dubbing-audio.mp3");
            Path outputVideoPath = workDirectory.resolve("dubbed-video.mp4");
            copyArtifact(jobId, videoArtifact.get(), inputVideoPath);
            copyArtifact(jobId, audioArtifact.get(), inputAudioPath);
            DubbedVideoBo dubbedVideo = audioReplacementService.replaceAudio(new ReplaceVideoAudioCommand(
                    jobId,
                    inputVideoPath,
                    inputAudioPath,
                    outputVideoPath
            ));
            artifactService.createArtifact(new CreateJobArtifactCommand(
                    jobId,
                    JobArtifactType.DUBBED_VIDEO,
                    dubbedVideo.filename(),
                    dubbedVideo.contentType(),
                    dubbedVideo.content()
            ));
        } finally {
            workDirectoryService.deleteRecursively(workDirectory);
        }
    }

    private Optional<JobArtifactVo> latestArtifact(List<JobArtifactVo> artifacts, JobArtifactType type) {
        return artifacts.stream()
                .filter(artifact -> artifact.type() == type)
                .max(Comparator.comparing(JobArtifactVo::createdAt));
    }

    private void copyArtifact(String jobId, JobArtifactVo artifact, Path targetPath) {
        StoredObjectResourceBo resource = artifactService.openArtifact(jobId, artifact.artifactId());
        try (InputStream inputStream = resource.inputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to prepare media artifact for dubbed video delivery.", ex);
        }
    }
}
