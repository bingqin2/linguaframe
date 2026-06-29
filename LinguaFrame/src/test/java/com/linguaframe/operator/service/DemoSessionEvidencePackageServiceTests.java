package com.linguaframe.operator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.operator.domain.bo.DemoSessionEvidencePackageBo;
import com.linguaframe.operator.domain.vo.DemoPresentationCockpitCheckVo;
import com.linguaframe.operator.domain.vo.DemoPresentationCockpitLinkVo;
import com.linguaframe.operator.domain.vo.DemoPresentationCockpitRunVo;
import com.linguaframe.operator.domain.vo.DemoPresentationCockpitVo;
import com.linguaframe.operator.domain.vo.DemoSessionCommandCenterActionVo;
import com.linguaframe.operator.domain.vo.DemoSessionCommandCenterEvidenceVo;
import com.linguaframe.operator.domain.vo.DemoSessionCommandCenterPhaseVo;
import com.linguaframe.operator.domain.vo.DemoSessionCommandCenterRunVo;
import com.linguaframe.operator.domain.vo.DemoSessionCommandCenterVo;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerSummaryVo;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerVo;
import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryDownloadVo;
import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryJobVo;
import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryVo;
import com.linguaframe.operator.domain.vo.PrivateDemoLaunchRehearsalStepVo;
import com.linguaframe.operator.domain.vo.PrivateDemoLaunchRehearsalVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsCheckVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsCommandVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsSectionVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveCandidateVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveLinkVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveVo;
import com.linguaframe.operator.service.impl.DemoSessionEvidencePackageServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class DemoSessionEvidencePackageServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final StubDemoSessionCommandCenterService commandCenterService = new StubDemoSessionCommandCenterService();
    private final StubPrivateDemoOperationsService operationsService = new StubPrivateDemoOperationsService();
    private final StubPrivateDemoLaunchRehearsalService launchService = new StubPrivateDemoLaunchRehearsalService();
    private final StubModelUsageLedgerService ledgerService = new StubModelUsageLedgerService();
    private final StubDemoPresentationCockpitService cockpitService = new StubDemoPresentationCockpitService();
    private final StubPrivateDemoEvidenceGalleryService galleryService = new StubPrivateDemoEvidenceGalleryService();
    private final StubPrivateDemoRunArchiveService archiveService = new StubPrivateDemoRunArchiveService();

    private final DemoSessionEvidencePackageService service = new DemoSessionEvidencePackageServiceImpl(
            objectMapper,
            commandCenterService,
            operationsService,
            launchService,
            ledgerService,
            cockpitService,
            galleryService,
            archiveService
    );

    @Test
    void opensSessionEvidencePackageWithRequiredEntries() throws Exception {
        DemoSessionEvidencePackageBo result = service.openPackage(null);
        Map<String, String> entries = unzip(result);

        assertThat(result.filename()).isEqualTo("linguaframe-demo-session-evidence-package.zip");
        assertThat(result.contentType()).isEqualTo("application/zip");
        assertThat(result.sizeBytes()).isGreaterThan(0);
        assertThat(entries.keySet()).containsExactly(
                "manifest.json",
                "README.md",
                "command-center.json",
                "command-center.md",
                "operations.json",
                "operations.md",
                "launch-rehearsal.json",
                "launch-rehearsal.md",
                "model-usage-ledger.json",
                "model-usage-ledger.md",
                "presentation-cockpit.json",
                "presentation-cockpit.md",
                "evidence-gallery.json",
                "evidence-gallery.md",
                "run-archive.json",
                "run-archive.md"
        );

        JsonNode manifest = objectMapper.readTree(entries.get("manifest.json"));
        assertThat(manifest.path("packageType").asText()).isEqualTo("DEMO_SESSION_EVIDENCE_PACKAGE");
        assertThat(manifest.path("overallStatus").asText()).isEqualTo("READY");
        assertThat(manifest.path("phase").asText()).isEqualTo("READY_TO_PRESENT");
        assertThat(manifest.path("entryCount").asInt()).isEqualTo(16);
        assertThat(entries.get("README.md")).contains("LinguaFrame Demo Session Evidence Package");
        assertThat(entries.get("command-center.md")).contains("Command center Markdown");
        assertThat(entries.get("operations.md")).contains("Private Demo Operations");
        assertThat(entries.get("model-usage-ledger.md")).contains("Model Usage Ledger");
    }

    @Test
    void focusesSelectedJobAndSanitizesFilename() throws Exception {
        DemoSessionEvidencePackageBo result = service.openPackage(" job with/slash ");
        Map<String, String> entries = unzip(result);

        assertThat(result.filename()).isEqualTo("linguaframe-demo-session-job-with-slash-evidence-package.zip");
        assertThat(commandCenterService.lastJobId).isEqualTo("job with/slash");
        assertThat(cockpitService.lastJobId).isEqualTo("job with/slash");

        JsonNode manifest = objectMapper.readTree(entries.get("manifest.json"));
        assertThat(manifest.path("focusedJobId").asText()).isEqualTo("job with/slash");
        assertThat(entries.get("command-center.json")).contains("job with/slash");
    }

    @Test
    void packageDoesNotExposeUnsafeMarkers() throws Exception {
        DemoSessionEvidencePackageBo result = service.openPackage("job-safe");
        String combined = String.join("\n", unzip(result).values());

        assertThat(combined)
                .doesNotContain("source-videos/")
                .doesNotContain("job-artifacts/")
                .doesNotContain("/Users/")
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("private-demo-token")
                .doesNotContain("Bearer ")
                .doesNotContain("provider request payload")
                .doesNotContain("provider response payload")
                .doesNotContain("raw transcript text:")
                .doesNotContain("raw subtitle text:")
                .doesNotContain("corrected subtitle text:");
    }

    private Map<String, String> unzip(DemoSessionEvidencePackageBo result) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        byte[] bytes = result.inputStream().readAllBytes();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zipInputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    private static DemoSessionCommandCenterVo commandCenter(String jobId) {
        DemoSessionCommandCenterRunVo focus = new DemoSessionCommandCenterRunVo(
                "SELECTED",
                jobId == null ? "job-session" : jobId,
                "video-session",
                "tears-showcase",
                "COMPLETED",
                "READY",
                "READY",
                "COMPLETED",
                42000L,
                "Use this run for the demo."
        );
        return new DemoSessionCommandCenterVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                "READY",
                "READY_TO_PRESENT",
                "Open the presenter pack.",
                "LINGUAFRAME_DEMO_JOB_ID=" + focus.jobId() + " scripts/demo/demo-session-command-center.sh",
                focus,
                null,
                focus,
                List.of(new DemoSessionCommandCenterPhaseVo("model-usage", "Model usage ledger", "READY", "Calls 2.", "Use ledger evidence.", false)),
                List.of(new DemoSessionCommandCenterActionVo("session", "Export session", "scripts/demo/demo-session-command-center.sh", "Export metadata.", true)),
                List.of(new DemoSessionCommandCenterEvidenceVo("Command center", "/api/operator/demo-session-command-center", "application/json", "Command center JSON.")),
                new BigDecimal("0.00020000"),
                2,
                0,
                BigDecimal.ZERO,
                100L,
                1,
                List.of("Metadata-only command center.")
        );
    }

    private static PrivateDemoOperationsVo operations() {
        return new PrivateDemoOperationsVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                "READY",
                1,
                0,
                0,
                List.of(new PrivateDemoOperationsSectionVo(
                        "Access gate",
                        "READY",
                        List.of(new PrivateDemoOperationsCheckVo("Owner access", "READY", "Token gate reachable.", "Continue."))
                )),
                List.of(new PrivateDemoOperationsCommandVo("Preflight", "scripts/demo/private-demo-preflight.sh", "Run preflight.")),
                List.of()
        );
    }

    private static PrivateDemoLaunchRehearsalVo launch() {
        return new PrivateDemoLaunchRehearsalVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                "READY",
                1,
                0,
                0,
                "full-demo",
                List.of(new PrivateDemoLaunchRehearsalStepVo(
                        "full-demo",
                        "Run full demo",
                        "READY",
                        "Run the configured public sample.",
                        "scripts/demo/docker-e2e-tears-of-steel-full.sh",
                        "/tmp/linguaframe-demo/full-tears",
                        "Export session package.",
                        false
                )),
                List.of("/api/operator/demo-session-evidence-package/download"),
                ""
        );
    }

    private static ModelUsageLedgerVo ledger() {
        return new ModelUsageLedgerVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                20,
                "demo-owner",
                "CONFIGURED_DEMO_OWNER",
                new ModelUsageLedgerSummaryVo(
                        "READY",
                        1,
                        2,
                        0,
                        1,
                        2,
                        200,
                        new BigDecimal("0.00020000"),
                        100,
                        BigDecimal.ZERO,
                        "Use ledger evidence."
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of("/api/operator/model-usage-ledger"),
                List.of("Metadata only.")
        );
    }

    private static DemoPresentationCockpitVo cockpit(String jobId) {
        return new DemoPresentationCockpitVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                "READY",
                "READY_TO_PRESENT",
                "Open presenter evidence.",
                new DemoPresentationCockpitRunVo(
                        jobId == null ? "job-session" : jobId,
                        "video-session",
                        "tears-showcase",
                        "COMPLETED",
                        "READY",
                        "READY",
                        "NONE",
                        "COMPLETED",
                        42000L,
                        "Present."
                ),
                null,
                null,
                List.of(new DemoPresentationCockpitCheckVo("ACCEPTANCE_GATE", "Acceptance gate", "READY", "Ready.", "Present.", false)),
                List.of(new DemoPresentationCockpitLinkVo("DEMO_RUN_PACKAGE", "Demo run package", "/api/jobs/job-session/demo-run-package/download")),
                List.of("Metadata-only cockpit.")
        );
    }

    private static PrivateDemoEvidenceGalleryVo gallery() {
        return new PrivateDemoEvidenceGalleryVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                "READY",
                1,
                1,
                "job-session",
                List.of(new PrivateDemoEvidenceGalleryJobVo(
                        "job-session",
                        "video-session",
                        "session.mp4",
                        "zh-CN",
                        "tears-showcase",
                        com.linguaframe.job.domain.enums.LocalizationJobStatus.COMPLETED,
                        Instant.parse("2026-06-29T07:00:00Z"),
                        Instant.parse("2026-06-29T08:00:00Z"),
                        94,
                        "PASS",
                        new BigDecimal("0.00020000"),
                        2,
                        1,
                        true,
                        true,
                        true,
                        List.of(),
                        List.of(new PrivateDemoEvidenceGalleryDownloadVo("Demo run package", "/api/jobs/job-session/demo-run-package/download", "application/zip", "Safe package."))
                )),
                List.of(),
                ""
        );
    }

    private static PrivateDemoRunArchiveVo archive() {
        return new PrivateDemoRunArchiveVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                "READY",
                "job-session",
                "video-session",
                "tears-showcase",
                "READY",
                "READY",
                "READY",
                "full-demo",
                1,
                1,
                List.of(new PrivateDemoRunArchiveCandidateVo(
                        "job-session",
                        "video-session",
                        "session.mp4",
                        "tears-showcase",
                        "COMPLETED",
                        "READY",
                        94,
                        new BigDecimal("0.00020000"),
                        2,
                        1,
                        true,
                        List.of("RECOMMENDED")
                )),
                List.of(new PrivateDemoRunArchiveLinkVo("Demo run package", "/api/jobs/job-session/demo-run-package/download", "application/zip", "Safe package.")),
                ""
        );
    }

    private final class StubDemoSessionCommandCenterService implements DemoSessionCommandCenterService {
        private String lastJobId;

        @Override
        public DemoSessionCommandCenterVo commandCenter(String jobId) {
            lastJobId = jobId;
            return DemoSessionEvidencePackageServiceTests.commandCenter(jobId);
        }

        @Override
        public String commandCenterMarkdown(String jobId) {
            return "# Command center Markdown\n\n- Job: " + (jobId == null ? "job-session" : jobId) + "\n";
        }
    }

    private static final class StubPrivateDemoOperationsService implements PrivateDemoOperationsService {
        @Override
        public PrivateDemoOperationsVo operations() {
            return DemoSessionEvidencePackageServiceTests.operations();
        }
    }

    private static final class StubPrivateDemoLaunchRehearsalService implements PrivateDemoLaunchRehearsalService {
        @Override
        public PrivateDemoLaunchRehearsalVo launchRehearsal() {
            return DemoSessionEvidencePackageServiceTests.launch();
        }
    }

    private static final class StubModelUsageLedgerService implements ModelUsageLedgerService {
        @Override
        public ModelUsageLedgerVo ledger(Integer limit) {
            return DemoSessionEvidencePackageServiceTests.ledger();
        }

        @Override
        public String ledgerMarkdown(Integer limit) {
            return "# LinguaFrame Model Usage Ledger\n\n- Calls: 2\n";
        }
    }

    private final class StubDemoPresentationCockpitService implements DemoPresentationCockpitService {
        private String lastJobId;

        @Override
        public DemoPresentationCockpitVo cockpit(String jobId) {
            lastJobId = jobId;
            return DemoSessionEvidencePackageServiceTests.cockpit(jobId);
        }
    }

    private static final class StubPrivateDemoEvidenceGalleryService implements PrivateDemoEvidenceGalleryService {
        @Override
        public PrivateDemoEvidenceGalleryVo evidenceGallery(Integer limit) {
            return DemoSessionEvidencePackageServiceTests.gallery();
        }
    }

    private static final class StubPrivateDemoRunArchiveService implements PrivateDemoRunArchiveService {
        @Override
        public PrivateDemoRunArchiveVo runArchive() {
            return DemoSessionEvidencePackageServiceTests.archive();
        }
    }
}
