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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    void estimatesUploadCostBeforeCreatingUpload() throws Exception {
        properties.getCost().setTranscriptionUsdPerMinute(new java.math.BigDecimal("0.006"));
        properties.getCost().setTranslationInputUsdPerMillionTokens(new java.math.BigDecimal("5"));
        properties.getCost().setTranslationOutputUsdPerMillionTokens(new java.math.BigDecimal("15"));
        properties.getCost().setTtsUsdPerMillionCharacters(new java.math.BigDecimal("15"));
        properties.getCost().setBudgetGuardEnabled(true);
        properties.getCost().setMaxJobCostUsd(new java.math.BigDecimal("1.00"));
        when(mediaDurationProbeService.probeDuration(any())).thenReturn(new MediaDurationProbeResult(90.0));
        MockMultipartFile file = new MockMultipartFile("file", "sample.mp4", "video/mp4", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/media/uploads/cost-estimate")
                        .file(file)
                        .param("targetLanguage", " zh-CN ")
                        .param("translationStyle", "formal")
                        .param("subtitleStylePreset", "high_contrast")
                        .param("subtitlePolishingMode", "balanced")
                        .param("translationGlossary", "Maya => 玛雅")
                        .param("demoProfileId", "tears-showcase"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallStatus").value("READY"))
                .andExpect(jsonPath("$.targetLanguage").value("zh-CN"))
                .andExpect(jsonPath("$.translationStyle").value("FORMAL"))
                .andExpect(jsonPath("$.subtitleStylePreset").value("HIGH_CONTRAST"))
                .andExpect(jsonPath("$.subtitlePolishingMode").value("BALANCED"))
                .andExpect(jsonPath("$.demoProfileId").value("tears-showcase"))
                .andExpect(jsonPath("$.translationGlossaryEntryCount").value(1))
                .andExpect(jsonPath("$.estimatedCostUsd").value(not(isEmptyOrNullString())))
                .andExpect(jsonPath("$.stages[?(@.id == 'transcription')]").isArray())
                .andExpect(jsonPath("$.budgets[?(@.id == 'jobCost')]").isArray());
    }

    @Test
    void returnsBlockedCostEstimateForInvalidFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {1});

        mockMvc.perform(multipart("/api/media/uploads/cost-estimate").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallStatus").value("BLOCKED"))
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.validationCode").value("UNSUPPORTED_CONTENT_TYPE"))
                .andExpect(jsonPath("$.recommendedNextAction").value("Replace the source video or choose media inside the configured upload limits."));
    }

    @Test
    void estimatesUploadExecutionPlanBeforeCreatingUpload() throws Exception {
        properties.getCost().setTranscriptionUsdPerMinute(new java.math.BigDecimal("0.006"));
        properties.getCost().setTranslationInputUsdPerMillionTokens(new java.math.BigDecimal("5"));
        properties.getCost().setTranslationOutputUsdPerMillionTokens(new java.math.BigDecimal("15"));
        when(mediaDurationProbeService.probeDuration(any())).thenReturn(new MediaDurationProbeResult(90.0));
        MockMultipartFile file = new MockMultipartFile("file", "sample.mp4", "video/mp4", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/media/uploads/execution-plan")
                        .file(file)
                        .param("targetLanguage", "zh-CN")
                        .param("translationStyle", "formal")
                        .param("subtitleStylePreset", "high_contrast")
                        .param("subtitlePolishingMode", "balanced")
                        .param("translationGlossary", "Maya => 玛雅")
                        .param("demoProfileId", "tears-showcase"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallStatus").value(not(isEmptyOrNullString())))
                .andExpect(jsonPath("$.filename").value("sample.mp4"))
                .andExpect(jsonPath("$.translationStyle").value("FORMAL"))
                .andExpect(jsonPath("$.subtitleStylePreset").value("HIGH_CONTRAST"))
                .andExpect(jsonPath("$.subtitlePolishingMode").value("BALANCED"))
                .andExpect(jsonPath("$.estimatedDurationSecondsLower").isNumber())
                .andExpect(jsonPath("$.estimatedDurationSecondsUpper").isNumber())
                .andExpect(jsonPath("$.sourceReuse.sourceContentSha256").value("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81"))
                .andExpect(jsonPath("$.sourceReuse.candidateCount").value(0))
                .andExpect(jsonPath("$.sourceReuse.recommendedAction").value("UPLOAD_NEW_SOURCE"))
                .andExpect(jsonPath("$.sourceReuseDecision.status").value("UPLOAD_NEW_SOURCE"))
                .andExpect(jsonPath("$.sourceReuseDecision.headline").value("No previous source match found."))
                .andExpect(jsonPath("$.sourceReuseDecision.actions[?(@.id == 'uploadNewSource')]").isArray())
                .andExpect(jsonPath("$.sourceReuseDecision.links").isEmpty())
                .andExpect(jsonPath("$.stages[?(@.id == 'translation')]").isArray())
                .andExpect(jsonPath("$.gates[?(@.id == 'uploadValidation')]").isArray())
                .andExpect(jsonPath("$.commands[?(@.id == 'upload')]").isArray());
    }

    @Test
    void downloadsUploadExecutionPlanMarkdown() throws Exception {
        properties.getCost().setTranscriptionUsdPerMinute(new java.math.BigDecimal("0.006"));
        properties.getCost().setTranslationInputUsdPerMillionTokens(new java.math.BigDecimal("5"));
        properties.getCost().setTranslationOutputUsdPerMillionTokens(new java.math.BigDecimal("15"));
        when(mediaDurationProbeService.probeDuration(any())).thenReturn(new MediaDurationProbeResult(90.0));
        MockMultipartFile file = new MockMultipartFile("file", "sample.mp4", "video/mp4", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/media/uploads/execution-plan/markdown/download")
                        .file(file)
                        .param("targetLanguage", "zh-CN")
                        .param("translationStyle", "formal")
                        .param("subtitleStylePreset", "high_contrast")
                        .param("subtitlePolishingMode", "balanced")
                        .param("translationGlossary", "Maya => 玛雅")
                        .param("demoProfileId", "tears-showcase"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/markdown"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"upload-execution-plan.md\""))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                        .contains("# Upload Execution Plan")
                        .contains("## Source Metadata")
                        .contains("sample.mp4")
                        .contains("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81")
                        .contains("## Source Reuse Decision")
                        .contains("No previous source match found.")
                        .contains("## Gates")
                        .contains("## Stages")
                        .contains("## Commands")
                        .doesNotContain("source-videos/")
                        .doesNotContain("/Users/")
                        .doesNotContain("sk-"));
    }

    @Test
    void returnsBlockedExecutionPlanForInvalidFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {1});

        mockMvc.perform(multipart("/api/media/uploads/execution-plan").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallStatus").value("BLOCKED"))
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.validationCode").value("UNSUPPORTED_CONTENT_TYPE"))
                .andExpect(jsonPath("$.sourceReuse.sourceContentSha256").doesNotExist())
                .andExpect(jsonPath("$.sourceReuse.candidateCount").value(0))
                .andExpect(jsonPath("$.sourceReuseDecision.status").value("UPLOAD_NEW_SOURCE"))
                .andExpect(jsonPath("$.stages").isEmpty())
                .andExpect(jsonPath("$.gates[?(@.id == 'uploadValidation' && @.status == 'BLOCKED')]").isArray());
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
