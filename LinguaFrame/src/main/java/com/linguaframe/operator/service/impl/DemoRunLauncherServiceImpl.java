package com.linguaframe.operator.service.impl;

import com.linguaframe.media.domain.vo.DemoUploadReadinessCheckVo;
import com.linguaframe.media.domain.vo.DemoUploadReadinessVo;
import com.linguaframe.media.service.DemoUploadReadinessService;
import com.linguaframe.operator.domain.vo.DemoRunLauncherCommandVo;
import com.linguaframe.operator.domain.vo.DemoRunLauncherEvidenceVo;
import com.linguaframe.operator.domain.vo.DemoRunLauncherGateVo;
import com.linguaframe.operator.domain.vo.DemoRunLauncherVo;
import com.linguaframe.operator.domain.vo.DemoSampleMediaCatalogVo;
import com.linguaframe.operator.domain.vo.DemoSampleMediaConfiguredPathVo;
import com.linguaframe.operator.service.DemoRunLauncherService;
import com.linguaframe.operator.service.DemoSampleMediaCatalogService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DemoRunLauncherServiceImpl implements DemoRunLauncherService {

    private static final String READY = "READY";
    private static final String ATTENTION = "ATTENTION";
    private static final String BLOCKED = "BLOCKED";
    private static final String CONFIGURED = "CONFIGURED";
    private static final String RECOMMENDED_SAMPLE_ID = "tears-of-steel-casting";
    private static final String RECOMMENDED_PROFILE_ID = "tears-showcase";
    private static final String FULL_TEARS_COMMAND = "scripts/demo/docker-e2e-tears-of-steel-full.sh";
    private static final String UPLOAD_READINESS_COMMAND = "scripts/demo/upload-readiness.sh";

    private final DemoSampleMediaCatalogService sampleMediaCatalogService;
    private final DemoUploadReadinessService uploadReadinessService;

    public DemoRunLauncherServiceImpl(
            DemoSampleMediaCatalogService sampleMediaCatalogService,
            DemoUploadReadinessService uploadReadinessService
    ) {
        this.sampleMediaCatalogService = sampleMediaCatalogService;
        this.uploadReadinessService = uploadReadinessService;
    }

    @Override
    public DemoRunLauncherVo launcher() {
        DemoSampleMediaCatalogVo catalog = sampleMediaCatalogService.catalog();
        DemoUploadReadinessVo readiness = uploadReadinessService.getReadiness(RECOMMENDED_PROFILE_ID);
        List<DemoRunLauncherGateVo> gates = gates(catalog, readiness);
        String overallStatus = overallStatus(gates);
        String recommendedNextCommand = BLOCKED.equals(overallStatus) ? UPLOAD_READINESS_COMMAND : FULL_TEARS_COMMAND;
        return new DemoRunLauncherVo(
                Instant.now(),
                overallStatus,
                catalog.recommendedSampleId(),
                RECOMMENDED_PROFILE_ID,
                recommendedNextCommand,
                gates,
                commands(),
                expectedEvidence(),
                notes(overallStatus, recommendedNextCommand, gates)
        );
    }

    private List<DemoRunLauncherGateVo> gates(
            DemoSampleMediaCatalogVo catalog,
            DemoUploadReadinessVo readiness
    ) {
        List<DemoRunLauncherGateVo> gates = new ArrayList<>();
        gates.add(sampleGate(catalog));
        gates.add(uploadReadinessGate(readiness));
        for (DemoUploadReadinessCheckVo check : readiness.checks()) {
            gates.add(new DemoRunLauncherGateVo(
                    safe(check.id()),
                    safe(check.label()),
                    safeStatus(check.status()),
                    safe(check.detail()),
                    safe(check.nextAction()),
                    check.blocking()
            ));
        }
        return List.copyOf(gates);
    }

    private DemoRunLauncherGateVo sampleGate(DemoSampleMediaCatalogVo catalog) {
        Optional<DemoSampleMediaConfiguredPathVo> tearsPath = catalog.configuredPaths().stream()
                .filter(path -> "LINGUAFRAME_TEARS_SAMPLE_PATH".equals(path.envVar()))
                .findFirst();
        boolean configured = tearsPath
                .map(path -> CONFIGURED.equals(path.status()))
                .orElse(false);
        String filename = tearsPath.map(DemoSampleMediaConfiguredPathVo::filename)
                .filter(value -> !value.isBlank())
                .orElse("not configured");
        return new DemoRunLauncherGateVo(
                "sample-media",
                "Sample media",
                configured ? READY : ATTENTION,
                configured
                        ? "Recommended Tears sample is configured as " + safe(filename) + "."
                        : "Recommended Tears sample is not configured for the full demo.",
                configured
                        ? "No sample-media action required."
                        : "Set LINGUAFRAME_TEARS_SAMPLE_PATH to the complete local Tears sample before a full run.",
                false
        );
    }

    private DemoRunLauncherGateVo uploadReadinessGate(DemoUploadReadinessVo readiness) {
        boolean blocking = BLOCKED.equals(readiness.overallStatus());
        return new DemoRunLauncherGateVo(
                "upload-readiness",
                "Upload readiness",
                safeStatus(readiness.overallStatus()),
                "Upload readiness for profile " + safe(readiness.demoProfileId()) + " is " + safeStatus(readiness.overallStatus()) + ".",
                blocking
                        ? "Run scripts/demo/upload-readiness.sh and resolve blocking checks before uploading."
                        : "Validate the selected media file before upload.",
                blocking
        );
    }

    private String overallStatus(List<DemoRunLauncherGateVo> gates) {
        if (gates.stream().anyMatch(DemoRunLauncherGateVo::blocking)) {
            return BLOCKED;
        }
        if (gates.stream().anyMatch(gate -> ATTENTION.equals(gate.status()))) {
            return ATTENTION;
        }
        return READY;
    }

    private List<DemoRunLauncherCommandVo> commands() {
        return List.of(
                command(
                        "Inspect launcher",
                        "scripts/demo/demo-run-launcher.sh",
                        "Download this read-only launcher contract and print the next command."
                ),
                command(
                        "Check OpenAI preflight",
                        "scripts/demo/openai-demo-preflight.sh",
                        "Verify provider-backed demo configuration before spending credits."
                ),
                command(
                        "Run upload readiness",
                        UPLOAD_READINESS_COMMAND,
                        "Check upload gates for the recommended Tears showcase profile."
                ),
                command(
                        "Run full Tears demo",
                        FULL_TEARS_COMMAND,
                        "Process the configured complete Tears sample and export demo evidence."
                ),
                command(
                        "Run full Tears profile explicitly",
                        "LINGUAFRAME_DEMO_PROFILE_ID=tears-showcase scripts/demo/docker-e2e-tears-of-steel-full.sh",
                        "Run the full sample with the recommended showcase profile."
                )
        );
    }

    private DemoRunLauncherCommandVo command(String label, String command, String description) {
        return new DemoRunLauncherCommandVo(label, command, description);
    }

    private List<DemoRunLauncherEvidenceVo> expectedEvidence() {
        return List.of(
                evidence("Job detail JSON", "/tmp/linguaframe-demo/full-tears/job-detail.json",
                        "Terminal job-detail snapshot after the full Tears run reaches a terminal state."),
                evidence("Demo presenter pack", "/tmp/linguaframe-demo/full-tears/demo-presenter-pack.json",
                        "Presenter-facing metadata and safe package links for the completed run."),
                evidence("Demo run matrix", "/tmp/linguaframe-demo/full-tears/demo-run-matrix.json",
                        "Same-source run comparison evidence for baseline, quality, cost, and cache checks."),
                evidence("Demo run snapshot ZIP", "/tmp/linguaframe-demo/full-tears/demo-run-snapshot.zip",
                        "Safe reviewer package combining job metadata, evidence, and handoff files."),
                evidence("Browser evidence", "/api/jobs/{jobId}/demo-run-snapshot/download",
                        "Backend route for downloading the same snapshot package from the selected job.")
        );
    }

    private DemoRunLauncherEvidenceVo evidence(String label, String path, String description) {
        return new DemoRunLauncherEvidenceVo(label, path, description);
    }

    private String notes(String overallStatus, String recommendedNextCommand, List<DemoRunLauncherGateVo> gates) {
        StringBuilder builder = new StringBuilder();
        builder.append("# LinguaFrame Demo Run Launcher\n\n");
        builder.append("- Overall: ").append(overallStatus).append('\n');
        builder.append("- Recommended sample: ").append(RECOMMENDED_SAMPLE_ID).append('\n');
        builder.append("- Recommended profile: ").append(RECOMMENDED_PROFILE_ID).append('\n');
        builder.append("- Recommended next command: ").append(recommendedNextCommand).append('\n');
        builder.append("- Safety boundary: this launcher is read-only; it does not upload media, start Docker, call OpenAI, mutate env files, or expose full local paths.\n\n");
        builder.append("## Gates\n\n");
        for (DemoRunLauncherGateVo gate : gates) {
            builder.append("- ")
                    .append(gate.status())
                    .append(": ")
                    .append(gate.label())
                    .append(" - ")
                    .append(gate.nextAction())
                    .append('\n');
        }
        return builder.toString();
    }

    private String safeStatus(String value) {
        if (BLOCKED.equals(value) || ATTENTION.equals(value) || READY.equals(value)) {
            return value;
        }
        return ATTENTION;
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
                .replaceAll("/Users/[^\\s,;)]*", "[local-path]")
                .replaceAll("sk-[A-Za-z0-9_-]+", "[redacted]")
                .replace("OPENAI_API_KEY", "[redacted]")
                .replace("private-demo-token", "[redacted]")
                .replace("provider payload", "[redacted]")
                .replace("raw transcript text", "[redacted]")
                .replace("raw subtitle text", "[redacted]");
    }
}
