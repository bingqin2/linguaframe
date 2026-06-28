package com.linguaframe.media.service.impl;

import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.SubtitleStylePreset;
import com.linguaframe.job.domain.enums.TranslationStyle;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.JobDispatchOutboxService;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.domain.vo.MediaUploadDetailVo;
import com.linguaframe.media.domain.vo.MediaUploadValidationVo;
import com.linguaframe.media.domain.vo.MediaUploadVo;
import com.linguaframe.media.repository.VideoRepository;
import com.linguaframe.media.service.MediaUploadService;
import com.linguaframe.media.service.MediaUploadValidationService;
import com.linguaframe.storage.domain.bo.StoreObjectCommand;
import com.linguaframe.storage.service.ObjectStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class MediaUploadServiceImpl implements MediaUploadService {

    private static final String DEFAULT_TARGET_LANGUAGE = "zh-CN";

    private final MediaUploadValidationService validationService;
    private final ObjectStorageService objectStorageService;
    private final VideoRepository videoRepository;
    private final LocalizationJobRepository jobRepository;
    private final JobDispatchOutboxService dispatchOutboxService;

    public MediaUploadServiceImpl(
            MediaUploadValidationService validationService,
            ObjectStorageService objectStorageService,
            VideoRepository videoRepository,
            LocalizationJobRepository jobRepository,
            JobDispatchOutboxService dispatchOutboxService
    ) {
        this.validationService = validationService;
        this.objectStorageService = objectStorageService;
        this.videoRepository = videoRepository;
        this.jobRepository = jobRepository;
        this.dispatchOutboxService = dispatchOutboxService;
    }

    @Override
    @Transactional
    public MediaUploadVo createUpload(
            MultipartFile file,
            String targetLanguage,
            String ttsVoice,
            String translationStyle,
            String subtitleStylePreset
    ) {
        String normalizedTranslationStyle = TranslationStyle.parse(translationStyle).name();
        String normalizedSubtitleStylePreset = SubtitleStylePreset.parse(subtitleStylePreset).name();
        MediaUploadValidationVo validation = validationService.validate(file);
        if (!validation.valid()) {
            throw new IllegalArgumentException(validation.code().name() + ": " + validation.message());
        }

        String videoId = UUID.randomUUID().toString();
        String jobId = UUID.randomUUID().toString();
        String filename = validation.filename();
        String objectKey = "source-videos/" + videoId + "/" + filename;
        String normalizedTargetLanguage = normalizeTargetLanguage(targetLanguage);
        String normalizedTtsVoice = normalizeTtsVoice(ttsVoice);
        Instant createdAt = Instant.now();

        try {
            objectStorageService.store(new StoreObjectCommand(
                    objectKey,
                    validation.contentType(),
                    validation.fileSizeBytes(),
                    file.getInputStream()
            ));
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException("Failed to store source video.", ex);
        }

        VideoRecord video = new VideoRecord(
                videoId,
                filename,
                validation.contentType(),
                validation.fileSizeBytes(),
                validation.durationSeconds(),
                objectKey,
                MediaUploadStatus.UPLOADED,
                createdAt
        );
        LocalizationJobRecord job = new LocalizationJobRecord(
                jobId,
                videoId,
                normalizedTargetLanguage,
                normalizedTtsVoice,
                normalizedTranslationStyle,
                normalizedSubtitleStylePreset,
                LocalizationJobStatus.QUEUED,
                createdAt
        );
        videoRepository.save(video);
        jobRepository.save(job);
        dispatchOutboxService.enqueueLocalizationJobQueued(video, job);

        return new MediaUploadVo(
                videoId,
                jobId,
                filename,
                validation.contentType(),
                validation.fileSizeBytes(),
                validation.durationSeconds(),
                objectKey,
                MediaUploadStatus.UPLOADED,
                LocalizationJobStatus.QUEUED,
                normalizedTargetLanguage,
                normalizedTtsVoice,
                normalizedTranslationStyle,
                normalizedSubtitleStylePreset,
                createdAt
        );
    }

    @Override
    public MediaUploadDetailVo getUpload(String videoId) {
        VideoRecord record = videoRepository.findById(videoId)
                .orElseThrow(() -> new NoSuchElementException("Media upload not found."));
        return new MediaUploadDetailVo(
                record.id(),
                record.originalFilename(),
                record.contentType(),
                record.fileSizeBytes(),
                record.durationSeconds(),
                record.status(),
                record.createdAt()
        );
    }

    @Override
    public StoredObjectResourceBo openSourceMedia(String videoId) {
        VideoRecord record = videoRepository.findById(videoId)
                .orElseThrow(() -> new NoSuchElementException("Media upload not found."));
        return new StoredObjectResourceBo(
                record.originalFilename(),
                record.contentType(),
                record.fileSizeBytes(),
                objectStorageService.open(record.sourceObjectKey())
        );
    }

    private String normalizeTargetLanguage(String targetLanguage) {
        if (!StringUtils.hasText(targetLanguage)) {
            return DEFAULT_TARGET_LANGUAGE;
        }
        return targetLanguage.trim();
    }

    private String normalizeTtsVoice(String ttsVoice) {
        if (!StringUtils.hasText(ttsVoice)) {
            return null;
        }
        return ttsVoice.trim();
    }
}
