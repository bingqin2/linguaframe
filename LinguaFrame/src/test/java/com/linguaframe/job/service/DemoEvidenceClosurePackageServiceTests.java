package com.linguaframe.job.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.linguaframe.job.domain.bo.StoredDemoEvidenceClosurePackageBo;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateCheckVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateEvidenceVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateLinkVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateVo;
import com.linguaframe.job.domain.vo.DemoCompletionCertificateCheckVo;
import com.linguaframe.job.domain.vo.DemoCompletionCertificateLinkVo;
import com.linguaframe.job.domain.vo.DemoCompletionCertificateSectionVo;
import com.linguaframe.job.domain.vo.DemoCompletionCertificateVo;
import com.linguaframe.job.domain.vo.DemoEvidenceClosurePackageVo;
import com.linguaframe.job.domain.vo.DemoRunVarianceMetricVo;
import com.linguaframe.job.domain.vo.DemoRunVarianceReportVo;
import com.linguaframe.job.service.impl.DemoEvidenceClosurePackageServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DemoEvidenceClosurePackageServiceTests {

    private final DemoRunVarianceReportService varianceReportService = mock(DemoRunVarianceReportService.class);
    private final DemoAcceptanceGateService acceptanceGateService = mock(DemoAcceptanceGateService.class);
    private final DemoCompletionCertificateService completionCertificateService = mock(DemoCompletionCertificateService.class);
    private final DemoEvidenceClosurePackageService service = new DemoEvidenceClosurePackageServiceImpl(
            varianceReportService,
            acceptanceGateService,
            completionCertificateService,
            JsonMapper.builder().addModule(new JavaTimeModule()).build()
    );

    @Test
    void buildsReadyClosureWithExecutionPlanBaseline() throws Exception {
        when(varianceReportService.build("job-1", "{\"overallStatus\":\"READY\"}")).thenReturn(variance("READY", "EXECUTION_PLAN"));
        when(varianceReportService.renderMarkdown(variance("READY", "EXECUTION_PLAN"))).thenReturn("# Demo Run Variance Report\n");
        when(acceptanceGateService.buildGate("job-1")).thenReturn(gate("READY"));
        when(completionCertificateService.buildCertificate("job-1")).thenReturn(certificate("READY"));

        DemoEvidenceClosurePackageVo closure = service.buildClosure("job-1", "{\"overallStatus\":\"READY\"}");
        String markdown = service.renderMarkdown(closure);
        StoredDemoEvidenceClosurePackageBo zip = service.openClosurePackage("job-1", "{\"overallStatus\":\"READY\"}");

        assertThat(closure.closureStatus()).isEqualTo("READY");
        assertThat(closure.baselineMode()).isEqualTo("EXECUTION_PLAN");
        assertThat(closure.sections()).extracting("key").contains(
                "PRE_UPLOAD_BASELINE",
                "POST_RUN_VARIANCE",
                "ACCEPTANCE_GATE",
                "COMPLETION_CERTIFICATE",
                "CUSTOM_NARRATION_RENDER",
                "DELIVERY_PACKAGE",
                "REVIEWER_HANDOFF"
        );
        assertThat(closure.safeLinks()).contains(
                "/api/jobs/job-1/demo-run-package/download",
                "/api/jobs/job-1/custom-narration-render/markdown/download",
                "/api/jobs/job-1/demo-evidence-closure/download"
        );
        assertThat(markdown).contains(
                "# Demo Evidence Closure Package",
                "## Summary",
                "## Baseline",
                "## Post-Run Variance",
                "## Acceptance",
                "## Completion",
                "## Custom Narration Render",
                "## Safe Links",
                "## Safety Notes"
        );
        assertThat(markdown).doesNotContain("/Users/", "source-videos/", "raw provider payload", "sk-");
        assertThat(zip.filename()).isEqualTo("linguaframe-job-job-1-demo-evidence-closure.zip");
        assertThat(zip.contentType()).isEqualTo("application/zip");
        assertThat(zip.sizeBytes()).isPositive();
        assertThat(zipEntries(zip.inputStream())).containsExactlyInAnyOrder(
                "manifest.json",
                "demo-evidence-closure.md",
                "demo-run-variance.md",
                "README.md"
        );
    }

    @Test
    void buildsActualOnlyClosureWithoutBaseline() {
        when(varianceReportService.build("job-1", null)).thenReturn(variance("READY", "MISSING"));
        when(acceptanceGateService.buildGate("job-1")).thenReturn(gate("READY"));
        when(completionCertificateService.buildCertificate("job-1")).thenReturn(certificate("READY"));

        DemoEvidenceClosurePackageVo closure = service.buildClosure("job-1", null);

        assertThat(closure.baselineMode()).isEqualTo("MISSING");
        assertThat(closure.closureStatus()).isEqualTo("READY");
        assertThat(closure.sections()).filteredOn(section -> section.key().equals("PRE_UPLOAD_BASELINE"))
                .singleElement()
                .satisfies(section -> assertThat(section.status()).isEqualTo("ATTENTION"));
    }

    @Test
    void buildsAttentionClosureForInvalidBaseline() {
        when(varianceReportService.build("job-1", "{bad")).thenReturn(variance("ATTENTION", "INVALID"));
        when(acceptanceGateService.buildGate("job-1")).thenReturn(gate("READY"));
        when(completionCertificateService.buildCertificate("job-1")).thenReturn(certificate("READY"));

        DemoEvidenceClosurePackageVo closure = service.buildClosure("job-1", "{bad");

        assertThat(closure.baselineMode()).isEqualTo("INVALID");
        assertThat(closure.closureStatus()).isEqualTo("ATTENTION");
        assertThat(closure.recommendedNextAction()).contains("baseline JSON");
    }

    private List<String> zipEntries(InputStream inputStream) throws Exception {
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            java.util.ArrayList<String> entries = new java.util.ArrayList<>();
            java.util.zip.ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
            return entries;
        }
    }

    private DemoRunVarianceReportVo variance(String status, String baselineMode) {
        return new DemoRunVarianceReportVo(
                "job-1",
                "video-1",
                Instant.parse("2026-06-29T12:00:00Z"),
                status,
                baselineMode,
                "COMPLETED",
                "zh-CN",
                "tears-showcase",
                "Use delivery package as evidence.",
                List.of(new DemoRunVarianceMetricVo(
                        "estimatedCostUsd",
                        "Estimated cost USD",
                        "LOWER_THAN_ESTIMATE",
                        "0.01000000",
                        "0.00007800",
                        "Cost comparison."
                )),
                baselineMode.equals("MISSING")
                        ? List.of("No pre-upload baseline was supplied; report is actual-only.")
                        : List.of(),
                List.of("/api/jobs/job-1/demo-run-package/download"),
                List.of("Variance report is read-only.")
        );
    }

    private DemoAcceptanceGateVo gate(String status) {
        return new DemoAcceptanceGateVo(
                "job-1",
                "video-1",
                Instant.parse("2026-06-29T12:01:00Z"),
                status,
                LocalizationJobStatus.COMPLETED,
                "zh-CN",
                "tears-showcase",
                "Ready to present",
                "Acceptance gate summary.",
                "Present this run.",
                List.of(),
                List.of(new DemoAcceptanceGateCheckVo("JOB_COMPLETED", "Job completed", "PASS", "Completed.", true)),
                List.of(new DemoAcceptanceGateEvidenceVo("QUALITY", "Quality", "91", "PASS")),
                List.of(new DemoAcceptanceGateLinkVo("ACCEPTANCE_GATE_JSON", "Acceptance gate", "/api/jobs/job-1/demo-acceptance-gate")),
                List.of("Acceptance gate is metadata-only.")
        );
    }

    private DemoCompletionCertificateVo certificate(String status) {
        return new DemoCompletionCertificateVo(
                "job-1",
                "video-1",
                Instant.parse("2026-06-29T12:02:00Z"),
                status,
                LocalizationJobStatus.COMPLETED,
                "zh-CN",
                "tears-showcase",
                "Completion certificate",
                "Completion summary.",
                "Use certificate evidence.",
                "job-baseline",
                "job-1",
                "job-baseline",
                List.of(new DemoCompletionCertificateCheckVo("HANDOFF_READY", "Handoff ready", "PASS", "Ready.", false)),
                List.of(new DemoCompletionCertificateSectionVo("PROOF", "Proof", "PASS", List.of("Job completed."))),
                List.of(new DemoCompletionCertificateLinkVo("CERTIFICATE_JSON", "Certificate", "/api/jobs/job-1/demo-completion-certificate")),
                List.of("Certificate is metadata-only.")
        );
    }
}
