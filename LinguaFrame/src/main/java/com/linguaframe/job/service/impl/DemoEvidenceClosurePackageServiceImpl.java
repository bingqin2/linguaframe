package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.bo.StoredDemoEvidenceClosurePackageBo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateLinkVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateVo;
import com.linguaframe.job.domain.vo.DemoCompletionCertificateLinkVo;
import com.linguaframe.job.domain.vo.DemoCompletionCertificateVo;
import com.linguaframe.job.domain.vo.DemoEvidenceClosurePackageVo;
import com.linguaframe.job.domain.vo.DemoEvidenceClosureSectionVo;
import com.linguaframe.job.domain.vo.DemoRunVarianceReportVo;
import com.linguaframe.job.service.DemoAcceptanceGateService;
import com.linguaframe.job.service.DemoCompletionCertificateService;
import com.linguaframe.job.service.DemoEvidenceClosurePackageService;
import com.linguaframe.job.service.DemoRunVarianceReportService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DemoEvidenceClosurePackageServiceImpl implements DemoEvidenceClosurePackageService {

    private final DemoRunVarianceReportService demoRunVarianceReportService;
    private final DemoAcceptanceGateService demoAcceptanceGateService;
    private final DemoCompletionCertificateService demoCompletionCertificateService;
    private final ObjectMapper objectMapper;

    public DemoEvidenceClosurePackageServiceImpl(
            DemoRunVarianceReportService demoRunVarianceReportService,
            DemoAcceptanceGateService demoAcceptanceGateService,
            DemoCompletionCertificateService demoCompletionCertificateService,
            ObjectMapper objectMapper
    ) {
        this.demoRunVarianceReportService = demoRunVarianceReportService;
        this.demoAcceptanceGateService = demoAcceptanceGateService;
        this.demoCompletionCertificateService = demoCompletionCertificateService;
        this.objectMapper = objectMapper;
    }

    @Override
    public DemoEvidenceClosurePackageVo buildClosure(String jobId, String preUploadJson) {
        DemoRunVarianceReportVo variance = demoRunVarianceReportService.build(jobId, preUploadJson);
        DemoAcceptanceGateVo acceptanceGate = demoAcceptanceGateService.buildGate(jobId);
        DemoCompletionCertificateVo certificate = demoCompletionCertificateService.buildCertificate(jobId);

        List<DemoEvidenceClosureSectionVo> sections = List.of(
                preUploadBaselineSection(variance),
                postRunVarianceSection(variance),
                acceptanceSection(acceptanceGate),
                completionSection(certificate),
                deliveryPackageSection(variance, acceptanceGate, certificate),
                reviewerHandoffSection(variance, acceptanceGate, certificate)
        );
        String closureStatus = closureStatus(variance, acceptanceGate, certificate);
        List<String> safeLinks = safeLinks(variance, acceptanceGate, certificate);

        return new DemoEvidenceClosurePackageVo(
                variance.jobId(),
                variance.videoId(),
                Instant.now(),
                closureStatus,
                variance.baselineMode(),
                variance.jobStatus(),
                variance.targetLanguage(),
                variance.demoProfileId(),
                recommendedNextAction(closureStatus, variance, acceptanceGate, certificate),
                variance,
                sections,
                safeLinks,
                List.of(
                        "Closure package is generated on demand from safe evidence services and does not create artifacts or call providers.",
                        "Package excludes media bytes, object keys, local filesystem paths, raw transcript or subtitle text, provider payloads, tokens, and credentials.",
                        "Use the embedded variance report for estimate comparison and the acceptance gate for final go/no-go evidence."
                )
        );
    }

    @Override
    public String renderMarkdown(DemoEvidenceClosurePackageVo closure) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Demo Evidence Closure Package\n\n");
        markdown.append("## Summary\n\n");
        markdown.append("- Job: `").append(clean(closure.jobId())).append("`\n");
        markdown.append("- Video: `").append(clean(closure.videoId())).append("`\n");
        markdown.append("- Closure status: `").append(clean(closure.closureStatus())).append("`\n");
        markdown.append("- Baseline mode: `").append(clean(closure.baselineMode())).append("`\n");
        markdown.append("- Job status: `").append(clean(closure.jobStatus())).append("`\n");
        markdown.append("- Recommended next action: ").append(clean(closure.recommendedNextAction())).append("\n\n");

        appendSection(markdown, "Baseline", closure, "PRE_UPLOAD_BASELINE");
        appendSection(markdown, "Post-Run Variance", closure, "POST_RUN_VARIANCE");
        appendSection(markdown, "Acceptance", closure, "ACCEPTANCE_GATE");
        appendSection(markdown, "Completion", closure, "COMPLETION_CERTIFICATE");

        markdown.append("## Safe Links\n\n");
        for (String link : closure.safeLinks()) {
            markdown.append("- `").append(clean(link)).append("`\n");
        }
        markdown.append("\n");

        markdown.append("## Safety Notes\n\n");
        for (String note : closure.safetyNotes()) {
            markdown.append("- ").append(clean(note)).append("\n");
        }
        return markdown.toString();
    }

    @Override
    public StoredDemoEvidenceClosurePackageBo openClosurePackage(String jobId, String preUploadJson) {
        DemoEvidenceClosurePackageVo closure = buildClosure(jobId, preUploadJson);
        String closureMarkdown = renderMarkdown(closure);
        String varianceMarkdown = demoRunVarianceReportService.renderMarkdown(closure.varianceReport());
        byte[] bytes = zipBytes(closure, closureMarkdown, varianceMarkdown);
        return new StoredDemoEvidenceClosurePackageBo(
                "linguaframe-job-" + clean(closure.jobId()) + "-demo-evidence-closure.zip",
                "application/zip",
                bytes.length,
                new ByteArrayInputStream(bytes)
        );
    }

    private DemoEvidenceClosureSectionVo preUploadBaselineSection(DemoRunVarianceReportVo variance) {
        return new DemoEvidenceClosureSectionVo(
                "PRE_UPLOAD_BASELINE",
                "Pre-upload baseline",
                variance.baselineMode().equals("MISSING") ? "ATTENTION" : variance.baselineMode().equals("INVALID") ? "ATTENTION" : "READY",
                "Baseline mode is " + variance.baselineMode() + ".",
                variance.notes().isEmpty() ? List.of("No baseline notes were reported.") : variance.notes(),
                List.of()
        );
    }

    private DemoEvidenceClosureSectionVo postRunVarianceSection(DemoRunVarianceReportVo variance) {
        return new DemoEvidenceClosureSectionVo(
                "POST_RUN_VARIANCE",
                "Post-run variance",
                variance.overallStatus(),
                variance.recommendedNextAction(),
                variance.metrics().stream()
                        .map(metric -> metric.label() + ": " + metric.status()
                                + " (estimated " + metric.estimatedValue()
                                + ", actual " + metric.actualValue() + ")")
                        .toList(),
                variance.safeLinks()
        );
    }

    private DemoEvidenceClosureSectionVo acceptanceSection(DemoAcceptanceGateVo gate) {
        return new DemoEvidenceClosureSectionVo(
                "ACCEPTANCE_GATE",
                "Acceptance gate",
                gate.gateStatus(),
                gate.recommendedNextAction(),
                List.of(
                        "Gate status: " + gate.gateStatus(),
                        "Required checks: " + gate.checks().stream().filter(check -> check.required()).count(),
                        "Evidence rows: " + gate.evidence().size()
                ),
                gate.links().stream().map(DemoAcceptanceGateLinkVo::url).toList()
        );
    }

    private DemoEvidenceClosureSectionVo completionSection(DemoCompletionCertificateVo certificate) {
        return new DemoEvidenceClosureSectionVo(
                "COMPLETION_CERTIFICATE",
                "Completion certificate",
                certificate.certificateStatus(),
                certificate.recommendedNextAction(),
                List.of(
                        "Certificate status: " + certificate.certificateStatus(),
                        "Recommended baseline job: " + valueOrNone(certificate.recommendedBaselineJobId()),
                        "Proof sections: " + certificate.sections().size()
                ),
                certificate.links().stream().map(DemoCompletionCertificateLinkVo::url).toList()
        );
    }

    private DemoEvidenceClosureSectionVo deliveryPackageSection(
            DemoRunVarianceReportVo variance,
            DemoAcceptanceGateVo gate,
            DemoCompletionCertificateVo certificate
    ) {
        return new DemoEvidenceClosureSectionVo(
                "DELIVERY_PACKAGE",
                "Delivery package",
                "READY".equals(gate.gateStatus()) && "READY".equals(certificate.certificateStatus()) ? "READY" : "ATTENTION",
                "Use safe package links for reviewer delivery evidence.",
                List.of(
                        "Variance safe links: " + variance.safeLinks().size(),
                        "Acceptance links: " + gate.links().size(),
                        "Certificate links: " + certificate.links().size()
                ),
                safeLinks(variance, gate, certificate)
        );
    }

    private DemoEvidenceClosureSectionVo reviewerHandoffSection(
            DemoRunVarianceReportVo variance,
            DemoAcceptanceGateVo gate,
            DemoCompletionCertificateVo certificate
    ) {
        String status = closureStatus(variance, gate, certificate);
        return new DemoEvidenceClosureSectionVo(
                "REVIEWER_HANDOFF",
                "Reviewer handoff",
                status,
                recommendedNextAction(status, variance, gate, certificate),
                List.of(
                        "Target language: " + valueOrNone(variance.targetLanguage()),
                        "Demo profile: " + valueOrNone(variance.demoProfileId()),
                        "Acceptance: " + gate.gateStatus(),
                        "Completion: " + certificate.certificateStatus()
                ),
                safeLinks(variance, gate, certificate)
        );
    }

    private String closureStatus(
            DemoRunVarianceReportVo variance,
            DemoAcceptanceGateVo gate,
            DemoCompletionCertificateVo certificate
    ) {
        if ("BLOCKED".equals(variance.overallStatus())
                || "BLOCKED".equals(gate.gateStatus())
                || "BLOCKED".equals(certificate.certificateStatus())) {
            return "BLOCKED";
        }
        if ("READY".equals(variance.overallStatus())
                && "READY".equals(gate.gateStatus())
                && "READY".equals(certificate.certificateStatus())) {
            return "READY";
        }
        return "ATTENTION";
    }

    private String recommendedNextAction(
            String closureStatus,
            DemoRunVarianceReportVo variance,
            DemoAcceptanceGateVo gate,
            DemoCompletionCertificateVo certificate
    ) {
        if ("READY".equals(closureStatus)) {
            return "Share the closure package with the demo run package and acceptance evidence.";
        }
        if ("BLOCKED".equals(closureStatus)) {
            return "Resolve blocking acceptance or completion evidence before presenting this run.";
        }
        if ("INVALID".equals(variance.baselineMode())) {
            return "Fix the pre-upload baseline JSON or use actual-only closure evidence.";
        }
        if (!"READY".equals(gate.gateStatus())) {
            return gate.recommendedNextAction();
        }
        return certificate.recommendedNextAction();
    }

    private List<String> safeLinks(
            DemoRunVarianceReportVo variance,
            DemoAcceptanceGateVo gate,
            DemoCompletionCertificateVo certificate
    ) {
        Set<String> links = new LinkedHashSet<>();
        links.addAll(variance.safeLinks());
        gate.links().forEach(link -> links.add(link.url()));
        certificate.links().forEach(link -> links.add(link.url()));
        links.add("/api/jobs/" + clean(variance.jobId()) + "/demo-evidence-closure");
        links.add("/api/jobs/" + clean(variance.jobId()) + "/demo-evidence-closure/download");
        return List.copyOf(links);
    }

    private void appendSection(
            StringBuilder markdown,
            String heading,
            DemoEvidenceClosurePackageVo closure,
            String key
    ) {
        markdown.append("## ").append(heading).append("\n\n");
        closure.sections().stream()
                .filter(section -> key.equals(section.key()))
                .findFirst()
                .ifPresent(section -> {
                    markdown.append("- Status: `").append(clean(section.status())).append("`\n");
                    markdown.append("- Summary: ").append(clean(section.summary())).append("\n");
                    for (String fact : section.facts()) {
                        markdown.append("- ").append(clean(fact)).append("\n");
                    }
                    markdown.append("\n");
                });
    }

    private byte[] zipBytes(
            DemoEvidenceClosurePackageVo closure,
            String closureMarkdown,
            String varianceMarkdown
    ) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
                writeEntry(zipOutputStream, "manifest.json", objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(closure));
                writeEntry(zipOutputStream, "demo-evidence-closure.md", closureMarkdown);
                writeEntry(zipOutputStream, "demo-run-variance.md", varianceMarkdown);
                writeEntry(zipOutputStream, "README.md", closureReadme(closure));
            }
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build demo evidence closure package", ex);
        }
    }

    private void writeEntry(ZipOutputStream zipOutputStream, String name, String content) throws java.io.IOException {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }

    private String closureReadme(DemoEvidenceClosurePackageVo closure) {
        return "# Demo Evidence Closure Package\n\n"
                + "- Job: `" + clean(closure.jobId()) + "`\n"
                + "- Status: `" + clean(closure.closureStatus()) + "`\n"
                + "- Baseline mode: `" + clean(closure.baselineMode()) + "`\n\n"
                + "This package contains safe metadata only. It does not include media bytes, raw transcript text, subtitle text, object keys, local paths, provider payloads, tokens, or credentials.\n";
    }

    private String valueOrNone(String value) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? "none" : cleaned;
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\r', ' ').replace('\n', ' ').replace('|', '/').trim();
    }
}
