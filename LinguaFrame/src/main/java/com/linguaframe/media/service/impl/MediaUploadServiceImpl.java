package com.linguaframe.media.service.impl;

import com.linguaframe.job.domain.bo.TranslationGlossaryBo;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.SubtitlePolishingMode;
import com.linguaframe.job.domain.enums.SubtitleStylePreset;
import com.linguaframe.job.domain.enums.TranslationStyle;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.JobDispatchOutboxService;
import com.linguaframe.job.service.impl.TranslationGlossaryParser;
import com.linguaframe.common.security.DemoOwnerIdentityService;
import com.linguaframe.demo.service.DemoRunProfileService;
import com.linguaframe.demo.service.impl.InMemoryDemoRunProfileService;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
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
    private final TranslationGlossaryParser translationGlossaryParser;
    private final DemoRunProfileService demoRunProfileService;
    private final DemoOwnerIdentityService ownerIdentityService;

    public MediaUploadServiceImpl(
            MediaUploadValidationService validationService,
            ObjectStorageService objectStorageService,
            VideoRepository videoRepository,
            LocalizationJobRepository jobRepository,
            JobDispatchOutboxService dispatchOutboxService
    ) {
        this(validationService, objectStorageService, videoRepository, jobRepository, dispatchOutboxService, new TranslationGlossaryParser(), new InMemoryDemoRunProfileService(), () -> "demo-owner");
    }

    public MediaUploadServiceImpl(
            MediaUploadValidationService validationService,
            ObjectStorageService objectStorageService,
            VideoRepository videoRepository,
            LocalizationJobRepository jobRepository,
            JobDispatchOutboxService dispatchOutboxService,
            DemoOwnerIdentityService ownerIdentityService
    ) {
        this(validationService, objectStorageService, videoRepository, jobRepository, dispatchOutboxService, new TranslationGlossaryParser(), new InMemoryDemoRunProfileService(), ownerIdentityService);
    }

    @Autowired
    public MediaUploadServiceImpl(
            MediaUploadValidationService validationService,
            ObjectStorageService objectStorageService,
            VideoRepository videoRepository,
            LocalizationJobRepository jobRepository,
            JobDispatchOutboxService dispatchOutboxService,
            TranslationGlossaryParser translationGlossaryParser,
            DemoRunProfileService demoRunProfileService,
            DemoOwnerIdentityService ownerIdentityService
    ) {
        this.validationService = validationService;
        this.objectStorageService = objectStorageService;
        this.videoRepository = videoRepository;
        this.jobRepository = jobRepository;
        this.dispatchOutboxService = dispatchOutboxService;
        this.translationGlossaryParser = translationGlossaryParser;
        this.demoRunProfileService = demoRunProfileService;
        this.ownerIdentityService = ownerIdentityService;
    }

    @Override
    @Transactional
    public MediaUploadVo createUpload(
            MultipartFile file,
            String targetLanguage,
            String ttsVoice,
            String translationStyle,
            String subtitleStylePreset,
            String translationGlossary,
            String subtitlePolishingMode,
            String demoProfileId
    ) {
        String normalizedTranslationStyle = TranslationStyle.parse(translationStyle).name();
        String normalizedSubtitleStylePreset = SubtitleStylePreset.parse(subtitleStylePreset).name();
        String normalizedSubtitlePolishingMode = SubtitlePolishingMode.parse(subtitlePolishingMode).name();
        String normalizedDemoProfileId = normalizeDemoProfileId(demoProfileId);
        TranslationGlossaryBo parsedGlossary = translationGlossaryParser.parse(translationGlossary);
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
        String ownerId = ownerIdentityService.currentOwnerId();
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
                ownerId,
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
                ownerId,
                normalizedTargetLanguage,
                normalizedTtsVoice,
                normalizedTranslationStyle,
                normalizedSubtitleStylePreset,
                parsedGlossary.json(),
                parsedGlossary.hash(),
                parsedGlossary.entryCount(),
                normalizedSubtitlePolishingMode,
                normalizedDemoProfileId,
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
                parsedGlossary.entryCount(),
                parsedGlossary.hash(),
                normalizedSubtitlePolishingMode,
                normalizedDemoProfileId,
                createdAt
        );
    }

    @Override
    public MediaUploadDetailVo getUpload(String videoId) {
        VideoRecord record = videoRepository.findByIdAndOwnerId(videoId, ownerIdentityService.currentOwnerId())
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
        VideoRecord record = videoRepository.findByIdAndOwnerId(videoId, ownerIdentityService.currentOwnerId())
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

    private String normalizeDemoProfileId(String demoProfileId) {
        if (!StringUtils.hasText(demoProfileId)) {
            return null;
        }
        DemoRunProfileService profileService = demoRunProfileService == null
                ? new InMemoryDemoRunProfileService()
                : demoRunProfileService;
        return profileService.normalizeProfileId(demoProfileId.trim().toLowerCase(Locale.ROOT));
    }
}
