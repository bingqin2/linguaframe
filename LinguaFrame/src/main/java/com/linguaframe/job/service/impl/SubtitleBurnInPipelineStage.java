package com.linguaframe.job.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.ArtifactCacheService;
import com.linguaframe.job.service.LocalizationPipelineStage;
import com.linguaframe.job.service.SubtitleExportService;
import com.linguaframe.job.service.SubtitleService;
import com.linguaframe.media.domain.bo.BurnInSubtitlesCommand;
import com.linguaframe.media.domain.bo.BurnedVideoBo;
import com.linguaframe.media.service.FfmpegSubtitleBurnInService;
import com.linguaframe.media.service.MediaWorkDirectoryService;
import com.linguaframe.storage.service.ObjectStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Component
public class SubtitleBurnInPipelineStage implements LocalizationPipelineStage {

    private final LinguaFrameProperties properties;
    private final ObjectStorageService objectStorageService;
    private final MediaWorkDirectoryService workDirectoryService;
    private final FfmpegSubtitleBurnInService burnInService;
    private final SubtitleService subtitleService;
    private final SubtitleExportService subtitleExportService;
    private final JobArtifactService artifactService;
    private final ArtifactCacheService artifactCacheService;

    public SubtitleBurnInPipelineStage(
            LinguaFrameProperties properties,
            ObjectStorageService objectStorageService,
            MediaWorkDirectoryService workDirectoryService,
            FfmpegSubtitleBurnInService burnInService,
            SubtitleService subtitleService,
            SubtitleExportService subtitleExportService,
            JobArtifactService artifactService
    ) {
        this(
                properties,
                objectStorageService,
                workDirectoryService,
                burnInService,
                subtitleService,
                subtitleExportService,
                artifactService,
                (context, type) -> java.util.Optional.empty()
        );
    }

    @Autowired
    public SubtitleBurnInPipelineStage(
            LinguaFrameProperties properties,
            ObjectStorageService objectStorageService,
            MediaWorkDirectoryService workDirectoryService,
            FfmpegSubtitleBurnInService burnInService,
            SubtitleService subtitleService,
            SubtitleExportService subtitleExportService,
            JobArtifactService artifactService,
            ArtifactCacheService artifactCacheService
    ) {
        this.properties = properties;
        this.objectStorageService = objectStorageService;
        this.workDirectoryService = workDirectoryService;
        this.burnInService = burnInService;
        this.subtitleService = subtitleService;
        this.subtitleExportService = subtitleExportService;
        this.artifactService = artifactService;
        this.artifactCacheService = artifactCacheService;
    }

    @Override
    public LocalizationJobStage stage() {
        return LocalizationJobStage.SUBTITLE_BURN_IN;
    }

    @Override
    public void execute(LocalizationJobExecutionContextBo context) {
        if (!properties.getFfmpeg().isBurnInEnabled()) {
            return;
        }
        if (reuseCachedArtifact(context, JobArtifactType.BURNED_VIDEO)) {
            return;
        }

        String jobId = context.job().id();
        String targetLanguage = context.job().targetLanguage();
        List<SubtitleSegmentVo> subtitles = subtitleService.listSubtitles(jobId, targetLanguage);
        if (subtitles.isEmpty()) {
            throw new IllegalStateException("Target subtitles not found for subtitle burn-in.");
        }

        Path workDirectory = workDirectoryService.createJobWorkDirectory(jobId);
        try {
            Path inputVideoPath = workDirectory.resolve("source-video.mp4");
            Path subtitlePath = workDirectory.resolve("target-subtitles.srt");
            Path outputVideoPath = workDirectory.resolve("burned-video.mp4");
            copySourceVideo(context.message().sourceObjectKey(), inputVideoPath);
            writeTargetSubtitles(subtitles, subtitlePath);
            BurnedVideoBo burnedVideo = burnInService.burnInSubtitles(new BurnInSubtitlesCommand(
                    jobId,
                    inputVideoPath,
                    subtitlePath,
                    outputVideoPath,
                    context.job().subtitleStylePreset()
            ));
            artifactService.createArtifact(new CreateJobArtifactCommand(
                    jobId,
                    JobArtifactType.BURNED_VIDEO,
                    burnedVideo.filename(),
                    burnedVideo.contentType(),
                    burnedVideo.content()
            ));
        } finally {
            workDirectoryService.deleteRecursively(workDirectory);
        }
    }

    private boolean reuseCachedArtifact(LocalizationJobExecutionContextBo context, JobArtifactType type) {
        return artifactCacheService.tryReuseArtifact(context, type)
                .map(artifact -> {
                    context.recordCacheHit(artifact);
                    return true;
                })
                .orElse(false);
    }

    private void copySourceVideo(String objectKey, Path inputVideoPath) {
        try (InputStream inputStream = objectStorageService.open(objectKey)) {
            Files.copy(inputStream, inputVideoPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to prepare source video for subtitle burn-in.", ex);
        }
    }

    private void writeTargetSubtitles(List<SubtitleSegmentVo> subtitles, Path subtitlePath) {
        try {
            Files.write(subtitlePath, subtitleExportService.exportSubtitleSrt(subtitles));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to prepare target subtitles for subtitle burn-in.", ex);
        }
    }
}
