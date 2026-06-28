package com.linguaframe.media.controller;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.media.domain.bo.MediaDurationProbeResult;
import com.linguaframe.media.domain.exception.UnreadableMediaException;
import com.linguaframe.media.service.MediaDurationProbeService;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.storage.domain.bo.StoreObjectCommand;
import com.linguaframe.storage.domain.bo.StoredObjectBo;
import com.linguaframe.storage.service.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.io.ByteArrayInputStream;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaUploadControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ObjectStorageService objectStorageService;

    @MockitoBean
    private MediaDurationProbeService mediaDurationProbeService;

    @Autowired
    private LocalizationJobRepository jobRepository;

    @Autowired
    private LinguaFrameProperties properties;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabaseAndResetQuota() {
        jdbcClient.sql("DELETE FROM model_call_records").update();
        jdbcClient.sql("DELETE FROM job_artifacts").update();
        jdbcClient.sql("DELETE FROM job_timeline_events").update();
        jdbcClient.sql("DELETE FROM job_dispatch_events").update();
        jdbcClient.sql("DELETE FROM localization_jobs").update();
        jdbcClient.sql("DELETE FROM videos").update();
        properties.getOwnerQuota().setEnabled(false);
        properties.getOwnerQuota().setMaxActiveJobs(0);
        properties.getOwnerQuota().setMaxQueuedJobs(0);
        properties.getOwnerQuota().setDailyBudgetGuardEnabled(false);
        properties.getOwnerQuota().setMaxDailyCostUsd(java.math.BigDecimal.ZERO);
    }

    @Test
    void returnsOwnerQuotaPreflight() throws Exception {
        mockMvc.perform(get("/api/media/uploads/preflight"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value("demo-owner"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.blockingReasons").isArray());
    }

    @Test
    void returnsDemoUploadReadiness() throws Exception {
        mockMvc.perform(get("/api/media/uploads/readiness").param("demoProfileId", "quick-baseline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallStatus").value("READY"))
                .andExpect(jsonPath("$.ownerId").value("demo-owner"))
                .andExpect(jsonPath("$.demoProfileId").value("quick-baseline"))
                .andExpect(jsonPath("$.checks").isArray())
                .andExpect(jsonPath("$.requiredActions").isArray())
                .andExpect(jsonPath("$.evidenceRoutes").isArray());
    }

    @Test
    void validatesSupportedMultipartVideo() throws Exception {
        when(mediaDurationProbeService.probeDuration(any())).thenReturn(new MediaDurationProbeResult(42.0));
        MockMultipartFile file = new MockMultipartFile("file", "sample.mp4", "video/mp4", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/media/uploads/validate").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.code").value("READY"))
                .andExpect(jsonPath("$.filename").value("sample.mp4"))
                .andExpect(jsonPath("$.contentType").value("video/mp4"))
                .andExpect(jsonPath("$.fileSizeBytes").value(3))
                .andExpect(jsonPath("$.maxFileSizeBytes").value(104857600))
                .andExpect(jsonPath("$.durationSeconds").value(42))
                .andExpect(jsonPath("$.maxDurationSeconds").value(300));
    }

    @Test
    void returnsBadRequestForInvalidValidationFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {1});

        mockMvc.perform(multipart("/api/media/uploads/validate").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CONTENT_TYPE"));
    }

    @Test
    void returnsBadRequestForTooLongValidationFile() throws Exception {
        when(mediaDurationProbeService.probeDuration(any())).thenReturn(new MediaDurationProbeResult(300.001));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "long.mp4",
                "video/mp4",
                new byte[] {1, 2, 3, 4}
        );

        mockMvc.perform(multipart("/api/media/uploads/validate").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.code").value("DURATION_TOO_LONG"));
    }

    @Test
    void returnsBadRequestForUnreadableValidationFile() throws Exception {
        when(mediaDurationProbeService.probeDuration(any()))
                .thenThrow(new UnreadableMediaException("The uploaded video could not be inspected."));
        MockMultipartFile file = new MockMultipartFile("file", "broken.mp4", "video/mp4", new byte[] {1});

        mockMvc.perform(multipart("/api/media/uploads/validate").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.code").value("UNREADABLE_MEDIA"))
                .andExpect(jsonPath("$.durationSeconds").doesNotExist());
    }

    @Test
    void createsUploadAndQueuedJob() throws Exception {
        when(mediaDurationProbeService.probeDuration(any())).thenReturn(new MediaDurationProbeResult(42.0));
        when(objectStorageService.store(any(StoreObjectCommand.class))).thenAnswer(invocation -> {
            StoreObjectCommand command = invocation.getArgument(0);
            return new StoredObjectBo("linguaframe-artifacts", command.objectKey(), command.sizeBytes());
        });
        MockMultipartFile file = new MockMultipartFile("file", "sample.mp4", "video/mp4", new byte[] {1, 2, 3});

        String response = mockMvc.perform(multipart("/api/media/uploads")
                        .file(file)
                        .param("targetLanguage", "zh-CN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.videoId", not(isEmptyOrNullString())))
                .andExpect(jsonPath("$.jobId", not(isEmptyOrNullString())))
                .andExpect(jsonPath("$.filename").value("sample.mp4"))
                .andExpect(jsonPath("$.durationSeconds").value(42))
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.jobStatus").value("QUEUED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String videoId = response.replaceAll(".*\"videoId\":\"([^\"]+)\".*", "$1");
        mockMvc.perform(get("/api/media/uploads/{videoId}", videoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoId").value(videoId))
                .andExpect(jsonPath("$.durationSeconds").value(42))
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.sourceObjectKey").doesNotExist());
    }

    @Test
    void rejectsUploadWhenOwnerQuotaIsExceeded() throws Exception {
        properties.getOwnerQuota().setEnabled(true);
        properties.getOwnerQuota().setMaxActiveJobs(1);
        when(mediaDurationProbeService.probeDuration(any())).thenReturn(new MediaDurationProbeResult(42.0));
        MockMultipartFile first = new MockMultipartFile("file", "first.mp4", "video/mp4", new byte[] {1, 2, 3});
        MockMultipartFile second = new MockMultipartFile("file", "second.mp4", "video/mp4", new byte[] {4, 5, 6});
        when(objectStorageService.store(any(StoreObjectCommand.class))).thenAnswer(invocation -> {
            StoreObjectCommand command = invocation.getArgument(0);
            return new StoredObjectBo("linguaframe-artifacts", command.objectKey(), command.sizeBytes());
        });

        mockMvc.perform(multipart("/api/media/uploads").file(first))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/media/uploads").file(second))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWNER_QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("Active job limit reached for owner demo-owner: current 1, limit 1."))
                .andExpect(jsonPath("$.message").value(not(isEmptyOrNullString())));

        org.assertj.core.api.Assertions.assertThat(jobRepository.countSummariesByOwnerId("demo-owner", LocalizationJobStatus.QUEUED))
                .isEqualTo(1);
    }

    @Test
    void downloadsUploadedSourceVideo() throws Exception {
        when(mediaDurationProbeService.probeDuration(any())).thenReturn(new MediaDurationProbeResult(42.0));
        when(objectStorageService.store(any(StoreObjectCommand.class))).thenAnswer(invocation -> {
            StoreObjectCommand command = invocation.getArgument(0);
            return new StoredObjectBo("linguaframe-artifacts", command.objectKey(), command.sizeBytes());
        });
        byte[] sourceBytes = new byte[] {9, 8, 7, 6};
        MockMultipartFile file = new MockMultipartFile("file", "sample.mp4", "video/mp4", sourceBytes);

        String response = mockMvc.perform(multipart("/api/media/uploads")
                        .file(file)
                        .param("targetLanguage", "zh-CN"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String videoId = response.replaceAll(".*\"videoId\":\"([^\"]+)\".*", "$1");
        when(objectStorageService.open("source-videos/" + videoId + "/sample.mp4"))
                .thenReturn(new ByteArrayInputStream(sourceBytes));

        mockMvc.perform(get("/api/media/uploads/{videoId}/source/download", videoId))
                .andExpect(status().isOk())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                                result.getResponse().getHeader("Content-Disposition"))
                        .contains("attachment")
                        .contains("sample.mp4"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                                result.getResponse().getContentType())
                        .isEqualTo("video/mp4"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                                result.getResponse().getContentLength())
                        .isEqualTo(sourceBytes.length))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                                result.getResponse().getContentAsByteArray())
                        .isEqualTo(sourceBytes));
    }

    @Test
    void createsUploadWithTtsVoice() throws Exception {
        when(mediaDurationProbeService.probeDuration(any())).thenReturn(new MediaDurationProbeResult(42.0));
        when(objectStorageService.store(any(StoreObjectCommand.class))).thenAnswer(invocation -> {
            StoreObjectCommand command = invocation.getArgument(0);
            return new StoredObjectBo("linguaframe-artifacts", command.objectKey(), command.sizeBytes());
        });
        MockMultipartFile file = new MockMultipartFile("file", "voice.mp4", "video/mp4", new byte[] {1, 2, 3});

        String response = mockMvc.perform(multipart("/api/media/uploads")
                        .file(file)
                        .param("targetLanguage", "zh-CN")
                        .param("ttsVoice", " verse "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ttsVoice").value("verse"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String jobId = response.replaceAll(".*\"jobId\":\"([^\"]+)\".*", "$1");
        mockMvc.perform(get("/api/jobs/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ttsVoice").value("verse"));
    }

    @Test
    void createsUploadWithTranslationStyle() throws Exception {
        when(mediaDurationProbeService.probeDuration(any())).thenReturn(new MediaDurationProbeResult(42.0));
        when(objectStorageService.store(any(StoreObjectCommand.class))).thenAnswer(invocation -> {
            StoreObjectCommand command = invocation.getArgument(0);
            return new StoredObjectBo("linguaframe-artifacts", command.objectKey(), command.sizeBytes());
        });
        MockMultipartFile file = new MockMultipartFile("file", "style.mp4", "video/mp4", new byte[] {1, 2, 3});

        String response = mockMvc.perform(multipart("/api/media/uploads")
                        .file(file)
                        .param("targetLanguage", "zh-CN")
                        .param("translationStyle", " concise "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.translationStyle").value("CONCISE"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String jobId = response.replaceAll(".*\"jobId\":\"([^\"]+)\".*", "$1");
        mockMvc.perform(get("/api/jobs/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.translationStyle").value("CONCISE"));
    }

    @Test
    void createsUploadWithSubtitleStylePreset() throws Exception {
        when(mediaDurationProbeService.probeDuration(any())).thenReturn(new MediaDurationProbeResult(42.0));
        when(objectStorageService.store(any(StoreObjectCommand.class))).thenAnswer(invocation -> {
            StoreObjectCommand command = invocation.getArgument(0);
            return new StoredObjectBo("linguaframe-artifacts", command.objectKey(), command.sizeBytes());
        });
        MockMultipartFile file = new MockMultipartFile("file", "subtitle-style.mp4", "video/mp4", new byte[] {1, 2, 3});

        String response = mockMvc.perform(multipart("/api/media/uploads")
                        .file(file)
                        .param("targetLanguage", "zh-CN")
                        .param("subtitleStylePreset", " high_contrast "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtitleStylePreset").value("HIGH_CONTRAST"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String jobId = response.replaceAll(".*\"jobId\":\"([^\"]+)\".*", "$1");
        mockMvc.perform(get("/api/jobs/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtitleStylePreset").value("HIGH_CONTRAST"));
    }

    @Test
    void returnsBadRequestForInvalidTranslationStyle() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "style.mp4", "video/mp4", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/media/uploads")
                        .file(file)
                        .param("targetLanguage", "zh-CN")
                        .param("translationStyle", "dramatic"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UPLOAD_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Unsupported translation style: dramatic"));
    }

    @Test
    void returnsBadRequestForInvalidSubtitleStylePreset() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "style.mp4", "video/mp4", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/media/uploads")
                        .file(file)
                        .param("targetLanguage", "zh-CN")
                        .param("subtitleStylePreset", "tiny"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UPLOAD_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Unsupported subtitle style preset: tiny"));
    }

    @Test
    void returnsBadRequestForInvalidUploadFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {1});

        mockMvc.perform(multipart("/api/media/uploads")
                        .file(file)
                        .param("targetLanguage", "zh-CN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UPLOAD_VALIDATION_FAILED"));
    }

    @Test
    void returnsBadRequestForUnreadableUploadFile() throws Exception {
        when(mediaDurationProbeService.probeDuration(any()))
                .thenThrow(new UnreadableMediaException("The uploaded video could not be inspected."));
        MockMultipartFile file = new MockMultipartFile("file", "broken.mp4", "video/mp4", new byte[] {1});

        mockMvc.perform(multipart("/api/media/uploads")
                        .file(file)
                        .param("targetLanguage", "zh-CN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UPLOAD_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(
                        "UNREADABLE_MEDIA: The uploaded video could not be inspected."
                ));
    }

    @Test
    void returnsNotFoundForUnknownVideo() throws Exception {
        mockMvc.perform(get("/api/media/uploads/{videoId}", "missing-video"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
