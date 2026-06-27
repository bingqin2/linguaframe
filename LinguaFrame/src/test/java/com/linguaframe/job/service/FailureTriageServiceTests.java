package com.linguaframe.job.service;

import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.FailureTriageCategory;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.domain.enums.ModelCallStatus;
import com.linguaframe.job.domain.vo.FailureTriageVo;
import com.linguaframe.job.domain.vo.ModelCallVo;
import com.linguaframe.job.service.impl.FailureTriageServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FailureTriageServiceTests {

    private final FailureTriageService service = new FailureTriageServiceImpl();

    @Test
    void returnsNullForCompletedJobs() {
        FailureTriageVo result = service.triage(job(LocalizationJobStatus.COMPLETED, null, null), List.of(), List.of());

        assertThat(result).isNull();
    }

    @Test
    void mapsCancelledJobsToUserCancelled() {
        FailureTriageVo result = service.triage(job(LocalizationJobStatus.CANCELLED, null, null), List.of(), List.of());

        assertThat(result.category()).isEqualTo(FailureTriageCategory.USER_CANCELLED);
        assertThat(result.retryable()).isFalse();
        assertThat(result.recommendedAction()).containsIgnoringCase("upload");
    }

    @Test
    void mapsBudgetFailuresToBudgetGuard() {
        FailureTriageVo result = service.triage(job(
                LocalizationJobStatus.FAILED,
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                "Daily cost budget exceeded before OpenAI translation"
        ), List.of(), List.of());

        assertThat(result.category()).isEqualTo(FailureTriageCategory.BUDGET_GUARD);
        assertThat(result.retryable()).isFalse();
        assertThat(result.recommendedAction()).contains("budget");
    }

    @Test
    void mapsOpenAiAuthAndModelFailuresFromModelCalls() {
        FailureTriageVo result = service.triage(job(
                LocalizationJobStatus.FAILED,
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                "Provider call failed"
        ), List.of(), List.of(failedOpenAiCall("OpenAI request failed with status 401 invalid_api_key")));

        assertThat(result.category()).isEqualTo(FailureTriageCategory.OPENAI_AUTH_OR_MODEL);
        assertThat(result.retryable()).isFalse();
        assertThat(result.runbookCommand()).isEqualTo("scripts/demo/openai-demo-preflight.sh");
    }

    @Test
    void mapsOpenAiNetworkFailuresFromTimelineEvents() {
        FailureTriageVo result = service.triage(job(
                LocalizationJobStatus.FAILED,
                LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT,
                "Provider call failed"
        ), List.of(event("Connection reset by peer while calling OpenAI")), List.of(failedOpenAiCall(null)));

        assertThat(result.category()).isEqualTo(FailureTriageCategory.OPENAI_TIMEOUT_OR_NETWORK);
        assertThat(result.retryable()).isTrue();
    }

    @Test
    void mapsMediaProcessingFailures() {
        FailureTriageVo result = service.triage(job(
                LocalizationJobStatus.FAILED,
                LocalizationJobStage.AUDIO_EXTRACTION,
                "FFmpeg failed because media duration is too long"
        ), List.of(), List.of());

        assertThat(result.category()).isEqualTo(FailureTriageCategory.MEDIA_PROCESSING);
        assertThat(result.recommendedAction()).contains("video");
    }

    @Test
    void mapsStorageFailures() {
        FailureTriageVo result = service.triage(job(
                LocalizationJobStatus.FAILED,
                LocalizationJobStage.ARTIFACT_SUMMARY,
                "MinIO object storage archive download failed"
        ), List.of(), List.of());

        assertThat(result.category()).isEqualTo(FailureTriageCategory.STORAGE_OR_ARTIFACT);
    }

    @Test
    void mapsWorkerAndQueueFailures() {
        FailureTriageVo result = service.triage(job(
                LocalizationJobStatus.FAILED,
                LocalizationJobStage.WORKER_RECEIVED,
                "Dispatch queue message could not be claimed by worker"
        ), List.of(), List.of());

        assertThat(result.category()).isEqualTo(FailureTriageCategory.WORKER_OR_QUEUE);
    }

    @Test
    void mapsConfigurationFailures() {
        FailureTriageVo result = service.triage(job(
                LocalizationJobStatus.FAILED,
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                "Missing required OPENAI_TRANSLATION_MODEL configuration"
        ), List.of(), List.of());

        assertThat(result.category()).isEqualTo(FailureTriageCategory.CONFIGURATION);
    }

    @Test
    void fallsBackToUnknownForAmbiguousFailures() {
        FailureTriageVo result = service.triage(job(
                LocalizationJobStatus.FAILED,
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                "Unexpected provider result"
        ), List.of(), List.of());

        assertThat(result.category()).isEqualTo(FailureTriageCategory.UNKNOWN);
        assertThat(result.retryable()).isTrue();
    }

    private LocalizationJobRecord job(
            LocalizationJobStatus status,
            LocalizationJobStage failureStage,
            String failureReason
    ) {
        Instant createdAt = Instant.parse("2026-06-28T08:00:00Z");
        return new LocalizationJobRecord(
                "job-triage",
                "video-triage",
                "zh-CN",
                status,
                createdAt,
                createdAt,
                null,
                status == LocalizationJobStatus.FAILED ? createdAt : null,
                failureStage,
                failureReason,
                0,
                createdAt
        );
    }

    private JobTimelineEventRecord event(String errorSummary) {
        return new JobTimelineEventRecord(
                "timeline-triage",
                "job-triage",
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                JobTimelineEventStatus.FAILED,
                "Stage failed",
                1200L,
                errorSummary,
                Instant.parse("2026-06-28T08:01:00Z")
        );
    }

    private ModelCallVo failedOpenAiCall(String safeErrorSummary) {
        return new ModelCallVo(
                "model-call-triage",
                "job-triage",
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSLATION,
                ModelCallProvider.OPENAI,
                "gpt-4o-mini",
                "translation-v1",
                ModelCallStatus.FAILED,
                900L,
                100,
                null,
                null,
                300,
                "target=zh-CN, segments=2",
                null,
                "demo-owner",
                BigDecimal.ZERO,
                safeErrorSummary,
                Instant.parse("2026-06-28T08:01:00Z")
        );
    }
}
