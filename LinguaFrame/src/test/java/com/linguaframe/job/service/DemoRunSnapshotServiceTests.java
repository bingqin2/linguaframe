package com.linguaframe.job.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.linguaframe.job.domain.bo.StoredDemoRunSnapshotPackageBo;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.QualityEvaluationStatus;
import com.linguaframe.job.domain.vo.DeliveryManifestLinkVo;
import com.linguaframe.job.domain.vo.DeliveryManifestVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackDownloadVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackRunVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackVo;
import com.linguaframe.job.domain.vo.DemoRunMonitorLinkVo;
import com.linguaframe.job.domain.vo.DemoRunMonitorStageVo;
import com.linguaframe.job.domain.vo.DemoRunMonitorVo;
import com.linguaframe.job.domain.vo.DemoRunSnapshotVo;
import com.linguaframe.job.domain.vo.DemoShareSheetLinkVo;
import com.linguaframe.job.domain.vo.DemoShareSheetVo;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsArtifactVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.service.impl.DemoRunSnapshotServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class DemoRunSnapshotServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-29T12:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void buildsReadySnapshotPreviewWithSectionsEntriesAndSafetyPolicy() {
        DemoRunSnapshotService service = service(LocalizationJobStatus.COMPLETED, "READY", "READY");

        DemoRunSnapshotVo snapshot = service.buildSnapshot("job-snapshot");

        assertThat(snapshot.jobId()).isEqualTo("job-snapshot");
        assertThat(snapshot.videoId()).isEqualTo("video-snapshot");
        assertThat(snapshot.targetLanguage()).isEqualTo("zh-CN");
        assertThat(snapshot.demoProfileId()).isEqualTo("tears-showcase");
        assertThat(snapshot.generatedAt()).isEqualTo(Instant.parse("2026-06-29T12:00:00Z"));
        assertThat(snapshot.readiness()).isEqualTo("READY");
        assertThat(snapshot.headline()).isEqualTo("tears-showcase demo to zh-CN");
        assertThat(snapshot.summary()).contains("offline reviewer snapshot", "COMPLETED");
        assertThat(snapshot.sections()).extracting(section -> section.kind()).containsExactly(
                "INDEX_HTML",
                "SHARE_SHEET",
                "RUN_MONITOR",
                "PRESENTER_PACK",
                "DELIVERY",
                "DIAGNOSTICS",
                "EVIDENCE"
        );
        assertThat(snapshot.packageEntries()).containsExactly(
                "index.html",
                "manifest.json",
                "README.md",
                "demo-share-sheet.md",
                "demo-share-sheet.json",
                "demo-run-monitor.md",
                "demo-run-monitor.json",
                "presenter-pack.json",
                "delivery-manifest.md",
                "diagnostics.json",
                "evidence.md"
        );
        assertThat(snapshot.links()).extracting(link -> link.kind()).contains(
                "DEMO_RUN_SNAPSHOT_DOWNLOAD",
                "DEMO_RUN_PACKAGE",
                "DEMO_SHARE_SHEET",
                "DEMO_RUN_MONITOR"
        );
        assertThat(snapshot.exclusionPolicy()).contains(
                "media bytes",
                "transcript content",
                "provider request bodies",
                "credentials"
        );
        assertThat(snapshot.markdown()).contains("# LinguaFrame Demo Snapshot");
        assertThat(snapshot.markdown()).doesNotContain("raw transcript text");
        assertThat(snapshot.markdown()).doesNotContain("/Users/example");
        assertThat(snapshot.markdown()).doesNotContain("sk-test");
        assertThat(snapshot.markdown()).doesNotContain("provider payload");
    }

    @Test
    void opensStaticSnapshotPackageWithEscapedHtmlAndMetadataOnlyEntries() throws IOException {
        DemoRunSnapshotService service = service(LocalizationJobStatus.COMPLETED, "READY", "READY");

        StoredDemoRunSnapshotPackageBo result = service.openSnapshotPackage("job-snapshot");

        assertThat(result.filename()).isEqualTo("linguaframe-job-job-snapshot-demo-run-snapshot.zip");
        assertThat(result.contentType()).isEqualTo("application/zip");
        assertThat(result.sizeBytes()).isPositive();
        Map<String, String> entries = readZipEntries(result.inputStream());
        assertThat(entries).containsOnlyKeys(
                "index.html",
                "manifest.json",
                "README.md",
                "demo-share-sheet.md",
                "demo-share-sheet.json",
                "demo-run-monitor.md",
                "demo-run-monitor.json",
                "presenter-pack.json",
                "delivery-manifest.md",
                "diagnostics.json",
                "evidence.md"
        );
        assertThat(entries.get("index.html"))
                .contains("<!doctype html>")
                .contains("LinguaFrame Demo Snapshot")
                .contains("demo-share-sheet.md")
                .contains("tears-showcase demo to zh-CN")
                .doesNotContain("<script>alert('x')</script>");
        assertThat(entries.get("manifest.json"))
                .contains("\"jobId\":\"job-snapshot\"")
                .contains("\"includesMediaBytes\":false")
                .contains("\"includesProviderPayloads\":false");
        assertThat(entries.get("README.md")).contains("# LinguaFrame Demo Snapshot");
        assertThat(entries.get("demo-share-sheet.md")).contains("# tears-showcase demo to zh-CN");
        assertThat(entries.get("demo-run-monitor.md")).contains("# LinguaFrame Demo Run Monitor");
        assertThat(entries.get("delivery-manifest.md")).contains("# Delivery Manifest");
        assertThat(entries.get("evidence.md")).contains("# Evidence");

        String combined = String.join("\n", entries.values());
        assertThat(combined)
                .doesNotContain("raw transcript text")
                .doesNotContain("raw subtitle text")
                .doesNotContain("provider request payload")
                .doesNotContain("job-artifacts/")
                .doesNotContain("/Users/")
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("private-demo-token")
                .doesNotContain("sk-test");
    }

    @Test
    void marksFailedSnapshotNeedsAttentionWithoutLeakingFailureReason() {
        DemoRunSnapshotService service = service(LocalizationJobStatus.FAILED, "NEEDS_ATTENTION", "BLOCKED");

        DemoRunSnapshotVo snapshot = service.buildSnapshot("job-snapshot");

        assertThat(snapshot.readiness()).isEqualTo("NEEDS_ATTENTION");
        assertThat(snapshot.summary()).contains("FAILED");
        assertThat(snapshot.sections()).extracting(section -> section.status()).contains("NEEDS_ATTENTION");
        assertThat(snapshot.markdown()).contains("Open diagnostics before sharing");
        assertThat(snapshot.markdown()).doesNotContain("raw transcript text");
        assertThat(snapshot.markdown()).doesNotContain("/Users/example");
        assertThat(snapshot.markdown()).doesNotContain("sk-test");
    }

    @Test
    void marksInProgressSnapshotAsInProgressReviewerWorkspace() {
        DemoRunSnapshotService service = service(LocalizationJobStatus.PROCESSING, "NEEDS_ATTENTION", "RUNNING");

        DemoRunSnapshotVo snapshot = service.buildSnapshot("job-snapshot");

        assertThat(snapshot.readiness()).isEqualTo("IN_PROGRESS");
        assertThat(snapshot.summary()).contains("PROCESSING");
        assertThat(snapshot.sections()).extracting(section -> section.kind()).contains("RUN_MONITOR");
        assertThat(snapshot.sections()).extracting(section -> section.status()).contains("RUNNING");
        assertThat(snapshot.markdown()).contains("Wait for completion before using this as final reviewer evidence.");
    }

    private DemoRunSnapshotService service(
            LocalizationJobStatus status,
            String shareReadiness,
            String monitorAttention
    ) {
        LocalizationJobVo job = job(status);
        return new DemoRunSnapshotServiceImpl(
                new SingleJobQueryService(job),
                new StaticShareSheetService(shareSheet(job, shareReadiness)),
                new StaticRunMonitorService(runMonitor(job, monitorAttention)),
                new StaticPresenterPackService(presenterPack(job)),
                new StaticDeliveryManifestService(job),
                new StaticEvidenceReportService(),
                objectMapper,
                FIXED_CLOCK
        );
    }

    private static Map<String, String> readZipEntries(java.io.InputStream inputStream) throws IOException {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    private static LocalizationJobVo job(LocalizationJobStatus status) {
        return new LocalizationJobVo(
                "job-snapshot",
                "video-snapshot",
                "zh-CN",
                "verse",
                "NATURAL",
                "STANDARD",
                0,
                "",
                "OFF",
                "tears-showcase",
                status,
                Instant.parse("2026-06-29T11:00:00Z"),
                Instant.parse("2026-06-29T11:00:10Z"),
                status == LocalizationJobStatus.COMPLETED ? Instant.parse("2026-06-29T11:08:00Z") : null,
                status == LocalizationJobStatus.FAILED ? Instant.parse("2026-06-29T11:04:00Z") : null,
                status == LocalizationJobStatus.FAILED
                        ? com.linguaframe.job.domain.enums.LocalizationJobStage.TARGET_SUBTITLE_EXPORT
                        : null,
                status == LocalizationJobStatus.FAILED
                        ? "raw transcript text /Users/example sk-test provider payload"
                        : null,
                0,
                JobDispatchEventStatus.DISPATCHED,
                1,
                Instant.parse("2026-06-29T11:00:05Z"),
                List.of(),
                new JobUsageSummaryVo(4, 0, 1200L, new BigDecimal("0.03400000"), 100, 80, null, 480),
                new JobCacheSummaryVo(1, 8, 2),
                List.of(),
                new QualityEvaluationVo(
                        "quality-snapshot",
                        "job-snapshot",
                        "zh-CN",
                        91,
                        "GOOD",
                        92,
                        90,
                        91,
                        90,
                        List.of(),
                        List.of(),
                        QualityEvaluationStatus.SUCCEEDED,
                        null,
                        Instant.parse("2026-06-29T11:06:00Z")
                ),
                null,
                null
        );
    }

    private static JobDiagnosticsReportVo diagnostics(LocalizationJobVo job) {
        return new JobDiagnosticsReportVo(
                Instant.parse("2026-06-29T11:59:00Z"),
                job,
                List.of(new JobDiagnosticsArtifactVo(
                        "artifact-reviewed-srt",
                        JobArtifactType.REVIEWED_SUBTITLE_SRT,
                        "reviewed-subtitles.zh-CN.srt",
                        "application/x-subrip",
                        100L,
                        "hash-reviewed",
                        false,
                        null,
                        Instant.parse("2026-06-29T11:07:00Z")
                )),
                1
        );
    }

    private static DemoShareSheetVo shareSheet(LocalizationJobVo job, String readiness) {
        String unsafeHeadline = "tears-showcase demo to zh-CN <script>alert('x')</script>";
        return new DemoShareSheetVo(
                job.jobId(),
                job.videoId(),
                Instant.parse("2026-06-29T11:58:00Z"),
                readiness,
                unsafeHeadline,
                "COMPLETED job for zh-CN is %s.".formatted(job.status()),
                List.of("Status: " + job.status(), "Quality score: 91 (GOOD)"),
                readiness.equals("READY")
                        ? "Open the demo run package or reviewed handoff package for reviewer delivery."
                        : "Review diagnostics and failure triage before sharing this run.",
                List.of(
                        new DemoShareSheetLinkVo("DEMO_RUN_PACKAGE", "Demo run package", "/api/jobs/job-snapshot/demo-run-package/download"),
                        new DemoShareSheetLinkVo("HANDOFF_PACKAGE", "Reviewed handoff package", "/api/jobs/job-snapshot/handoff-package/download")
                ),
                "# tears-showcase demo to zh-CN\n\n- Status: " + job.status() + "\n"
        );
    }

    private static DemoRunMonitorVo runMonitor(LocalizationJobVo job, String attention) {
        return new DemoRunMonitorVo(
                job.jobId(),
                job.videoId(),
                job.status(),
                JobDispatchEventStatus.DISPATCHED,
                Instant.parse("2026-06-29T11:59:00Z"),
                480000L,
                job.status() == LocalizationJobStatus.PROCESSING
                        ? com.linguaframe.job.domain.enums.LocalizationJobStage.TARGET_SUBTITLE_EXPORT
                        : null,
                4,
                8,
                job.status() == LocalizationJobStatus.FAILED ? 1 : 0,
                com.linguaframe.job.domain.enums.LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT,
                120000L,
                attention,
                "Monitor is " + attention + " for " + job.status(),
                attention.equals("RUNNING")
                        ? "Keep watching the run monitor."
                        : "Open diagnostics before sharing.",
                List.of(new DemoRunMonitorStageVo(
                        com.linguaframe.job.domain.enums.LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                        JobTimelineEventStatus.STARTED,
                        Instant.parse("2026-06-29T11:03:00Z"),
                        null,
                        null,
                        120000L,
                        attention,
                        "Target subtitle export"
                )),
                List.of(new DemoRunMonitorLinkVo("DIAGNOSTICS_JSON", "Diagnostics JSON", "/api/jobs/job-snapshot/diagnostics/download")),
                "# LinguaFrame Demo Run Monitor\n\n- Attention level: " + attention + "\n"
        );
    }

    private static DemoPresenterPackVo presenterPack(LocalizationJobVo job) {
        return new DemoPresenterPackVo(
                job.jobId(),
                job.videoId(),
                Instant.parse("2026-06-29T11:59:00Z"),
                "tears-showcase demo to zh-CN",
                job.status() == LocalizationJobStatus.COMPLETED ? "READY" : "NEEDS_ATTENTION",
                "job-baseline",
                job.jobId(),
                "job-low-cost",
                List.of(new DemoPresenterPackRunVo(
                        job.jobId(),
                        "tears-showcase",
                        job.status(),
                        job.completedAt(),
                        91,
                        new BigDecimal("0.03400000"),
                        4,
                        2,
                        job.status() == LocalizationJobStatus.COMPLETED,
                        List.of("ANCHOR", "BEST_QUALITY")
                )),
                List.of(new DemoPresenterPackDownloadVo(
                        "DEMO_RUN_PACKAGE",
                        "Demo run package",
                        "/api/jobs/job-snapshot/demo-run-package/download"
                )),
                "# LinguaFrame Demo Presenter Pack\n"
        );
    }

    private record SingleJobQueryService(LocalizationJobVo job) implements LocalizationJobQueryService {
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
            return diagnostics(job);
        }
    }

    private record StaticShareSheetService(DemoShareSheetVo shareSheet) implements DemoShareSheetService {
        @Override
        public DemoShareSheetVo buildShareSheet(String jobId) {
            return shareSheet;
        }

        @Override
        public String buildMarkdownShareSheet(String jobId) {
            return shareSheet.markdown();
        }
    }

    private record StaticRunMonitorService(DemoRunMonitorVo monitor) implements DemoRunMonitorService {
        @Override
        public DemoRunMonitorVo buildMonitor(String jobId) {
            return monitor;
        }

        @Override
        public String buildMarkdownMonitor(String jobId) {
            return monitor.markdown();
        }
    }

    private record StaticPresenterPackService(DemoPresenterPackVo presenterPack) implements DemoPresenterPackService {
        @Override
        public DemoPresenterPackVo buildPresenterPack(String jobId) {
            return presenterPack;
        }
    }

    private record StaticDeliveryManifestService(LocalizationJobVo job) implements DeliveryManifestService {
        @Override
        public DeliveryManifestVo buildManifest(String jobId) {
            return new DeliveryManifestVo(
                    jobId,
                    job.videoId(),
                    job.targetLanguage(),
                    job.subtitleStylePreset(),
                    job.status(),
                    Instant.parse("2026-06-29T11:59:00Z"),
                    job.status() == LocalizationJobStatus.COMPLETED,
                    1,
                    false,
                    1,
                    List.of(),
                    List.of(),
                    List.of(new DeliveryManifestLinkVo(
                            "Demo run package",
                            "DEMO_RUN_PACKAGE",
                            "/api/jobs/job-snapshot/demo-run-package/download"
                    ))
            );
        }

        @Override
        public String buildMarkdownManifest(String jobId) {
            return "# Delivery Manifest\n\n- Handoff ready: " + (job.status() == LocalizationJobStatus.COMPLETED) + "\n";
        }
    }

    private record StaticEvidenceReportService() implements JobEvidenceReportService {
        @Override
        public String buildMarkdownReport(String jobId) {
            return "# Evidence\n\n- Job: job-snapshot\n";
        }
    }
}
