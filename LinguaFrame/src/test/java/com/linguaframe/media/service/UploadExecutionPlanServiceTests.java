package com.linguaframe.media.service;

import com.linguaframe.common.quota.OwnerQuotaLimitVo;
import com.linguaframe.common.quota.OwnerQuotaPreflightService;
import com.linguaframe.common.quota.OwnerQuotaPreflightVo;
import com.linguaframe.media.domain.bo.UploadCostEstimateOptionsBo;
import com.linguaframe.media.domain.enums.MediaUploadValidationCode;
import com.linguaframe.media.domain.vo.DemoUploadReadinessCheckVo;
import com.linguaframe.media.domain.vo.DemoUploadReadinessVo;
import com.linguaframe.media.domain.vo.MediaUploadValidationVo;
import com.linguaframe.media.domain.vo.UploadCostEstimateBudgetVo;
import com.linguaframe.media.domain.vo.UploadCostEstimateStageVo;
import com.linguaframe.media.domain.vo.UploadCostEstimateVo;
import com.linguaframe.media.domain.vo.UploadExecutionPlanVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseCandidateVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseDecisionActionVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseDecisionVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseVo;
import com.linguaframe.media.service.impl.UploadSourceReuseDecisionServiceImpl;
import com.linguaframe.media.service.impl.UploadExecutionPlanServiceImpl;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UploadExecutionPlanServiceTests {

    private final StubUploadCostEstimateService costEstimateService = new StubUploadCostEstimateService();
    private final StubDemoUploadReadinessService readinessService = new StubDemoUploadReadinessService();
    private final StubOwnerQuotaPreflightService ownerQuotaPreflightService = new StubOwnerQuotaPreflightService();
    private final StubUploadSourceReuseService uploadSourceReuseService = new StubUploadSourceReuseService();
    private final UploadSourceReuseDecisionService uploadSourceReuseDecisionService = new UploadSourceReuseDecisionServiceImpl();
    private final UploadExecutionPlanService service = new UploadExecutionPlanServiceImpl(
            costEstimateService,
            readinessService,
            ownerQuotaPreflightService,
            uploadSourceReuseService,
            uploadSourceReuseDecisionService
    );

    @Test
    void createsReadyExecutionPlanForValidUpload() {
        costEstimateService.next = readyCostEstimate();
        readinessService.next = readiness("READY", List.of());
        ownerQuotaPreflightService.next = ownerPreflight(true);

        UploadExecutionPlanVo plan = service.plan(videoFile(), new UploadCostEstimateOptionsBo(
                "zh-CN",
                "",
                "FORMAL",
                "HIGH_CONTRAST",
                "Maya => 玛雅",
                "BALANCED",
                "tears-showcase"
        ));

        assertThat(plan.overallStatus()).isEqualTo("READY");
        assertThat(plan.recommendedNextAction()).contains("Upload");
        assertThat(plan.filename()).isEqualTo("sample.mp4");
        assertThat(plan.targetLanguage()).isEqualTo("zh-CN");
        assertThat(plan.demoProfileId()).isEqualTo("tears-showcase");
        assertThat(plan.estimatedCostUsd()).isEqualByComparingTo("0.01500000");
        assertThat(plan.estimatedDurationSecondsLower()).isGreaterThan(0);
        assertThat(plan.estimatedDurationSecondsUpper()).isGreaterThan(plan.estimatedDurationSecondsLower());
        assertThat(plan.stages())
                .extracting("id")
                .contains("audioExtraction", "transcription", "translation", "subtitlePolishing", "subtitleBurnIn");
        assertThat(plan.stages())
                .anySatisfy(stage -> {
                    assertThat(stage.id()).isEqualTo("translation");
                    assertThat(stage.executionType()).isEqualTo("PAID");
                    assertThat(stage.runnable()).isTrue();
                });
        assertThat(plan.gates())
                .extracting("id")
                .contains("uploadValidation", "uploadReadiness", "ownerQuota", "jobCost");
        assertThat(plan.commands())
                .anySatisfy(command -> {
                    assertThat(command.id()).isEqualTo("upload");
                    assertThat(command.command()).contains("scripts/demo/docker-e2e");
                });
        assertThat(plan.sourceReuse().recommendedAction()).isEqualTo("UPLOAD_NEW_SOURCE");
        assertThat(plan.sourceReuse().candidateCount()).isZero();
        assertThat(plan.sourceReuseDecision().status()).isEqualTo("UPLOAD_NEW_SOURCE");
        assertThat(plan.narrationScriptIntake().status()).isEqualTo("READY");
        assertThat(plan.narrationScriptIntake().segmentCount()).isZero();
    }

    @Test
    void includesSafeNarrationScriptIntakeMetadata() {
        costEstimateService.next = readyCostEstimate();
        readinessService.next = readiness("READY", List.of());
        ownerQuotaPreflightService.next = ownerPreflight(true);

        UploadExecutionPlanVo plan = service.plan(videoFile(), new UploadCostEstimateOptionsBo(
                "zh-CN",
                "",
                "FORMAL",
                "HIGH_CONTRAST",
                "Maya => 玛雅",
                "BALANCED",
                "tears-showcase",
                """
                        00:15-00:28 | demo-voice | Explain the opening gesture.
                        00:55-01:10 || Inherit the default voice.
                        """
        ));

        assertThat(plan.overallStatus()).isEqualTo("READY");
        assertThat(plan.narrationScriptIntake().status()).isEqualTo("READY");
        assertThat(plan.narrationScriptIntake().segmentCount()).isEqualTo(2);
        assertThat(plan.narrationScriptIntake().characterCount()).isEqualTo(54);
        assertThat(plan.narrationScriptIntake().voiceSummary()).isEqualTo("demo-voice: 1, inherited: 1");
        assertThat(plan.narrationScriptIntake().errors()).isEmpty();
        assertThat(plan.gates())
                .anySatisfy(gate -> {
                    assertThat(gate.id()).isEqualTo("narrationScriptIntake");
                    assertThat(gate.status()).isEqualTo("READY");
                    assertThat(gate.detail()).contains("2 narration script rows");
                });
    }

    @Test
    void blocksInvalidNarrationScriptIntake() {
        costEstimateService.next = readyCostEstimate();
        readinessService.next = readiness("READY", List.of());
        ownerQuotaPreflightService.next = ownerPreflight(true);

        UploadExecutionPlanVo plan = service.plan(videoFile(), new UploadCostEstimateOptionsBo(
                "zh-CN",
                "",
                "FORMAL",
                "HIGH_CONTRAST",
                "",
                "BALANCED",
                "quick-baseline",
                "00:20-00:10 | demo-voice | Backwards."
        ));

        assertThat(plan.overallStatus()).isEqualTo("BLOCKED");
        assertThat(plan.recommendedNextAction()).contains("blocking");
        assertThat(plan.narrationScriptIntake().status()).isEqualTo("BLOCKED");
        assertThat(plan.narrationScriptIntake().errors())
                .containsExactly("Row 1: end time must be greater than start time.");
        assertThat(plan.gates())
                .anySatisfy(gate -> {
                    assertThat(gate.id()).isEqualTo("narrationScriptIntake");
                    assertThat(gate.status()).isEqualTo("BLOCKED");
                    assertThat(gate.blocking()).isTrue();
                });
    }

    @Test
    void blocksUnknownNarrationVoiceInDemoMode() {
        costEstimateService.next = readyCostEstimate();
        readinessService.next = readiness("READY", List.of());
        ownerQuotaPreflightService.next = ownerPreflight(true);

        UploadExecutionPlanVo plan = service.plan(videoFile(), new UploadCostEstimateOptionsBo(
                "zh-CN",
                "",
                "FORMAL",
                "HIGH_CONTRAST",
                "",
                "BALANCED",
                "quick-baseline",
                "00:15-00:28 | alloy | OpenAI voice is not available in demo mode."
        ));

        assertThat(plan.overallStatus()).isEqualTo("BLOCKED");
        assertThat(plan.narrationScriptIntake().status()).isEqualTo("BLOCKED");
        assertThat(plan.narrationScriptIntake().errors())
                .containsExactly("Row 1: narration voice must be one of the configured presets.");
        assertThat(plan.gates())
                .anySatisfy(gate -> {
                    assertThat(gate.id()).isEqualTo("narrationScriptIntake");
                    assertThat(gate.status()).isEqualTo("BLOCKED");
                    assertThat(gate.blocking()).isTrue();
                });
    }

    @Test
    void blocksInvalidFileWithoutRunnableProviderStages() {
        costEstimateService.next = invalidCostEstimate();
        readinessService.next = readiness("READY", List.of());
        ownerQuotaPreflightService.next = ownerPreflight(true);

        UploadExecutionPlanVo plan = service.plan(videoFile(), UploadCostEstimateOptionsBo.empty());

        assertThat(plan.overallStatus()).isEqualTo("BLOCKED");
        assertThat(plan.validationCode()).isEqualTo("DURATION_TOO_LONG");
        assertThat(plan.recommendedNextAction()).contains("Replace");
        assertThat(plan.stages()).isEmpty();
        assertThat(plan.sourceReuse().sourceContentSha256()).isNull();
        assertThat(plan.sourceReuse().candidateCount()).isZero();
        assertThat(plan.sourceReuseDecision().status()).isEqualTo("UPLOAD_NEW_SOURCE");
        assertThat(plan.gates())
                .anySatisfy(gate -> {
                    assertThat(gate.id()).isEqualTo("uploadValidation");
                    assertThat(gate.status()).isEqualTo("BLOCKED");
                    assertThat(gate.blocking()).isTrue();
                });
    }

    @Test
    void blocksWhenReadinessOrOwnerQuotaBlocksUpload() {
        costEstimateService.next = readyCostEstimate();
        readinessService.next = readiness(
                "BLOCKED",
                List.of(new DemoUploadReadinessCheckVo(
                        "live-dependencies",
                        "Live dependencies",
                        "BLOCKED",
                        "A required dependency probe is down: redis.",
                        "Run scripts/demo/private-demo-preflight.sh.",
                        true
                ))
        );
        ownerQuotaPreflightService.next = ownerPreflight(false);

        UploadExecutionPlanVo plan = service.plan(videoFile(), UploadCostEstimateOptionsBo.empty());

        assertThat(plan.overallStatus()).isEqualTo("BLOCKED");
        assertThat(plan.gates())
                .anySatisfy(gate -> {
                    assertThat(gate.id()).isEqualTo("live-dependencies");
                    assertThat(gate.status()).isEqualTo("BLOCKED");
                    assertThat(gate.blocking()).isTrue();
                })
                .anySatisfy(gate -> {
                    assertThat(gate.id()).isEqualTo("ownerQuota");
                    assertThat(gate.status()).isEqualTo("BLOCKED");
                    assertThat(gate.detail()).contains("Owner quota is already blocked");
                });
        assertThat(plan.recommendedNextAction()).contains("blocking");
    }

    @Test
    void includesSourceReuseRecommendationForDuplicateCompletedRun() {
        costEstimateService.next = readyCostEstimate();
        readinessService.next = readiness("READY", List.of());
        ownerQuotaPreflightService.next = ownerPreflight(true);
        uploadSourceReuseService.next = new UploadSourceReuseVo(
                "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81",
                1,
                "REVIEW_EXISTING_COMPLETED_RUN",
                "job-existing",
                List.of(new UploadSourceReuseCandidateVo(
                        "video-existing",
                        "job-existing",
                        "sample.mp4",
                        90,
                        LocalizationJobStatus.COMPLETED,
                        "tears-showcase",
                        "FORMAL",
                        "HIGH_CONTRAST",
                        "BALANCED",
                        Instant.parse("2026-06-28T12:00:00Z"),
                        "/api/jobs/job-existing",
                        "/api/jobs/job-existing/demo-share-sheet",
                        "/api/jobs/job-existing/evidence/markdown/download",
                        "/api/jobs/job-existing/demo-run-package/download",
                        "/api/jobs/job-existing/demo-acceptance-gate"
                ))
        );

        UploadExecutionPlanVo plan = service.plan(videoFile(), UploadCostEstimateOptionsBo.empty());

        assertThat(plan.sourceReuse().sourceContentSha256()).isEqualTo("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81");
        assertThat(plan.sourceReuse().recommendedAction()).isEqualTo("REVIEW_EXISTING_COMPLETED_RUN");
        assertThat(plan.sourceReuse().recommendedExistingJobId()).isEqualTo("job-existing");
        assertThat(plan.sourceReuse().candidates())
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.videoId()).isEqualTo("video-existing");
                    assertThat(candidate.jobStatus()).isEqualTo(LocalizationJobStatus.COMPLETED);
                    assertThat(candidate.demoProfileId()).isEqualTo("tears-showcase");
                });
        assertThat(plan.sourceReuseDecision().status()).isEqualTo("REUSE_COMPLETED_RUN");
        assertThat(plan.sourceReuseDecision().actions())
                .extracting(UploadSourceReuseDecisionActionVo::id)
                .contains("openJob", "downloadPackage");
    }

    private static MockMultipartFile videoFile() {
        return new MockMultipartFile("file", "sample.mp4", "video/mp4", new byte[] {1, 2, 3});
    }

    private static UploadCostEstimateVo readyCostEstimate() {
        return new UploadCostEstimateVo(
                "READY",
                "Upload can proceed with the selected profile and options.",
                "sample.mp4",
                "video/mp4",
                3,
                104857600,
                90,
                300,
                true,
                "READY",
                "File is ready for upload.",
                "zh-CN",
                null,
                "FORMAL",
                "HIGH_CONTRAST",
                1,
                "glossary-hash",
                "BALANCED",
                "tears-showcase",
                new BigDecimal("0.01000000"),
                new BigDecimal("0.01500000"),
                new BigDecimal("0.02000000"),
                List.of(
                        new UploadCostEstimateStageVo("audioExtraction", "Audio extraction", "LOCAL", "ffmpeg", "ffmpeg", false, BigDecimal.ZERO.setScale(8), "90 seconds", "Local media preparation."),
                        new UploadCostEstimateStageVo("transcription", "Transcription", "ESTIMATED", "openai", "whisper-1", true, new BigDecimal("0.00900000"), "90 audio seconds", "Estimated from rounded upload duration."),
                        new UploadCostEstimateStageVo("translation", "Translation", "ESTIMATED", "openai", "gpt-4.1-mini", true, new BigDecimal("0.00400000"), "500 input tokens", "Includes style prompt."),
                        new UploadCostEstimateStageVo("subtitlePolishing", "Subtitle polishing", "ESTIMATED", "openai", "gpt-4.1-mini", true, new BigDecimal("0.00200000"), "BALANCED", "Estimated from translated subtitle volume."),
                        new UploadCostEstimateStageVo("subtitleBurnIn", "Subtitle burn-in", "LOCAL", "ffmpeg", "ffmpeg", false, BigDecimal.ZERO.setScale(8), "HIGH_CONTRAST", "Local rendering step.")
                ),
                List.of(
                        new UploadCostEstimateBudgetVo("jobCost", "Per-job cost guard", true, "READY", BigDecimal.ZERO.setScale(8), new BigDecimal("0.01500000"), new BigDecimal("0.01500000"), new BigDecimal("1.00000000"), "Projected job cost."),
                        new UploadCostEstimateBudgetVo("ownerDailyCost", "Owner daily quota", true, "READY", new BigDecimal("0.10000000"), new BigDecimal("0.01500000"), new BigDecimal("0.11500000"), new BigDecimal("2.00000000"), "Owner projected daily cost.")
                ),
                List.of("Estimate does not assume cache hits."),
                List.of("Only safe media metadata is returned.")
        );
    }

    private static UploadCostEstimateVo invalidCostEstimate() {
        return new UploadCostEstimateVo(
                "BLOCKED",
                "Replace the source video or choose media inside the configured upload limits.",
                "long.mp4",
                "video/mp4",
                10,
                104857600,
                301,
                300,
                false,
                "DURATION_TOO_LONG",
                "The uploaded video exceeds the 300 second duration limit.",
                "zh-CN",
                null,
                "NATURAL",
                "STANDARD",
                0,
                "",
                "OFF",
                null,
                BigDecimal.ZERO.setScale(8),
                BigDecimal.ZERO.setScale(8),
                BigDecimal.ZERO.setScale(8),
                List.of(),
                List.of(new UploadCostEstimateBudgetVo("jobCost", "Per-job cost guard", false, "READY", BigDecimal.ZERO.setScale(8), BigDecimal.ZERO.setScale(8), BigDecimal.ZERO.setScale(8), BigDecimal.ZERO.setScale(8), "Disabled.")),
                List.of("No provider stages are estimated until the file passes upload validation."),
                List.of("The uploaded video exceeds the 300 second duration limit.")
        );
    }

    private static DemoUploadReadinessVo readiness(String status, List<DemoUploadReadinessCheckVo> checks) {
        return new DemoUploadReadinessVo(
                status,
                "demo-owner",
                "tears-showcase",
                Instant.parse("2026-06-29T00:00:00Z"),
                checks,
                "BLOCKED".equals(status) ? List.of("Resolve blocking upload readiness checks before uploading media.") : List.of(),
                List.of("/api/media/uploads/readiness")
        );
    }

    private static OwnerQuotaPreflightVo ownerPreflight(boolean allowed) {
        return new OwnerQuotaPreflightVo(
                "demo-owner",
                true,
                allowed,
                0,
                0,
                new BigDecimal("0.10000000"),
                LocalDate.parse("2026-06-29"),
                List.of(new OwnerQuotaLimitVo("dailyCostUsd", true, new BigDecimal("2.00000000"), new BigDecimal("0.10000000"))),
                allowed ? List.of() : List.of("Owner quota is already blocked.")
        );
    }

    private static final class StubUploadCostEstimateService implements UploadCostEstimateService {
        private UploadCostEstimateVo next = readyCostEstimate();

        @Override
        public UploadCostEstimateVo estimate(MultipartFile file, UploadCostEstimateOptionsBo options) {
            return next;
        }
    }

    private static final class StubDemoUploadReadinessService implements DemoUploadReadinessService {
        private DemoUploadReadinessVo next = readiness("READY", List.of());

        @Override
        public DemoUploadReadinessVo getReadiness(String demoProfileId) {
            return next;
        }
    }

    private static final class StubOwnerQuotaPreflightService implements OwnerQuotaPreflightService {
        private OwnerQuotaPreflightVo next = ownerPreflight(true);

        @Override
        public OwnerQuotaPreflightVo getPreflight() {
            return next;
        }

        @Override
        public void requireUploadAllowed() {
        }
    }

    private static final class StubUploadSourceReuseService implements UploadSourceReuseService {
        private UploadSourceReuseVo next = UploadSourceReuseVo.empty();

        @Override
        public UploadSourceReuseVo evaluate(MultipartFile file, UploadCostEstimateVo estimate, UploadCostEstimateOptionsBo options) {
            if (!estimate.valid()) {
                return UploadSourceReuseVo.empty();
            }
            return next;
        }
    }
}
