package com.linguaframe.media.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.TranslationGlossaryBo;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.SubtitlePolishingMode;
import com.linguaframe.job.domain.enums.SubtitleStylePreset;
import com.linguaframe.job.domain.enums.TranslationStyle;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.JobDispatchOutboxService;
import com.linguaframe.job.service.NarrationVoiceCatalogService;
import com.linguaframe.job.service.NarrationWorkspaceService;
import com.linguaframe.job.service.impl.NarrationQuickScriptParser;
import com.linguaframe.job.service.impl.NarrationVoiceCatalogServiceImpl;
import com.linguaframe.job.service.impl.TranslationGlossaryParser;
import com.linguaframe.common.quota.OwnerQuotaPreflightService;
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
import com.linguaframe.media.service.SourceMediaFingerprintService;
import com.linguaframe.storage.domain.bo.StoreObjectCommand;
import com.linguaframe.storage.service.ObjectStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
    private final OwnerQuotaPreflightService ownerQuotaPreflightService;
    private final SourceMediaFingerprintService sourceMediaFingerprintService;
    private final NarrationWorkspaceService narrationWorkspaceService;
    private final NarrationVoiceCatalogService narrationVoiceCatalogService;
    private final NarrationQuickScriptParser narrationQuickScriptParser;

    public MediaUploadServiceImpl(
            MediaUploadValidationService validationService,
            ObjectStorageService objectStorageService,
            VideoRepository videoRepository,
            LocalizationJobRepository jobRepository,
            JobDispatchOutboxService dispatchOutboxService
    ) {
        this(validationService, objectStorageService, videoRepository, jobRepository, dispatchOutboxService, new TranslationGlossaryParser(), new InMemoryDemoRunProfileService(), () -> "demo-owner", noopQuotaPreflightService(), new Sha256SourceMediaFingerprintService(), noopNarrationWorkspaceService(), defaultNarrationVoiceCatalogService(), new NarrationQuickScriptParser());
    }

    public MediaUploadServiceImpl(
            MediaUploadValidationService validationService,
            ObjectStorageService objectStorageService,
            VideoRepository videoRepository,
            LocalizationJobRepository jobRepository,
            JobDispatchOutboxService dispatchOutboxService,
            DemoOwnerIdentityService ownerIdentityService
    ) {
        this(validationService, objectStorageService, videoRepository, jobRepository, dispatchOutboxService, new TranslationGlossaryParser(), new InMemoryDemoRunProfileService(), ownerIdentityService, noopQuotaPreflightService(), new Sha256SourceMediaFingerprintService(), noopNarrationWorkspaceService(), defaultNarrationVoiceCatalogService(), new NarrationQuickScriptParser());
    }

    public MediaUploadServiceImpl(
            MediaUploadValidationService validationService,
            ObjectStorageService objectStorageService,
            VideoRepository videoRepository,
            LocalizationJobRepository jobRepository,
            JobDispatchOutboxService dispatchOutboxService,
            OwnerQuotaPreflightService ownerQuotaPreflightService
    ) {
        this(validationService, objectStorageService, videoRepository, jobRepository, dispatchOutboxService, new TranslationGlossaryParser(), new InMemoryDemoRunProfileService(), () -> "demo-owner", ownerQuotaPreflightService, new Sha256SourceMediaFingerprintService(), noopNarrationWorkspaceService(), defaultNarrationVoiceCatalogService(), new NarrationQuickScriptParser());
    }

    public MediaUploadServiceImpl(
            MediaUploadValidationService validationService,
            ObjectStorageService objectStorageService,
            VideoRepository videoRepository,
            LocalizationJobRepository jobRepository,
            JobDispatchOutboxService dispatchOutboxService,
            NarrationWorkspaceService narrationWorkspaceService
    ) {
        this(validationService, objectStorageService, videoRepository, jobRepository, dispatchOutboxService, new TranslationGlossaryParser(), new InMemoryDemoRunProfileService(), () -> "demo-owner", noopQuotaPreflightService(), new Sha256SourceMediaFingerprintService(), narrationWorkspaceService, defaultNarrationVoiceCatalogService(), new NarrationQuickScriptParser());
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
            DemoOwnerIdentityService ownerIdentityService,
            OwnerQuotaPreflightService ownerQuotaPreflightService,
            SourceMediaFingerprintService sourceMediaFingerprintService,
            NarrationWorkspaceService narrationWorkspaceService,
            NarrationVoiceCatalogService narrationVoiceCatalogService
    ) {
        this(validationService, objectStorageService, videoRepository, jobRepository, dispatchOutboxService, translationGlossaryParser, demoRunProfileService, ownerIdentityService, ownerQuotaPreflightService, sourceMediaFingerprintService, narrationWorkspaceService, narrationVoiceCatalogService, new NarrationQuickScriptParser());
    }

    private MediaUploadServiceImpl(
            MediaUploadValidationService validationService,
            ObjectStorageService objectStorageService,
            VideoRepository videoRepository,
            LocalizationJobRepository jobRepository,
            JobDispatchOutboxService dispatchOutboxService,
            TranslationGlossaryParser translationGlossaryParser,
            DemoRunProfileService demoRunProfileService,
            DemoOwnerIdentityService ownerIdentityService,
            OwnerQuotaPreflightService ownerQuotaPreflightService,
            SourceMediaFingerprintService sourceMediaFingerprintService,
            NarrationWorkspaceService narrationWorkspaceService,
            NarrationVoiceCatalogService narrationVoiceCatalogService,
            NarrationQuickScriptParser narrationQuickScriptParser
    ) {
        this.validationService = validationService;
        this.objectStorageService = objectStorageService;
        this.videoRepository = videoRepository;
        this.jobRepository = jobRepository;
        this.dispatchOutboxService = dispatchOutboxService;
        this.translationGlossaryParser = translationGlossaryParser;
        this.demoRunProfileService = demoRunProfileService;
        this.ownerIdentityService = ownerIdentityService;
        this.ownerQuotaPreflightService = ownerQuotaPreflightService;
        this.sourceMediaFingerprintService = sourceMediaFingerprintService;
        this.narrationWorkspaceService = narrationWorkspaceService;
        this.narrationVoiceCatalogService = narrationVoiceCatalogService;
        this.narrationQuickScriptParser = narrationQuickScriptParser;
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
        return createUpload(
                file,
                targetLanguage,
                ttsVoice,
                translationStyle,
                subtitleStylePreset,
                translationGlossary,
                subtitlePolishingMode,
                demoProfileId,
                null
        );
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
            String demoProfileId,
            String narrationScript
    ) {
        String normalizedTranslationStyle = TranslationStyle.parse(translationStyle).name();
        String normalizedSubtitleStylePreset = SubtitleStylePreset.parse(subtitleStylePreset).name();
        String normalizedSubtitlePolishingMode = SubtitlePolishingMode.parse(subtitlePolishingMode).name();
        String normalizedDemoProfileId = normalizeDemoProfileId(demoProfileId);
        TranslationGlossaryBo parsedGlossary = translationGlossaryParser.parse(translationGlossary);
        NarrationQuickScriptParser.Result parsedNarrationScript = narrationQuickScriptParser.parse(narrationScript);
        if (!parsedNarrationScript.valid()) {
            throw new IllegalArgumentException(String.join(" ", parsedNarrationScript.errors()));
        }
        validateNarrationVoices(parsedNarrationScript);
        MediaUploadValidationVo validation = validationService.validate(file);
        if (!validation.valid()) {
            throw new IllegalArgumentException(validation.code().name() + ": " + validation.message());
        }
        ownerQuotaPreflightService.requireUploadAllowed();

        String videoId = UUID.randomUUID().toString();
        String jobId = UUID.randomUUID().toString();
        String filename = validation.filename();
        String objectKey = "source-videos/" + videoId + "/" + filename;
        String normalizedTargetLanguage = normalizeTargetLanguage(targetLanguage);
        String normalizedTtsVoice = normalizeTtsVoice(ttsVoice);
        String ownerId = ownerIdentityService.currentOwnerId();
        Instant createdAt = Instant.now();
        String sourceContentSha256 = sourceMediaFingerprintService.sha256(file);

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
                sourceContentSha256,
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
        if (parsedNarrationScript.segmentCount() > 0) {
            narrationWorkspaceService.saveWorkspace(jobId, parsedNarrationScript.toSaveRequest());
        }
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
                parsedNarrationScript.segmentCount() > 0,
                parsedNarrationScript.segmentCount(),
                parsedNarrationScript.characterCount(),
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

    private static OwnerQuotaPreflightService noopQuotaPreflightService() {
        return new OwnerQuotaPreflightService() {
            @Override
            public com.linguaframe.common.quota.OwnerQuotaPreflightVo getPreflight() {
                return new com.linguaframe.common.quota.OwnerQuotaPreflightVo(
                        "demo-owner",
                        false,
                        true,
                        0,
                        0,
                        BigDecimal.ZERO,
                        LocalDate.now(),
                        List.of(),
                        List.of()
                );
            }

            @Override
            public void requireUploadAllowed() {
            }
        };
    }

    private static NarrationWorkspaceService noopNarrationWorkspaceService() {
        return new NarrationWorkspaceService() {
            @Override
            public com.linguaframe.job.domain.vo.NarrationWorkspaceVo getWorkspace(String jobId) {
                throw new UnsupportedOperationException("No-op narration workspace service cannot read workspaces.");
            }

            @Override
            public com.linguaframe.job.domain.vo.NarrationWorkspaceVo saveWorkspace(
                    String jobId,
                    com.linguaframe.job.domain.dto.SaveNarrationSegmentsRequest request
            ) {
                return null;
            }

            @Override
            public com.linguaframe.job.domain.vo.NarrationWorkspaceVo updateMixSettings(
                    String jobId,
                    com.linguaframe.job.domain.dto.UpdateNarrationMixSettingsDto request
            ) {
                throw new UnsupportedOperationException("No-op narration workspace service cannot update mix settings.");
            }

            @Override
            public com.linguaframe.job.domain.vo.NarrationWorkspaceVo clearWorkspace(String jobId) {
                throw new UnsupportedOperationException("No-op narration workspace service cannot clear workspaces.");
            }
        };
    }

    private static NarrationVoiceCatalogService defaultNarrationVoiceCatalogService() {
        return new NarrationVoiceCatalogServiceImpl(new LinguaFrameProperties());
    }

    private void validateNarrationVoices(NarrationQuickScriptParser.Result parsedNarrationScript) {
        for (com.linguaframe.job.domain.dto.SaveNarrationSegmentsRequest.Segment segment : parsedNarrationScript.segments()) {
            if (!narrationVoiceCatalogService.containsVoice(segment.voice())) {
                throw new IllegalArgumentException("Row " + (segment.index() + 1)
                        + ": narration voice must be one of the configured presets.");
            }
        }
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
