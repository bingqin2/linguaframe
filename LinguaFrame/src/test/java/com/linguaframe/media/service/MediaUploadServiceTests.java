package com.linguaframe.media.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.common.quota.OwnerQuotaExceededException;
import com.linguaframe.common.quota.OwnerQuotaLimitVo;
import com.linguaframe.common.quota.OwnerQuotaPreflightService;
import com.linguaframe.common.quota.OwnerQuotaPreflightVo;
import com.linguaframe.common.security.DemoOwnerIdentityService;
import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.JobDispatchEventType;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.dto.SaveNarrationSegmentsRequest;
import com.linguaframe.job.domain.dto.UpdateNarrationMixSettingsDto;
import com.linguaframe.job.domain.vo.NarrationWorkspaceVo;
import com.linguaframe.job.repository.JobDispatchEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.impl.JobDispatchOutboxServiceImpl;
import com.linguaframe.job.service.NarrationWorkspaceService;
import com.linguaframe.media.domain.bo.MediaDurationProbeCommand;
import com.linguaframe.media.domain.bo.MediaDurationProbeResult;
import com.linguaframe.media.domain.exception.UnreadableMediaException;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.domain.vo.MediaUploadVo;
import com.linguaframe.media.repository.VideoRepository;
import com.linguaframe.media.service.impl.MediaUploadServiceImpl;
import com.linguaframe.media.service.impl.MediaUploadValidationServiceImpl;
import com.linguaframe.storage.domain.bo.StoreObjectCommand;
import com.linguaframe.storage.domain.bo.StoredObjectBo;
import com.linguaframe.storage.service.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class MediaUploadServiceTests {

    @Autowired
    private LinguaFrameProperties properties;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private LocalizationJobRepository jobRepository;

    @Autowired
    private JobDispatchEventRepository dispatchEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM model_call_records").update();
        jdbcClient.sql("DELETE FROM subtitle_segments").update();
        jdbcClient.sql("DELETE FROM transcript_segments").update();
        jdbcClient.sql("DELETE FROM job_artifacts").update();
        jdbcClient.sql("DELETE FROM job_timeline_events").update();
        jdbcClient.sql("DELETE FROM job_dispatch_events").update();
        jdbcClient.sql("DELETE FROM localization_jobs").update();
        jdbcClient.sql("DELETE FROM videos").update();
    }

    @Test
    void createsDurableVideoAndQueuedJob() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper)
        );
        MockMultipartFile file = new MockMultipartFile("file", "C:\\tmp\\demo.mp4", "video/mp4", new byte[] {1, 2, 3});

        MediaUploadVo result = service.createUpload(file, "zh-CN");

        assertThat(result.videoId()).isNotBlank();
        assertThat(result.jobId()).isNotBlank();
        assertThat(result.filename()).isEqualTo("demo.mp4");
        assertThat(result.contentType()).isEqualTo("video/mp4");
        assertThat(result.fileSizeBytes()).isEqualTo(3);
        assertThat(result.durationSeconds()).isEqualTo(42);
        assertThat(result.status()).isEqualTo(MediaUploadStatus.UPLOADED);
        assertThat(result.jobStatus()).isEqualTo(LocalizationJobStatus.QUEUED);
        assertThat(result.sourceObjectKey()).isEqualTo("source-videos/" + result.videoId() + "/demo.mp4");
        assertThat(storageService.lastCommand.objectKey()).isEqualTo(result.sourceObjectKey());
        assertThat(videoRepository.findById(result.videoId()))
                .isPresent()
                .get()
                .satisfies(video -> {
                    assertThat(video.durationSeconds()).isEqualTo(42);
                    assertThat(video.ownerId()).isEqualTo("demo-owner");
                    assertThat(video.sourceContentSha256())
                            .isEqualTo("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81");
                });
        assertThat(jobRepository.findById(result.jobId()))
                .isPresent()
                .get()
                .satisfies(job -> assertThat(job.ownerId()).isEqualTo("demo-owner"));
        assertThat(dispatchEventRepository.findLatestByJobId(result.jobId()))
                .isPresent()
                .get()
                .satisfies(event -> {
                    assertThat(event.status()).isEqualTo(JobDispatchEventStatus.PENDING);
                    assertThat(event.eventType()).isEqualTo(JobDispatchEventType.LOCALIZATION_JOB_QUEUED);
                    assertThat(event.payloadJson())
                            .contains(result.jobId())
                            .contains(result.videoId())
                            .contains(result.sourceObjectKey());
                });
    }

    @Test
    void createsVideoAndJobForConfiguredDemoOwner() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper),
                new FixedDemoOwnerIdentityService("owner-alpha")
        );
        MockMultipartFile file = new MockMultipartFile("file", "owner.mp4", "video/mp4", new byte[] {1, 2, 3});

        MediaUploadVo result = service.createUpload(file, "zh-CN");

        assertThat(videoRepository.findByIdAndOwnerId(result.videoId(), "owner-alpha")).isPresent();
        assertThat(jobRepository.findByIdAndOwnerId(result.jobId(), "owner-alpha")).isPresent();
        assertThat(videoRepository.findByIdAndOwnerId(result.videoId(), "owner-beta")).isEmpty();
        assertThat(jobRepository.findByIdAndOwnerId(result.jobId(), "owner-beta")).isEmpty();
    }

    @Test
    void rejectsOverQuotaOwnerBeforeStorageDatabaseAndDispatch() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper),
                new BlockingOwnerQuotaPreflightService()
        );
        MockMultipartFile file = new MockMultipartFile("file", "quota.mp4", "video/mp4", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.createUpload(file, "zh-CN"))
                .isInstanceOf(OwnerQuotaExceededException.class)
                .hasMessageContaining("Active job limit reached");
        assertThat(storageService.lastCommand).isNull();
        assertThat(jobRepository.countSummariesByOwnerId("demo-owner", null)).isZero();
        assertThat(dispatchEventRepository.findLatestByJobId("missing")).isEmpty();
    }

    @Test
    void createsJobWithTrimmedTtsVoice() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper)
        );
        MockMultipartFile file = new MockMultipartFile("file", "voice.mp4", "video/mp4", new byte[] {1, 2, 3});

        MediaUploadVo result = service.createUpload(file, "zh-CN", " verse ");

        assertThat(result.ttsVoice()).isEqualTo("verse");
        assertThat(jobRepository.findById(result.jobId()))
                .get()
                .satisfies(job -> assertThat(job.ttsVoice()).isEqualTo("verse"));
        assertThat(dispatchEventRepository.findLatestByJobId(result.jobId()))
                .get()
                .satisfies(event -> assertThat(event.payloadJson()).contains("\"ttsVoice\":\"verse\""));
    }

    @Test
    void createsJobWithExplicitTranslationStyle() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper)
        );
        MockMultipartFile file = new MockMultipartFile("file", "formal.mp4", "video/mp4", new byte[] {1, 2, 3});

        MediaUploadVo result = service.createUpload(file, "zh-CN", "verse", " formal ");

        assertThat(result.translationStyle()).isEqualTo("FORMAL");
        assertThat(jobRepository.findById(result.jobId()))
                .get()
                .satisfies(job -> assertThat(job.translationStyle()).isEqualTo("FORMAL"));
        assertThat(dispatchEventRepository.findLatestByJobId(result.jobId()))
                .get()
                .satisfies(event -> assertThat(event.payloadJson()).contains("\"translationStyle\":\"FORMAL\""));
    }

    @Test
    void createsJobWithExplicitSubtitleStylePreset() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper)
        );
        MockMultipartFile file = new MockMultipartFile("file", "contrast.mp4", "video/mp4", new byte[] {1, 2, 3});

        MediaUploadVo result = service.createUpload(file, "zh-CN", "verse", "formal", " high_contrast ");

        assertThat(result.subtitleStylePreset()).isEqualTo("HIGH_CONTRAST");
        assertThat(jobRepository.findById(result.jobId()))
                .get()
                .satisfies(job -> assertThat(job.subtitleStylePreset()).isEqualTo("HIGH_CONTRAST"));
        assertThat(dispatchEventRepository.findLatestByJobId(result.jobId()))
                .get()
                .satisfies(event -> assertThat(event.payloadJson()).contains("\"subtitleStylePreset\":\"HIGH_CONTRAST\""));
    }

    @Test
    void createsJobWithExplicitTranslationGlossary() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper)
        );
        MockMultipartFile file = new MockMultipartFile("file", "glossary.mp4", "video/mp4", new byte[] {1, 2, 3});

        MediaUploadVo result = service.createUpload(
                file,
                "zh-CN",
                "verse",
                "formal",
                "high_contrast",
                """
                        Maya => 玛雅
                        Tears of Steel = 钢铁之泪
                        """
        );

        assertThat(result.translationGlossaryEntryCount()).isEqualTo(2);
        assertThat(result.translationGlossaryHash()).matches("[a-f0-9]{64}");
        assertThat(jobRepository.findById(result.jobId()))
                .get()
                .satisfies(job -> {
                    assertThat(job.translationGlossaryEntryCount()).isEqualTo(2);
                    assertThat(job.translationGlossaryHash()).isEqualTo(result.translationGlossaryHash());
                    assertThat(job.translationGlossaryJson()).contains("\"sourceTerm\":\"Maya\"");
                });
        assertThat(dispatchEventRepository.findLatestByJobId(result.jobId()))
                .get()
                .satisfies(event -> assertThat(event.payloadJson())
                        .contains("\"translationGlossaryEntryCount\":2")
                        .contains("\"translationGlossaryHash\":\"" + result.translationGlossaryHash() + "\"")
                        .contains("\"translationGlossaryJson\":\"[{"));
    }

    @Test
    void createsJobWithExplicitSubtitlePolishingMode() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper)
        );
        MockMultipartFile file = new MockMultipartFile("file", "polished.mp4", "video/mp4", new byte[] {1, 2, 3});

        MediaUploadVo result = service.createUpload(
                file,
                "zh-CN",
                "verse",
                "formal",
                "high_contrast",
                "Maya => 玛雅",
                " balanced "
        );

        assertThat(result.subtitlePolishingMode()).isEqualTo("BALANCED");
        assertThat(jobRepository.findById(result.jobId()))
                .get()
                .satisfies(job -> assertThat(job.subtitlePolishingMode()).isEqualTo("BALANCED"));
        assertThat(dispatchEventRepository.findLatestByJobId(result.jobId()))
                .get()
                .satisfies(event -> assertThat(event.payloadJson()).contains("\"subtitlePolishingMode\":\"BALANCED\""));
    }

    @Test
    void createsJobWithExplicitDemoProfileId() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper)
        );
        MockMultipartFile file = new MockMultipartFile("file", "profile.mp4", "video/mp4", new byte[] {1, 2, 3});

        MediaUploadVo result = service.createUpload(
                file,
                "zh-CN",
                "verse",
                "formal",
                "high_contrast",
                "Maya => 玛雅",
                "balanced",
                " tears-showcase "
        );

        assertThat(result.demoProfileId()).isEqualTo("tears-showcase");
        assertThat(jobRepository.findById(result.jobId()))
                .get()
                .satisfies(job -> assertThat(job.demoProfileId()).isEqualTo("tears-showcase"));
        assertThat(dispatchEventRepository.findLatestByJobId(result.jobId()))
                .get()
                .satisfies(event -> assertThat(event.payloadJson()).contains("\"demoProfileId\":\"tears-showcase\""));
    }

    @Test
    void seedsNarrationWorkspaceFromUploadQuickScript() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        RecordingNarrationWorkspaceService narrationWorkspaceService = new RecordingNarrationWorkspaceService();
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper),
                narrationWorkspaceService
        );
        MockMultipartFile file = new MockMultipartFile("file", "narrated.mp4", "video/mp4", new byte[] {1, 2, 3});

        MediaUploadVo result = service.createUpload(
                file,
                "zh-CN",
                "verse",
                "formal",
                "high_contrast",
                "Maya => 玛雅",
                "balanced",
                "tears-showcase",
                """
                        00:15-00:28 | demo-voice | Explain the opening gesture.
                        00:55-01:10 || Inherit the default voice.
                        """
        );

        assertThat(result.narrationScriptSeeded()).isTrue();
        assertThat(result.narrationScriptSegmentCount()).isEqualTo(2);
        assertThat(result.narrationScriptCharacterCount()).isEqualTo(54);
        assertThat(narrationWorkspaceService.jobId).isEqualTo(result.jobId());
        assertThat(narrationWorkspaceService.request.segments()).hasSize(2);
        assertThat(narrationWorkspaceService.request.segments().get(0).voice()).isEqualTo("demo-voice");
        assertThat(narrationWorkspaceService.request.segments().get(1).voice()).isNull();
        assertThat(dispatchEventRepository.findLatestByJobId(result.jobId())).isPresent();
    }

    @Test
    void rejectsInvalidNarrationScriptBeforeStorageAndDispatch() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        RecordingNarrationWorkspaceService narrationWorkspaceService = new RecordingNarrationWorkspaceService();
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper),
                narrationWorkspaceService
        );
        MockMultipartFile file = new MockMultipartFile("file", "bad-narration.mp4", "video/mp4", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.createUpload(
                file,
                "zh-CN",
                null,
                null,
                null,
                null,
                null,
                null,
                "00:20-00:10 | alloy | Backwards."
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Row 1: end time must be greater than start time.");
        assertThat(storageService.lastCommand).isNull();
        assertThat(narrationWorkspaceService.request).isNull();
        assertThat(jobRepository.countSummariesByOwnerId("demo-owner", null)).isZero();
    }

    @Test
    void rejectsUnknownNarrationVoiceBeforeStorageAndDispatch() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        RecordingNarrationWorkspaceService narrationWorkspaceService = new RecordingNarrationWorkspaceService();
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper),
                narrationWorkspaceService
        );
        MockMultipartFile file = new MockMultipartFile("file", "bad-voice.mp4", "video/mp4", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.createUpload(
                file,
                "zh-CN",
                null,
                null,
                null,
                null,
                null,
                null,
                "00:15-00:28 | alloy | OpenAI voice is not available in demo mode."
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Row 1: narration voice must be one of the configured presets.");
        assertThat(storageService.lastCommand).isNull();
        assertThat(narrationWorkspaceService.request).isNull();
        assertThat(jobRepository.countSummariesByOwnerId("demo-owner", null)).isZero();
    }

    @Test
    void rejectsUnknownDemoProfileIdBeforeStorage() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper)
        );
        MockMultipartFile file = new MockMultipartFile("file", "profile.mp4", "video/mp4", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.createUpload(file, "zh-CN", null, null, null, null, null, "unknown-profile"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown demo profile");
        assertThat(storageService.lastCommand).isNull();
    }

    @Test
    void defaultsBlankSubtitlePolishingModeToOff() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper)
        );
        MockMultipartFile file = new MockMultipartFile("file", "unpolished.mp4", "video/mp4", new byte[] {1, 2, 3});

        MediaUploadVo result = service.createUpload(file, "zh-CN", null, null, null, null, "   ");

        assertThat(result.subtitlePolishingMode()).isEqualTo("OFF");
        assertThat(jobRepository.findById(result.jobId()))
                .get()
                .satisfies(job -> assertThat(job.subtitlePolishingMode()).isEqualTo("OFF"));
    }

    @Test
    void defaultsBlankTranslationGlossaryToEmpty() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper)
        );
        MockMultipartFile file = new MockMultipartFile("file", "no-glossary.mp4", "video/mp4", new byte[] {1, 2, 3});

        MediaUploadVo result = service.createUpload(file, "zh-CN", null, null, null, "   ");

        assertThat(result.translationGlossaryEntryCount()).isZero();
        assertThat(result.translationGlossaryHash()).isEmpty();
        assertThat(jobRepository.findById(result.jobId()))
                .get()
                .satisfies(job -> {
                    assertThat(job.translationGlossaryEntryCount()).isZero();
                    assertThat(job.translationGlossaryHash()).isEmpty();
                    assertThat(job.translationGlossaryJson()).isEqualTo("[]");
                });
    }

    @Test
    void rejectsInvalidTranslationGlossaryBeforeStorage() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper)
        );
        MockMultipartFile file = new MockMultipartFile("file", "invalid-glossary.mp4", "video/mp4", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.createUpload(file, "zh-CN", null, null, null, "Maya 玛雅"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Translation glossary");
        assertThat(storageService.lastCommand).isNull();
    }

    @Test
    void defaultsBlankSubtitleStylePresetToStandard() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper)
        );
        MockMultipartFile file = new MockMultipartFile("file", "standard.mp4", "video/mp4", new byte[] {1, 2, 3});

        MediaUploadVo result = service.createUpload(file, "zh-CN", null, null, "   ");

        assertThat(result.subtitleStylePreset()).isEqualTo("STANDARD");
        assertThat(jobRepository.findById(result.jobId()))
                .get()
                .satisfies(job -> assertThat(job.subtitleStylePreset()).isEqualTo("STANDARD"));
    }

    @Test
    void rejectsInvalidSubtitleStylePresetBeforeStorage() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper)
        );
        MockMultipartFile file = new MockMultipartFile("file", "invalid-subtitle-style.mp4", "video/mp4", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.createUpload(file, "zh-CN", null, null, "tiny"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported subtitle style preset");
        assertThat(storageService.lastCommand).isNull();
    }

    @Test
    void defaultsBlankTranslationStyleToNatural() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper)
        );
        MockMultipartFile file = new MockMultipartFile("file", "natural.mp4", "video/mp4", new byte[] {1, 2, 3});

        MediaUploadVo result = service.createUpload(file, "zh-CN", null, "   ");

        assertThat(result.translationStyle()).isEqualTo("NATURAL");
        assertThat(jobRepository.findById(result.jobId()))
                .get()
                .satisfies(job -> assertThat(job.translationStyle()).isEqualTo("NATURAL"));
    }

    @Test
    void rejectsInvalidTranslationStyleBeforeStorage() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper)
        );
        MockMultipartFile file = new MockMultipartFile("file", "invalid-style.mp4", "video/mp4", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.createUpload(file, "zh-CN", null, "dramatic"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported translation style");
        assertThat(storageService.lastCommand).isNull();
    }

    @Test
    void normalizesBlankTtsVoiceToNull() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper)
        );
        MockMultipartFile file = new MockMultipartFile("file", "voice-default.mp4", "video/mp4", new byte[] {1, 2, 3});

        MediaUploadVo result = service.createUpload(file, "zh-CN", "   ");

        assertThat(result.ttsVoice()).isNull();
        assertThat(jobRepository.findById(result.jobId()))
                .get()
                .satisfies(job -> assertThat(job.ttsVoice()).isNull());
    }

    @Test
    void rejectsInvalidUploadBeforeStorage() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper)
        );
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {1});

        assertThatThrownBy(() -> service.createUpload(file, "zh-CN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNSUPPORTED_CONTENT_TYPE");
        assertThat(storageService.lastCommand).isNull();
    }

    @Test
    void rejectsTooLongVideoBeforeStorage() {
        properties.getMedia().setMaxDurationSeconds(300);
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(300.001)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper)
        );
        MockMultipartFile file = new MockMultipartFile("file", "long.mp4", "video/mp4", new byte[] {1});

        assertThatThrownBy(() -> service.createUpload(file, "zh-CN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DURATION_TOO_LONG");
        assertThat(storageService.lastCommand).isNull();
    }

    @Test
    void rejectsUnreadableVideoBeforeStorage() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(
                        properties,
                        command -> {
                            throw new UnreadableMediaException("The uploaded video could not be inspected.");
                        }
                ),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper)
        );
        MockMultipartFile file = new MockMultipartFile("file", "broken.mp4", "video/mp4", new byte[] {1});

        assertThatThrownBy(() -> service.createUpload(file, "zh-CN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNREADABLE_MEDIA");
        assertThat(storageService.lastCommand).isNull();
    }

    @Test
    void returnsSafeStorageFailure() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(true);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper)
        );
        MockMultipartFile file = new MockMultipartFile("file", "demo.mp4", "video/mp4", new byte[] {1});

        assertThatThrownBy(() -> service.createUpload(file, "zh-CN"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to store source video.");
    }

    private static class RecordingObjectStorageService implements ObjectStorageService {

        private final boolean fail;
        private StoreObjectCommand lastCommand;

        private RecordingObjectStorageService(boolean fail) {
            this.fail = fail;
        }

        @Override
        public StoredObjectBo store(StoreObjectCommand command) {
            this.lastCommand = command;
            if (fail) {
                throw new IllegalStateException("raw storage password stack trace");
            }
            return new StoredObjectBo("linguaframe-artifacts", command.objectKey(), command.sizeBytes());
        }

        @Override
        public InputStream open(String objectKey) {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void delete(String objectKey) {
        }
    }

    private static class RecordingMediaDurationProbeService implements MediaDurationProbeService {

        private final double durationSeconds;

        private RecordingMediaDurationProbeService(double durationSeconds) {
            this.durationSeconds = durationSeconds;
        }

        @Override
        public MediaDurationProbeResult probeDuration(MediaDurationProbeCommand command) {
            return new MediaDurationProbeResult(durationSeconds);
        }
    }

    private record FixedDemoOwnerIdentityService(String ownerId) implements DemoOwnerIdentityService {

        @Override
        public String currentOwnerId() {
            return ownerId;
        }
    }

    private static class BlockingOwnerQuotaPreflightService implements OwnerQuotaPreflightService {

        @Override
        public OwnerQuotaPreflightVo getPreflight() {
            return preflight();
        }

        @Override
        public void requireUploadAllowed() {
            throw new OwnerQuotaExceededException(preflight());
        }

        private OwnerQuotaPreflightVo preflight() {
            return new OwnerQuotaPreflightVo(
                    "demo-owner",
                    true,
                    false,
                    1,
                    1,
                    BigDecimal.ZERO,
                    LocalDate.parse("2026-06-28"),
                    List.of(new OwnerQuotaLimitVo("activeJobs", true, BigDecimal.ONE, BigDecimal.ONE)),
                    List.of("Active job limit reached for owner demo-owner: current 1, limit 1.")
            );
        }
    }

    private static class RecordingNarrationWorkspaceService implements NarrationWorkspaceService {

        private String jobId;
        private SaveNarrationSegmentsRequest request;

        @Override
        public NarrationWorkspaceVo getWorkspace(String jobId) {
            throw new UnsupportedOperationException("Not used by this test.");
        }

        @Override
        public NarrationWorkspaceVo saveWorkspace(String jobId, SaveNarrationSegmentsRequest request) {
            this.jobId = jobId;
            this.request = request;
            return null;
        }

        @Override
        public NarrationWorkspaceVo updateMixSettings(String jobId, UpdateNarrationMixSettingsDto request) {
            throw new UnsupportedOperationException("Not used by this test.");
        }

        @Override
        public NarrationWorkspaceVo clearWorkspace(String jobId) {
            throw new UnsupportedOperationException("Not used by this test.");
        }
    }
}
