package com.linguaframe.media.service;

import com.linguaframe.media.domain.vo.UploadExecutionPlanCommandVo;
import com.linguaframe.media.domain.vo.UploadExecutionPlanGateVo;
import com.linguaframe.media.domain.vo.UploadExecutionPlanStageVo;
import com.linguaframe.media.domain.vo.UploadExecutionPlanVo;
import com.linguaframe.media.domain.vo.UploadNarrationScriptIntakeVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseDecisionActionVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseDecisionLinkVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseDecisionVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseVo;
import com.linguaframe.media.service.impl.UploadExecutionPlanReportServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UploadExecutionPlanReportServiceTests {

    private final UploadExecutionPlanReportService service = new UploadExecutionPlanReportServiceImpl();

    @Test
    void rendersReadyPlanWithReuseLinksAndSafeMetadata() {
        String markdown = service.renderMarkdown(readyPlan());

        assertThat(markdown)
                .contains("# Upload Execution Plan")
                .contains("## Source Metadata")
                .contains("sample.mp4")
                .contains("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81")
                .contains("## Source Reuse Decision")
                .contains("REUSE_COMPLETED_RUN")
                .contains("/api/jobs/job-existing/demo-run-package/download")
                .contains("## Gates")
                .contains("## Stages")
                .contains("## Commands")
                .doesNotContain("source-videos/")
                .doesNotContain("/Users/")
                .doesNotContain("sk-");
    }

    @Test
    void rendersInvalidPlanWithoutStages() {
        String markdown = service.renderMarkdown(invalidPlan());

        assertThat(markdown)
                .contains("Status: BLOCKED")
                .contains("Valid: false")
                .contains("UNSUPPORTED_CONTENT_TYPE")
                .contains("No runnable stages are planned until upload validation passes.")
                .contains("No previous source match found.");
    }

    private static UploadExecutionPlanVo readyPlan() {
        UploadSourceReuseVo sourceReuse = new UploadSourceReuseVo(
                "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81",
                1,
                "REVIEW_EXISTING_COMPLETED_RUN",
                "job-existing",
                List.of()
        );
        UploadSourceReuseDecisionVo decision = new UploadSourceReuseDecisionVo(
                "REUSE_COMPLETED_RUN",
                "Existing completed run found for this source.",
                "Review the completed job evidence before uploading this same source again.",
                "REVIEW_EXISTING_COMPLETED_RUN",
                "job-existing",
                1,
                List.of(new UploadSourceReuseDecisionActionVo("openJob", "Open existing job", "LINK", true, "Inspect the completed same-source job.", "/api/jobs/job-existing")),
                List.of(new UploadSourceReuseDecisionLinkVo("DEMO_RUN_PACKAGE", "Demo run package", "/api/jobs/job-existing/demo-run-package/download")),
                List.of("Source reuse decision is read-only and does not store media or call providers."),
                sourceReuse
        );
        return new UploadExecutionPlanVo(
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
                20,
                60,
                List.of(new UploadExecutionPlanStageVo("translation", "Translation", "ESTIMATED", "PAID", "openai", "gpt-4.1-mini", true, new BigDecimal("0.00400000"), 3, 9, "Includes style prompt.")),
                List.of(new UploadExecutionPlanGateVo("uploadValidation", "Upload validation", "READY", false, "File is ready for upload.", "No validation action required.")),
                List.of(new UploadExecutionPlanCommandVo("upload", "Run upload demo", "scripts/demo/docker-e2e-success.sh", "Run the selected demo upload.")),
                sourceReuse,
                decision,
                narrationIntake(),
                List.of("Estimate does not assume cache hits."),
                List.of("Execution plan is read-only and does not store media or call providers.")
        );
    }

    private static UploadExecutionPlanVo invalidPlan() {
        UploadSourceReuseVo sourceReuse = UploadSourceReuseVo.empty();
        UploadSourceReuseDecisionVo decision = new UploadSourceReuseDecisionVo(
                "UPLOAD_NEW_SOURCE",
                "No previous source match found.",
                "Upload can proceed because no same-owner source fingerprint match was found.",
                "UPLOAD_NEW_SOURCE",
                null,
                0,
                List.of(),
                List.of(),
                List.of("No same-owner fingerprint match was found for this file."),
                sourceReuse
        );
        return new UploadExecutionPlanVo(
                "BLOCKED",
                "Replace the source video or choose media inside the configured upload limits.",
                "notes.txt",
                "text/plain",
                1,
                104857600,
                null,
                300,
                false,
                "UNSUPPORTED_CONTENT_TYPE",
                "Only MP4 and QuickTime videos are supported.",
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
                0,
                0,
                List.of(),
                List.of(new UploadExecutionPlanGateVo("uploadValidation", "Upload validation", "BLOCKED", true, "Only MP4 and QuickTime videos are supported.", "Replace the source video or change upload limits.")),
                List.of(),
                sourceReuse,
                decision,
                narrationIntake(),
                List.of(),
                List.of("Only safe media metadata is returned.")
        );
    }

    private static UploadNarrationScriptIntakeVo narrationIntake() {
        return new UploadNarrationScriptIntakeVo("READY", false, 0, 0, "none", List.of());
    }
}
