package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.domain.enums.ModelCallStatus;
import com.linguaframe.job.domain.enums.QualityEvaluationStatus;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.ModelCallVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.service.impl.OpenAiSmokeProofServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiSmokeProofServiceTests {

    @Test
    void buildsReadyProofForCompletedOpenAiSmokeJob() {
        OpenAiSmokeProofService service = new OpenAiSmokeProofServiceImpl(
                new RecordingLocalizationJobQueryService(
                        completedJob(List.of(
                                call(ModelCallOperation.TRANSCRIPTION, ModelCallStatus.SUCCEEDED, null),
                                call(ModelCallOperation.TRANSLATION, ModelCallStatus.SUCCEEDED, null),
                                call(ModelCallOperation.EVALUATION, ModelCallStatus.SUCCEEDED, null),
                                call(ModelCallOperation.TTS, ModelCallStatus.SUCCEEDED, null)
                        ), qualityEvaluation())
                ),
                new RecordingJobArtifactService(fullArtifacts())
        );

        var proof = service.getProof("job-openai-smoke");

        assertThat(proof.jobId()).isEqualTo("job-openai-smoke");
        assertThat(proof.overallStatus()).isEqualTo("READY");
        assertThat(proof.phase()).isEqualTo("OPENAI_SMOKE_PROVEN");
        assertThat(proof.recommendedNextAction()).contains("present");
        assertThat(proof.requiredChecks())
                .extracting("status")
                .containsOnly("READY");
        assertThat(proof.modelCalls())
                .extracting("operation")
                .contains("TRANSCRIPTION", "TRANSLATION", "EVALUATION");
        assertThat(proof.artifacts())
                .extracting("type")
                .contains("TRANSCRIPT_JSON", "TARGET_SUBTITLE_JSON", "TARGET_SUBTITLE_SRT", "TARGET_SUBTITLE_VTT");
        assertThat(proof.safeLinks())
                .extracting("href")
                .contains(
                        "/api/jobs/job-openai-smoke",
                        "/api/jobs/job-openai-smoke/openai-smoke-proof/markdown/download",
                        "/api/jobs/job-openai-smoke/demo-run-package/download",
                        "/api/jobs/job-openai-smoke/ai-audit-package/download"
                );
    }

    @Test
    void blocksProofWhenRequiredOpenAiTranslationCallIsMissing() {
        OpenAiSmokeProofService service = new OpenAiSmokeProofServiceImpl(
                new RecordingLocalizationJobQueryService(
                        completedJob(List.of(call(ModelCallOperation.TRANSCRIPTION, ModelCallStatus.SUCCEEDED, null)), null)
                ),
                new RecordingJobArtifactService(requiredArtifacts())
        );

        var proof = service.getProof("job-openai-smoke");

        assertThat(proof.overallStatus()).isEqualTo("BLOCKED");
        assertThat(proof.requiredChecks())
                .anySatisfy(check -> {
                    assertThat(check.name()).isEqualTo("OpenAI translation call");
                    assertThat(check.status()).isEqualTo("BLOCKED");
                    assertThat(check.detail()).contains("Missing successful OpenAI TRANSLATION call");
                });
        assertThat(proof.recommendedNextAction()).contains("Inspect model calls");
    }

    @Test
    void warnsWhenRequiredProofIsReadyButOptionalEvidenceIsMissing() {
        OpenAiSmokeProofService service = new OpenAiSmokeProofServiceImpl(
                new RecordingLocalizationJobQueryService(
                        completedJob(List.of(
                                call(ModelCallOperation.TRANSCRIPTION, ModelCallStatus.SUCCEEDED, null),
                                call(ModelCallOperation.TRANSLATION, ModelCallStatus.SUCCEEDED, null)
                        ), null)
                ),
                new RecordingJobArtifactService(requiredArtifacts())
        );

        var proof = service.getProof("job-openai-smoke");

        assertThat(proof.overallStatus()).isEqualTo("ATTENTION");
        assertThat(proof.optionalChecks())
                .anySatisfy(check -> {
                    assertThat(check.name()).isEqualTo("Quality evaluation");
                    assertThat(check.status()).isEqualTo("ATTENTION");
                });
    }

    @Test
    void rendersSafeMarkdownWithoutSecretsOrRawContent() {
        OpenAiSmokeProofService service = new OpenAiSmokeProofServiceImpl(
                new RecordingLocalizationJobQueryService(
                        completedJob(List.of(
                                call(ModelCallOperation.TRANSCRIPTION, ModelCallStatus.SUCCEEDED, "provider request payload raw transcript text sk-test /Users/example"),
                                call(ModelCallOperation.TRANSLATION, ModelCallStatus.SUCCEEDED, null),
                                call(ModelCallOperation.TTS, ModelCallStatus.SUCCEEDED, null)
                        ), qualityEvaluation())
                ),
                new RecordingJobArtifactService(fullArtifacts())
        );

        String markdown = service.renderMarkdown("job-openai-smoke");

        assertThat(markdown)
                .contains("# LinguaFrame OpenAI Smoke Proof")
                .contains("- Job: job-openai-smoke")
                .contains("- Overall status: READY")
                .contains("OpenAI transcription call")
                .contains("OpenAI translation call")
                .contains("/api/jobs/job-openai-smoke/openai-smoke-proof/markdown/download");
        assertThat(markdown)
                .doesNotContain("provider request payload")
                .doesNotContain("raw transcript text")
                .doesNotContain("raw subtitle text")
                .doesNotContain("/Users/")
                .doesNotContain("job-artifacts/")
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("private-demo-token")
                .doesNotContain("sk-");
    }

    private static LocalizationJobVo completedJob(List<ModelCallVo> modelCalls, QualityEvaluationVo qualityEvaluation) {
        return new LocalizationJobVo(
                "job-openai-smoke",
                "video-openai-smoke",
                "zh-CN",
                "verse",
                LocalizationJobStatus.COMPLETED,
                Instant.parse("2026-06-29T01:00:00Z"),
                Instant.parse("2026-06-29T01:00:05Z"),
                Instant.parse("2026-06-29T01:03:00Z"),
                null,
                null,
                "provider request payload raw subtitle text sk-test /Users/example job-artifacts/raw.json OPENAI_API_KEY private-demo-token",
                0,
                JobDispatchEventStatus.DISPATCHED,
                1,
                Instant.parse("2026-06-29T01:00:02Z"),
                List.of(),
                new JobUsageSummaryVo(2, 0, 1200, BigDecimal.valueOf(0.012), 1000, 500, null, null),
                new JobCacheSummaryVo(0, 4, 0),
                modelCalls,
                qualityEvaluation,
                null,
                null
        );
    }

    private static ModelCallVo call(ModelCallOperation operation, ModelCallStatus status, String safeErrorSummary) {
        return new ModelCallVo(
                "model-call-" + operation.name().toLowerCase(),
                "job-openai-smoke",
                switch (operation) {
                    case TRANSCRIPTION -> LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT;
                    case TRANSLATION -> LocalizationJobStage.TARGET_SUBTITLE_EXPORT;
                    case EVALUATION -> LocalizationJobStage.TRANSLATION_QUALITY_EVALUATION;
                    case TTS -> LocalizationJobStage.DUBBING_AUDIO_GENERATION;
                    case SUBTITLE_POLISHING -> LocalizationJobStage.SUBTITLE_POLISHING;
                },
                operation,
                ModelCallProvider.OPENAI,
                "gpt-4o-mini",
                "prompt-v1",
                status,
                1200L,
                100,
                50,
                null,
                240,
                "segments=2",
                "segments=2",
                "demo-owner",
                BigDecimal.valueOf(0.0012),
                safeErrorSummary,
                Instant.parse("2026-06-29T01:02:00Z")
        );
    }

    private static List<JobArtifactVo> requiredArtifacts() {
        return List.of(
                artifact("transcript-json", JobArtifactType.TRANSCRIPT_JSON, "transcript.json", "application/json"),
                artifact("target-json", JobArtifactType.TARGET_SUBTITLE_JSON, "target-subtitles.zh-CN.json", "application/json"),
                artifact("target-srt", JobArtifactType.TARGET_SUBTITLE_SRT, "target-subtitles.zh-CN.srt", "application/x-subrip"),
                artifact("target-vtt", JobArtifactType.TARGET_SUBTITLE_VTT, "target-subtitles.zh-CN.vtt", "text/vtt"),
                artifact("burned-video", JobArtifactType.BURNED_VIDEO, "burned-video.mp4", "video/mp4")
        );
    }

    private static List<JobArtifactVo> fullArtifacts() {
        return List.of(
                artifact("transcript-json", JobArtifactType.TRANSCRIPT_JSON, "transcript.json", "application/json"),
                artifact("target-json", JobArtifactType.TARGET_SUBTITLE_JSON, "target-subtitles.zh-CN.json", "application/json"),
                artifact("target-srt", JobArtifactType.TARGET_SUBTITLE_SRT, "target-subtitles.zh-CN.srt", "application/x-subrip"),
                artifact("target-vtt", JobArtifactType.TARGET_SUBTITLE_VTT, "target-subtitles.zh-CN.vtt", "text/vtt"),
                artifact("dubbing-audio", JobArtifactType.DUBBING_AUDIO, "dubbing-audio.mp3", "audio/mpeg"),
                artifact("burned-video", JobArtifactType.BURNED_VIDEO, "burned-video.mp4", "video/mp4"),
                artifact("dubbed-video", JobArtifactType.DUBBED_VIDEO, "dubbed-video.mp4", "video/mp4")
        );
    }

    private static JobArtifactVo artifact(String id, JobArtifactType type, String filename, String contentType) {
        return new JobArtifactVo(
                id,
                "job-openai-smoke",
                type,
                filename,
                contentType,
                1024L,
                "hash-" + id,
                false,
                null,
                Instant.parse("2026-06-29T01:03:00Z")
        );
    }

    private static QualityEvaluationVo qualityEvaluation() {
        return new QualityEvaluationVo(
                "quality-openai-smoke",
                "job-openai-smoke",
                "zh-CN",
                91,
                "GOOD",
                93,
                91,
                90,
                89,
                List.of("No blocking issue."),
                List.of("Review terminology."),
                QualityEvaluationStatus.SUCCEEDED,
                null,
                Instant.parse("2026-06-29T01:02:30Z")
        );
    }

    private record RecordingLocalizationJobQueryService(LocalizationJobVo job) implements LocalizationJobQueryService {
        @Override
        public LocalizationJobListVo listJobs(LocalizationJobStatus status, Integer limit, Integer offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LocalizationJobVo getJob(String jobId) {
            return job;
        }

        @Override
        public JobDiagnosticsReportVo getDiagnosticsReport(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private record RecordingJobArtifactService(List<JobArtifactVo> artifacts) implements JobArtifactService {
        @Override
        public JobArtifactVo createArtifact(CreateJobArtifactCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JobArtifactVo createReusedArtifact(String jobId, JobArtifactRecord source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<JobArtifactVo> listArtifacts(String jobId) {
            return artifacts;
        }

        @Override
        public StoredObjectResourceBo openArtifact(String jobId, String artifactId) {
            throw new UnsupportedOperationException();
        }
    }
}
