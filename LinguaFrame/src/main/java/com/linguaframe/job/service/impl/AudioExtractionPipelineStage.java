package com.linguaframe.job.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.LocalizationPipelineStage;
import com.linguaframe.media.domain.bo.ExtractAudioCommand;
import com.linguaframe.media.domain.bo.ExtractedAudioBo;
import com.linguaframe.media.service.FfmpegAudioExtractionService;
import com.linguaframe.media.service.MediaWorkDirectoryService;
import com.linguaframe.storage.service.ObjectStorageService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class AudioExtractionPipelineStage implements LocalizationPipelineStage {

    private final LinguaFrameProperties properties;
    private final ObjectStorageService objectStorageService;
    private final MediaWorkDirectoryService workDirectoryService;
    private final FfmpegAudioExtractionService audioExtractionService;
    private final JobArtifactService artifactService;

    public AudioExtractionPipelineStage(
            LinguaFrameProperties properties,
            ObjectStorageService objectStorageService,
            MediaWorkDirectoryService workDirectoryService,
            FfmpegAudioExtractionService audioExtractionService,
            JobArtifactService artifactService
    ) {
        this.properties = properties;
        this.objectStorageService = objectStorageService;
        this.workDirectoryService = workDirectoryService;
        this.audioExtractionService = audioExtractionService;
        this.artifactService = artifactService;
    }

    @Override
    public LocalizationJobStage stage() {
        return LocalizationJobStage.AUDIO_EXTRACTION;
    }

    @Override
    public void execute(LocalizationJobExecutionContextBo context) {
        if (!properties.getFfmpeg().isAudioEnabled()) {
            return;
        }

        Path workDirectory = workDirectoryService.createJobWorkDirectory(context.job().id());
        try {
            Path inputVideoPath = workDirectory.resolve("source-video");
            Path outputAudioPath = workDirectory.resolve("audio.wav");
            copySourceVideo(context.message().sourceObjectKey(), inputVideoPath);
            ExtractedAudioBo extractedAudio = audioExtractionService.extractAudio(new ExtractAudioCommand(
                    context.job().id(),
                    inputVideoPath,
                    outputAudioPath
            ));
            artifactService.createArtifact(new CreateJobArtifactCommand(
                    context.job().id(),
                    JobArtifactType.EXTRACTED_AUDIO,
                    extractedAudio.filename(),
                    extractedAudio.contentType(),
                    extractedAudio.content()
            ));
        } finally {
            workDirectoryService.deleteRecursively(workDirectory);
        }
    }

    private void copySourceVideo(String objectKey, Path inputVideoPath) {
        try (InputStream inputStream = objectStorageService.open(objectKey)) {
            Files.copy(inputStream, inputVideoPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to prepare source video for audio extraction.", ex);
        }
    }
}
