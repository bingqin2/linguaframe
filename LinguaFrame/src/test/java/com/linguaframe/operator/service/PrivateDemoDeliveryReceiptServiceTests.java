package com.linguaframe.operator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.operator.domain.bo.DemoSessionEvidencePackageBo;
import com.linguaframe.operator.domain.vo.DemoSessionCommandCenterActionVo;
import com.linguaframe.operator.domain.vo.DemoSessionCommandCenterEvidenceVo;
import com.linguaframe.operator.domain.vo.DemoSessionCommandCenterRunVo;
import com.linguaframe.operator.domain.vo.DemoSessionCommandCenterVo;
import com.linguaframe.operator.domain.vo.DemoSessionRecoveryBoardActionVo;
import com.linguaframe.operator.domain.vo.DemoSessionRecoveryBoardCheckVo;
import com.linguaframe.operator.domain.vo.DemoSessionRecoveryBoardLinkVo;
import com.linguaframe.operator.domain.vo.DemoSessionRecoveryBoardVo;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerSummaryVo;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerVo;
import com.linguaframe.operator.domain.vo.OpenAiReadinessEvidenceVo;
import com.linguaframe.operator.domain.vo.OpenAiReadinessLiveCheckVo;
import com.linguaframe.operator.domain.vo.OpenAiReadinessModelUsageVo;
import com.linguaframe.operator.domain.vo.PrivateDemoDeliveryReceiptVo;
import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryDownloadVo;
import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryJobVo;
import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryVo;
import com.linguaframe.operator.domain.vo.PrivateDemoLaunchRehearsalVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveCandidateVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveLinkVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveVo;
import com.linguaframe.operator.service.impl.PrivateDemoDeliveryReceiptServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PrivateDemoDeliveryReceiptServiceTests {

    private final StubPrivateDemoOperationsService operationsService = new StubPrivateDemoOperationsService();
    private final StubPrivateDemoLaunchRehearsalService launchService = new StubPrivateDemoLaunchRehearsalService();
    private final StubPrivateDemoEvidenceGalleryService galleryService = new StubPrivateDemoEvidenceGalleryService();
    private final StubPrivateDemoRunArchiveService archiveService = new StubPrivateDemoRunArchiveService();
    private final StubDemoSessionCommandCenterService commandCenterService = new StubDemoSessionCommandCenterService();
    private final StubDemoSessionRecoveryBoardService recoveryBoardService = new StubDemoSessionRecoveryBoardService();
    private final StubModelUsageLedgerService ledgerService = new StubModelUsageLedgerService();
    private final StubOpenAiReadinessEvidenceService openAiService = new StubOpenAiReadinessEvidenceService();

    private final PrivateDemoDeliveryReceiptService service = new PrivateDemoDeliveryReceiptServiceImpl(
            new ObjectMapper().findAndRegisterModules(),
            operationsService,
            launchService,
            galleryService,
            archiveService,
            commandCenterService,
            recoveryBoardService,
            ledgerService,
            openAiService
    );

    @Test
    void fallsBackToArchiveRecommendedJobWhenNoJobIsSelected() {
        PrivateDemoDeliveryReceiptVo receipt = service.receipt(null);

        assertThat(commandCenterService.lastJobId).isEqualTo("job-best");
        assertThat(receipt.overallStatus()).isEqualTo("READY");
        assertThat(receipt.selectedJobId()).isNull();
        assertThat(receipt.recommendedJobId()).isEqualTo("job-best");
        assertThat(receipt.evidenceLinks()).extracting("href")
                .contains(
                        "/api/operator/demo-session-evidence-package/download",
                        "/api/jobs/job-best/demo-reviewer-workspace/download",
                        "/api/jobs/job-best/demo-handoff-portal/download",
                        "/api/jobs/job-best/demo-evidence-closure/download",
                        "/api/jobs/job-best/openai-smoke-proof/markdown/download",
                        "/api/jobs/job-best/ai-audit-package/download"
                );
        assertThat(receipt.packageEntries()).extracting("href")
                .contains("/api/operator/private-demo/delivery-receipt/download");
        assertThat(receipt.actions()).extracting("command")
                .contains("LINGUAFRAME_DEMO_JOB_ID=job-best scripts/demo/private-demo-delivery-receipt.sh");
        assertThat(receipt.receiptNotesMarkdown())
                .contains("LinguaFrame Private Demo Delivery Receipt", "Demo reviewer workspace", "OpenAI smoke proof Markdown")
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("raw transcript text");
    }

    @Test
    void marksReceiptAttentionWhenEvidenceIsPartial() {
        galleryService.gallery = gallery("EMPTY", 0, null);
        archiveService.archive = archive("ATTENTION", null);
        commandCenterService.center = commandCenter(null, "ATTENTION");

        PrivateDemoDeliveryReceiptVo receipt = service.receipt(null);

        assertThat(receipt.overallStatus()).isEqualTo("ATTENTION");
        assertThat(receipt.recommendedJobId()).isNull();
        assertThat(receipt.checks())
                .filteredOn(check -> check.id().equals("final-proof-links"))
                .singleElement()
                .satisfies(check -> {
                    assertThat(check.status()).isEqualTo("ATTENTION");
                    assertThat(check.blocking()).isFalse();
                    assertThat(check.nextAction()).contains("Complete or select a demo run");
                });
    }

    @Test
    void blocksReceiptWhenCriticalReadinessIsBlocked() {
        operationsService.operations = operations("BLOCKED");

        PrivateDemoDeliveryReceiptVo receipt = service.receipt("job-best");

        assertThat(receipt.overallStatus()).isEqualTo("BLOCKED");
        assertThat(receipt.checks())
                .filteredOn(check -> check.id().equals("operations"))
                .singleElement()
                .satisfies(check -> {
                    assertThat(check.status()).isEqualTo("BLOCKED");
                    assertThat(check.blocking()).isTrue();
                });
    }

    @Test
    void createsMetadataOnlyZipWithoutNestedBinaryPackages() throws Exception {
        DemoSessionEvidencePackageBo receiptPackage = service.openPackage(" job-best/slash ");

        Map<String, String> entries = zipEntries(receiptPackage.inputStream().readAllBytes());

        assertThat(receiptPackage.filename()).isEqualTo("linguaframe-private-demo-job-best-slash-delivery-receipt.zip");
        assertThat(entries.keySet())
                .containsExactlyInAnyOrder(
                        "manifest.json",
                        "private-demo-delivery-receipt.json",
                        "private-demo-delivery-receipt.md",
                        "run-archive.json",
                        "command-center.json",
                        "README.md"
                );
        assertThat(entries.get("manifest.json"))
                .contains("PRIVATE_DEMO_DELIVERY_RECEIPT", "private-demo-delivery-receipt.md");
        assertThat(String.join("\n", entries.values()))
                .contains("/api/jobs/job-best%2Fslash/ai-audit-package/download")
                .doesNotContain("source-videos/")
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("private-demo-token")
                .doesNotContain("provider request payload")
                .doesNotContain("raw transcript text:");
        assertThat(entries.keySet()).noneMatch(name -> name.endsWith(".zip") && !name.equals("manifest.json"));
    }

    private static PrivateDemoOperationsVo operations(String status) {
        return new PrivateDemoOperationsVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                status,
                "BLOCKED".equals(status) ? 0 : 3,
                "ATTENTION".equals(status) ? 1 : 0,
                "BLOCKED".equals(status) ? 1 : 0,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static PrivateDemoLaunchRehearsalVo launch(String status) {
        return new PrivateDemoLaunchRehearsalVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                status,
                "BLOCKED".equals(status) ? 0 : 4,
                "ATTENTION".equals(status) ? 1 : 0,
                "BLOCKED".equals(status) ? 1 : 0,
                "full-tears-demo",
                List.of(),
                List.of("/api/operator/private-demo/operations"),
                "# Launch"
        );
    }

    private static PrivateDemoEvidenceGalleryVo gallery(String status, int completedCount, String recommendedJobId) {
        return new PrivateDemoEvidenceGalleryVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                status,
                completedCount,
                completedCount,
                recommendedJobId,
                recommendedJobId == null ? List.of() : List.of(new PrivateDemoEvidenceGalleryJobVo(
                        recommendedJobId,
                        "video-" + recommendedJobId,
                        "best.mp4",
                        "zh-CN",
                        "tears-showcase",
                        com.linguaframe.job.domain.enums.LocalizationJobStatus.COMPLETED,
                        Instant.parse("2026-06-29T07:00:00Z"),
                        Instant.parse("2026-06-29T08:00:00Z"),
                        94,
                        "PASS",
                        new BigDecimal("0.10000000"),
                        4,
                        1,
                        true,
                        true,
                        true,
                        List.of(),
                        List.of(new PrivateDemoEvidenceGalleryDownloadVo("Demo run package", "/api/jobs/" + recommendedJobId + "/demo-run-package/download", "application/zip", "Safe package."))
                )),
                List.of(),
                "# Gallery"
        );
    }

    private static PrivateDemoRunArchiveVo archive(String status, String recommendedJobId) {
        return new PrivateDemoRunArchiveVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                status,
                recommendedJobId,
                recommendedJobId == null ? null : "video-" + recommendedJobId,
                recommendedJobId == null ? null : "tears-showcase",
                recommendedJobId == null ? "MISSING" : "READY",
                "READY",
                "READY",
                "full-tears-demo",
                recommendedJobId == null ? 0 : 1,
                recommendedJobId == null ? 0 : 1,
                recommendedJobId == null ? List.of() : List.of(new PrivateDemoRunArchiveCandidateVo(
                        recommendedJobId,
                        "video-" + recommendedJobId,
                        "best.mp4",
                        "tears-showcase",
                        "COMPLETED",
                        "READY",
                        94,
                        new BigDecimal("0.10000000"),
                        4,
                        1,
                        true,
                        List.of("RECOMMENDED")
                )),
                recommendedJobId == null ? List.of() : List.of(new PrivateDemoRunArchiveLinkVo("Demo run package", "/api/jobs/" + recommendedJobId + "/demo-run-package/download", "application/zip", "Safe package.")),
                "# Archive"
        );
    }

    private static DemoSessionCommandCenterVo commandCenter(String jobId, String status) {
        DemoSessionCommandCenterRunVo focus = jobId == null ? null : new DemoSessionCommandCenterRunVo(
                "RECOMMENDED",
                jobId,
                "video-" + jobId,
                "tears-showcase",
                "COMPLETED",
                "READY",
                "READY",
                "NONE",
                42000L,
                "Present."
        );
        return new DemoSessionCommandCenterVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                status,
                focus == null ? "PRE_UPLOAD" : "READY_TO_PRESENT",
                focus == null ? "Complete a run." : "Present job " + focus.jobId() + ".",
                focus == null ? "scripts/demo/docker-e2e-tears-of-steel-full.sh" : "LINGUAFRAME_DEMO_JOB_ID=" + focus.jobId() + " scripts/demo/demo-session-command-center.sh",
                focus,
                null,
                focus,
                List.of(),
                focus == null ? List.of() : List.of(new DemoSessionCommandCenterActionVo("session", "Export session", "LINGUAFRAME_DEMO_JOB_ID=" + focus.jobId() + " scripts/demo/demo-session-command-center.sh", "Export.", true)),
                focus == null ? List.of() : List.of(new DemoSessionCommandCenterEvidenceVo("Command center", "/api/operator/demo-session-command-center", "application/json", "JSON.")),
                "READY",
                0,
                0,
                0,
                1,
                "No recovery action.",
                null,
                List.of(),
                new BigDecimal("0.01000000"),
                4,
                0,
                BigDecimal.ZERO,
                100L,
                1,
                List.of("Metadata only.")
        );
    }

    private static DemoSessionRecoveryBoardVo recoveryBoard(String status) {
        return new DemoSessionRecoveryBoardVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                status,
                "No recoverable jobs.",
                "No recovery action.",
                0,
                0,
                1,
                0,
                0,
                new DemoSessionRecoveryBoardActionVo("NONE", "No action", "/api/operator/demo-session-recovery-board", "No action.", false),
                List.of(),
                List.of(new DemoSessionRecoveryBoardCheckVo("recovery", "Recovery", status, "Ready.", "No action.", false)),
                List.of(new DemoSessionRecoveryBoardLinkVo("JSON", "Recovery board", "/api/operator/demo-session-recovery-board", "application/json", "JSON.")),
                List.of("Metadata only."),
                "# Recovery"
        );
    }

    private static ModelUsageLedgerVo ledger(String status) {
        return new ModelUsageLedgerVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                20,
                "demo-owner",
                "CONFIGURED_DEMO_OWNER",
                new ModelUsageLedgerSummaryVo(
                        status,
                        1,
                        4,
                        0,
                        1,
                        2,
                        400L,
                        new BigDecimal("0.01000000"),
                        100L,
                        BigDecimal.ZERO,
                        "Use ledger evidence."
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of("/api/operator/model-usage-ledger", "/api/operator/model-usage-ledger/markdown/download"),
                List.of("Metadata only.")
        );
    }

    private static OpenAiReadinessEvidenceVo openAi(String status) {
        return new OpenAiReadinessEvidenceVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                status,
                "READY_FOR_DEMO",
                "Use OpenAI readiness evidence.",
                List.of(),
                new OpenAiReadinessLiveCheckVo(status, 100L, "Ready."),
                List.of(),
                new OpenAiReadinessModelUsageVo("READY", 4, 0, BigDecimal.ZERO, new BigDecimal("0.01000000"), "Use ledger evidence."),
                List.of(),
                List.of("/api/operator/openai-readiness-evidence/markdown/download"),
                List.of("Metadata only.")
        );
    }

    private Map<String, String> zipEntries(byte[] bytes) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zipInputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    private static final class StubPrivateDemoOperationsService implements PrivateDemoOperationsService {
        private PrivateDemoOperationsVo operations = PrivateDemoDeliveryReceiptServiceTests.operations("READY");

        @Override
        public PrivateDemoOperationsVo operations() {
            return operations;
        }
    }

    private static final class StubPrivateDemoLaunchRehearsalService implements PrivateDemoLaunchRehearsalService {
        @Override
        public PrivateDemoLaunchRehearsalVo launchRehearsal() {
            return launch("READY");
        }
    }

    private static final class StubPrivateDemoEvidenceGalleryService implements PrivateDemoEvidenceGalleryService {
        private PrivateDemoEvidenceGalleryVo gallery = gallery("READY", 1, "job-best");

        @Override
        public PrivateDemoEvidenceGalleryVo evidenceGallery(Integer limit) {
            return gallery;
        }
    }

    private static final class StubPrivateDemoRunArchiveService implements PrivateDemoRunArchiveService {
        private PrivateDemoRunArchiveVo archive = archive("READY", "job-best");

        @Override
        public PrivateDemoRunArchiveVo runArchive() {
            return archive;
        }
    }

    private static final class StubDemoSessionCommandCenterService implements DemoSessionCommandCenterService {
        private String lastJobId;
        private DemoSessionCommandCenterVo center;

        @Override
        public DemoSessionCommandCenterVo commandCenter(String jobId) {
            lastJobId = jobId;
            return center == null ? PrivateDemoDeliveryReceiptServiceTests.commandCenter(jobId, "READY") : center;
        }

        @Override
        public String commandCenterMarkdown(String jobId) {
            return "# Command Center";
        }
    }

    private static final class StubDemoSessionRecoveryBoardService implements DemoSessionRecoveryBoardService {
        @Override
        public DemoSessionRecoveryBoardVo board(Integer limit) {
            return recoveryBoard("READY");
        }

        @Override
        public String boardMarkdown(Integer limit) {
            return "# Recovery";
        }
    }

    private static final class StubModelUsageLedgerService implements ModelUsageLedgerService {
        @Override
        public ModelUsageLedgerVo ledger(Integer limit) {
            return PrivateDemoDeliveryReceiptServiceTests.ledger("READY");
        }

        @Override
        public String ledgerMarkdown(Integer limit) {
            return "# Ledger";
        }
    }

    private static final class StubOpenAiReadinessEvidenceService implements OpenAiReadinessEvidenceService {
        @Override
        public OpenAiReadinessEvidenceVo getEvidence() {
            return openAi("READY");
        }

        @Override
        public String evidenceMarkdown() {
            return "# OpenAI Readiness";
        }
    }
}
