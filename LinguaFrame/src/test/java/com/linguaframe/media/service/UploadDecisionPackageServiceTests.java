package com.linguaframe.media.service;

import com.linguaframe.common.quota.OwnerQuotaLimitVo;
import com.linguaframe.common.quota.OwnerQuotaPreflightService;
import com.linguaframe.common.quota.OwnerQuotaPreflightVo;
import com.linguaframe.media.domain.bo.UploadCostEstimateOptionsBo;
import com.linguaframe.media.domain.vo.DemoUploadReadinessCheckVo;
import com.linguaframe.media.domain.vo.DemoUploadReadinessVo;
import com.linguaframe.media.domain.vo.UploadDecisionPackageVo;
import com.linguaframe.media.domain.vo.UploadExecutionPlanCommandVo;
import com.linguaframe.media.domain.vo.UploadExecutionPlanGateVo;
import com.linguaframe.media.domain.vo.UploadExecutionPlanStageVo;
import com.linguaframe.media.domain.vo.UploadExecutionPlanVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseDecisionActionVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseDecisionLinkVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseDecisionVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseVo;
import com.linguaframe.media.service.impl.UploadDecisionPackageServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UploadDecisionPackageServiceTests {

    private final UploadExecutionPlanService uploadExecutionPlanService = mock(UploadExecutionPlanService.class);
    private final OwnerQuotaPreflightService ownerQuotaPreflightService = mock(OwnerQuotaPreflightService.class);
    private final DemoUploadReadinessService demoUploadReadinessService = mock(DemoUploadReadinessService.class);
    private final UploadExecutionPlanReportService uploadExecutionPlanReportService = mock(UploadExecutionPlanReportService.class);
    private final UploadDecisionPackageService service = new UploadDecisionPackageServiceImpl(
            uploadExecutionPlanService,
            ownerQuotaPreflightService,
            demoUploadReadinessService,
            uploadExecutionPlanReportService
    );

    @Test
    void buildsUploadNewSourcePackageForReadyPlan() {
        MockMultipartFile file = sampleFile();
        UploadExecutionPlanVo plan = plan("READY", decision("UPLOAD_NEW_SOURCE", null, 0));
        when(uploadExecutionPlanService.plan(file, UploadCostEstimateOptionsBo.empty())).thenReturn(plan);
        when(ownerQuotaPreflightService.getPreflight()).thenReturn(ownerQuota(true));
        when(demoUploadReadinessService.getReadiness("tears-showcase")).thenReturn(readiness("READY"));
        when(uploadExecutionPlanReportService.renderMarkdown(plan)).thenReturn("# Upload Execution Plan\n");

        UploadDecisionPackageVo value = service.build(file, UploadCostEstimateOptionsBo.empty());
        String markdown = service.renderMarkdown(value);

        assertThat(value.overallStatus()).isEqualTo("READY");
        assertThat(value.recommendedDecision()).isEqualTo("UPLOAD_NEW_SOURCE");
        assertThat(value.executionPlanMarkdown()).contains("# Upload Execution Plan");
        assertThat(markdown)
                .contains("# Upload Decision Package")
                .contains("## Owner Quota")
                .contains("## Upload Readiness")
                .contains("## Execution Plan Summary")
                .contains("## Package Contents")
                .doesNotContain("source-videos/")
                .doesNotContain("/Users/")
                .doesNotContain("sk-");
    }

    @Test
    void blocksPackageWhenPlanOrReadinessIsBlocked() {
        MockMultipartFile file = sampleFile();
        UploadExecutionPlanVo plan = plan("BLOCKED", decision("UPLOAD_NEW_SOURCE", null, 0));
        when(uploadExecutionPlanService.plan(file, UploadCostEstimateOptionsBo.empty())).thenReturn(plan);
        when(ownerQuotaPreflightService.getPreflight()).thenReturn(ownerQuota(false));
        when(demoUploadReadinessService.getReadiness("tears-showcase")).thenReturn(readiness("BLOCKED"));
        when(uploadExecutionPlanReportService.renderMarkdown(plan)).thenReturn("# Upload Execution Plan\n");

        UploadDecisionPackageVo value = service.build(file, UploadCostEstimateOptionsBo.empty());

        assertThat(value.overallStatus()).isEqualTo("BLOCKED");
        assertThat(value.recommendedDecision()).isEqualTo("BLOCKED");
        assertThat(value.recommendedNextAction()).contains("Resolve blocking upload gates");
    }

    @Test
    void recommendsCompletedRunReuseWhenSafeDuplicateExists() {
        MockMultipartFile file = sampleFile();
        UploadExecutionPlanVo plan = plan("READY", decision("REUSE_COMPLETED_RUN", "job-existing", 1));
        when(uploadExecutionPlanService.plan(file, UploadCostEstimateOptionsBo.empty())).thenReturn(plan);
        when(ownerQuotaPreflightService.getPreflight()).thenReturn(ownerQuota(true));
        when(demoUploadReadinessService.getReadiness("tears-showcase")).thenReturn(readiness("READY"));
        when(uploadExecutionPlanReportService.renderMarkdown(plan)).thenReturn("# Upload Execution Plan\n");

        UploadDecisionPackageVo value = service.build(file, UploadCostEstimateOptionsBo.empty());
        String markdown = service.renderMarkdown(value);

        assertThat(value.recommendedDecision()).isEqualTo("REUSE_COMPLETED_RUN");
        assertThat(value.recommendedNextAction()).contains("existing completed run package");
        assertThat(markdown)
                .contains("REUSE_COMPLETED_RUN")
                .contains("job-existing")
                .contains("/api/jobs/job-existing/demo-run-package/download");
    }

    private static MockMultipartFile sampleFile() {
        return new MockMultipartFile("file", "sample.mp4", "video/mp4", new byte[] {1, 2, 3});
    }

    private static OwnerQuotaPreflightVo ownerQuota(boolean allowed) {
        return new OwnerQuotaPreflightVo(
                "demo-owner",
                true,
                allowed,
                allowed ? 0 : 2,
                0,
                new BigDecimal("0.00000000"),
                LocalDate.parse("2026-06-29"),
                List.of(new OwnerQuotaLimitVo("activeJobs", true, new BigDecimal("2"), new BigDecimal(allowed ? "0" : "2"))),
                allowed ? List.of() : List.of("Owner active job limit reached.")
        );
    }

    private static DemoUploadReadinessVo readiness(String status) {
        return new DemoUploadReadinessVo(
                status,
                "demo-owner",
                "tears-showcase",
                Instant.parse("2026-06-29T00:00:00Z"),
                List.of(new DemoUploadReadinessCheckVo(
                        "runtime",
                        "Runtime",
                        status,
                        "Runtime checks are visible.",
                        "No runtime action required.",
                        "BLOCKED".equals(status)
                )),
                "BLOCKED".equals(status) ? List.of("Start required dependencies.") : List.of(),
                List.of("/api/runtime/dependencies")
        );
    }

    private static UploadExecutionPlanVo plan(String status, UploadSourceReuseDecisionVo decision) {
        UploadSourceReuseVo sourceReuse = decision.sourceReuse();
        return new UploadExecutionPlanVo(
                status,
                "READY".equals(status)
                        ? "Upload can proceed with the selected profile and options."
                        : "Resolve blocking gates before uploading media.",
                "sample.mp4",
                "video/mp4",
                3,
                104857600,
                90,
                300,
                "READY".equals(status),
                "READY".equals(status) ? "READY" : "UNSUPPORTED_CONTENT_TYPE",
                "READY".equals(status) ? "File is ready for upload." : "Only MP4 and QuickTime videos are supported.",
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
                "READY".equals(status) ? 20 : 0,
                "READY".equals(status) ? 60 : 0,
                "READY".equals(status)
                        ? List.of(new UploadExecutionPlanStageVo("translation", "Translation", "ESTIMATED", "PAID", "openai", "gpt-4.1-mini", true, new BigDecimal("0.00400000"), 3, 9, "Includes style prompt."))
                        : List.of(),
                List.of(new UploadExecutionPlanGateVo("uploadValidation", "Upload validation", status, "BLOCKED".equals(status), "Validation detail.", "Next action.")),
                List.of(new UploadExecutionPlanCommandVo("upload", "Run upload demo", "scripts/demo/docker-e2e-success.sh", "Run the selected demo upload.")),
                sourceReuse,
                decision,
                List.of(),
                List.of("Execution plan is read-only and does not store media or call providers.")
        );
    }

    private static UploadSourceReuseDecisionVo decision(String status, String jobId, int candidateCount) {
        UploadSourceReuseVo sourceReuse = new UploadSourceReuseVo(
                "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81",
                candidateCount,
                candidateCount > 0 ? "REVIEW_EXISTING_COMPLETED_RUN" : "UPLOAD_NEW_SOURCE",
                jobId,
                List.of()
        );
        return new UploadSourceReuseDecisionVo(
                status,
                candidateCount > 0 ? "Existing completed run found for this source." : "No previous source match found.",
                candidateCount > 0
                        ? "Review the completed job evidence before uploading this same source again."
                        : "Upload can proceed because no same-owner source fingerprint match was found.",
                candidateCount > 0 ? "REVIEW_EXISTING_COMPLETED_RUN" : "UPLOAD_NEW_SOURCE",
                jobId,
                candidateCount,
                jobId == null ? List.of() : List.of(new UploadSourceReuseDecisionActionVo("openJob", "Open existing job", "LINK", true, "Inspect the completed same-source job.", "/api/jobs/" + jobId)),
                jobId == null ? List.of() : List.of(new UploadSourceReuseDecisionLinkVo("DEMO_RUN_PACKAGE", "Demo run package", "/api/jobs/" + jobId + "/demo-run-package/download")),
                List.of("Source reuse decision is read-only and does not store media or call providers."),
                sourceReuse
        );
    }
}
