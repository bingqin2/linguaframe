package com.linguaframe.job.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.dto.PublishReviewedSubtitlesRequest;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.SubtitleDraftExportFormat;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.ReviewedSubtitlePublishVo;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.ReviewedSubtitleDeliveryService;
import com.linguaframe.job.service.SubtitleDraftService;
import com.linguaframe.media.repository.VideoRepository;
import com.linguaframe.media.domain.bo.BurnInSubtitlesCommand;
import com.linguaframe.media.domain.bo.BurnedVideoBo;
import com.linguaframe.media.service.FfmpegSubtitleBurnInService;
import com.linguaframe.media.service.MediaWorkDirectoryService;
import com.linguaframe.storage.service.ObjectStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReviewedSubtitleDeliveryServiceImpl implements ReviewedSubtitleDeliveryService {

    private final LinguaFrameProperties properties;
    private final SubtitleDraftService subtitleDraftService;
    private final JobArtifactService artifactService;
    private final ObjectStorageService objectStorageService;
    private final MediaWorkDirectoryService workDirectoryService;
    private final FfmpegSubtitleBurnInService burnInService;
    private final LocalizationJobRepository jobRepository;
    private final VideoRepository videoRepository;

    @Autowired
    public ReviewedSubtitleDeliveryServiceImpl(
            LinguaFrameProperties properties,
            SubtitleDraftService subtitleDraftService,
            JobArtifactService artifactService,
            ObjectStorageService objectStorageService,
            MediaWorkDirectoryService workDirectoryService,
            FfmpegSubtitleBurnInService burnInService,
            LocalizationJobRepository jobRepository,
            VideoRepository videoRepository
    ) {
        this.properties = properties;
        this.subtitleDraftService = subtitleDraftService;
        this.artifactService = artifactService;
        this.objectStorageService = objectStorageService;
        this.workDirectoryService = workDirectoryService;
        this.burnInService = burnInService;
        this.jobRepository = jobRepository;
        this.videoRepository = videoRepository;
    }

    public ReviewedSubtitleDeliveryServiceImpl(
            LinguaFrameProperties properties,
            SubtitleDraftService subtitleDraftService,
            JobArtifactService artifactService,
            ObjectStorageService objectStorageService,
            MediaWorkDirectoryService workDirectoryService,
            FfmpegSubtitleBurnInService burnInService
    ) {
        this(
                properties,
                subtitleDraftService,
                artifactService,
                objectStorageService,
                workDirectoryService,
                burnInService,
                null,
                null
        );
    }

    @Override
    public ReviewedSubtitlePublishVo publish(String jobId, PublishReviewedSubtitlesRequest request) {
        String language = normalizeLanguage(request == null ? null : request.language());
        boolean includeBurnedVideo = request != null && request.includeBurnedVideo();
        List<JobArtifactVo> artifacts = new ArrayList<>();

        byte[] json = subtitleDraftService.exportDraft(jobId, language, SubtitleDraftExportFormat.JSON);
        byte[] srt = subtitleDraftService.exportDraft(jobId, language, SubtitleDraftExportFormat.SRT);
        byte[] vtt = subtitleDraftService.exportDraft(jobId, language, SubtitleDraftExportFormat.VTT);

        artifacts.add(createArtifact(jobId, JobArtifactType.REVIEWED_SUBTITLE_JSON,
                "reviewed-subtitles." + language + ".json", "application/json", json));
        artifacts.add(createArtifact(jobId, JobArtifactType.REVIEWED_SUBTITLE_SRT,
                "reviewed-subtitles." + language + ".srt", "application/x-subrip;charset=UTF-8", srt));
        artifacts.add(createArtifact(jobId, JobArtifactType.REVIEWED_SUBTITLE_VTT,
                "reviewed-subtitles." + language + ".vtt", "text/vtt;charset=UTF-8", vtt));

        boolean burnedVideoCreated = false;
        if (includeBurnedVideo && properties.getFfmpeg().isBurnInEnabled()) {
            artifacts.add(createReviewedBurnedVideo(jobId, srt));
            burnedVideoCreated = true;
        }

        return new ReviewedSubtitlePublishVo(
                jobId,
                language,
                includeBurnedVideo,
                burnedVideoCreated,
                List.copyOf(artifacts)
        );
    }

    private JobArtifactVo createReviewedBurnedVideo(String jobId, byte[] srt) {
        Path workDirectory = workDirectoryService.createJobWorkDirectory(jobId);
        try {
            Path inputVideoPath = workDirectory.resolve("source-video.mp4");
            Path subtitlePath = workDirectory.resolve("reviewed-subtitles.srt");
            Path outputVideoPath = workDirectory.resolve("reviewed-burned-video.mp4");
            copySourceVideo(sourceObjectKey(jobId), inputVideoPath);
            Files.write(subtitlePath, srt);
            BurnedVideoBo burnedVideo = burnInService.burnInSubtitles(new BurnInSubtitlesCommand(
                    jobId,
                    inputVideoPath,
                    subtitlePath,
                    outputVideoPath
            ));
            return createArtifact(
                    jobId,
                    JobArtifactType.REVIEWED_BURNED_VIDEO,
                    "reviewed-burned-video.mp4",
                    burnedVideo.contentType(),
                    burnedVideo.content()
            );
        } catch (RuntimeException | IOException ex) {
            throw new IllegalStateException("Failed to create reviewed burned video.", ex);
        } finally {
            workDirectoryService.deleteRecursively(workDirectory);
        }
    }

    private void copySourceVideo(String objectKey, Path inputVideoPath) {
        try (InputStream inputStream = objectStorageService.open(objectKey)) {
            Files.copy(inputStream, inputVideoPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to prepare source video for reviewed burn-in.", ex);
        }
    }

    private String sourceObjectKey(String jobId) {
        if (jobRepository == null || videoRepository == null) {
            return "source-videos/" + jobId + "/source.mp4";
        }
        return jobRepository.findById(jobId)
                .flatMap(job -> videoRepository.findById(job.videoId()))
                .map(com.linguaframe.media.domain.entity.VideoRecord::sourceObjectKey)
                .orElseThrow(() -> new java.util.NoSuchElementException("Source video not found for job " + jobId + "."));
    }

    private JobArtifactVo createArtifact(
            String jobId,
            JobArtifactType type,
            String filename,
            String contentType,
            byte[] content
    ) {
        return artifactService.createArtifact(new CreateJobArtifactCommand(
                jobId,
                type,
                filename,
                contentType,
                content
        ));
    }

    private String normalizeLanguage(String language) {
        return language == null || language.isBlank() ? "zh-CN" : language.trim();
    }
}
