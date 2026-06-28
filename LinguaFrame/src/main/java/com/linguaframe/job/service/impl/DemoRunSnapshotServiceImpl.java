package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.bo.StoredDemoRunSnapshotPackageBo;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.DemoPresenterPackDownloadVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackVo;
import com.linguaframe.job.domain.vo.DemoRunMonitorVo;
import com.linguaframe.job.domain.vo.DemoRunSnapshotLinkVo;
import com.linguaframe.job.domain.vo.DemoRunSnapshotSectionVo;
import com.linguaframe.job.domain.vo.DemoRunSnapshotVo;
import com.linguaframe.job.domain.vo.DemoShareSheetVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.service.DeliveryManifestService;
import com.linguaframe.job.service.DemoPresenterPackService;
import com.linguaframe.job.service.DemoRunMonitorService;
import com.linguaframe.job.service.DemoRunSnapshotService;
import com.linguaframe.job.service.DemoShareSheetService;
import com.linguaframe.job.service.JobEvidenceReportService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DemoRunSnapshotServiceImpl implements DemoRunSnapshotService {

    private static final String CONTENT_TYPE = "application/zip";
    private static final List<String> PACKAGE_ENTRIES = List.of(
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
    private static final List<String> EXCLUSION_POLICY = List.of(
            "media bytes",
            "uploaded source video bytes",
            "generated media bytes",
            "transcript content",
            "subtitle content",
            "corrected draft text",
            "object keys",
            "local paths",
            "provider request bodies",
            "credentials",
            "demo tokens"
    );
    private static final List<String> FORBIDDEN_MARKERS = List.of(
            "/Users/",
            "source-videos/",
            "job-artifacts/",
            "objectKey",
            "demo-access-token",
            "private-demo-token",
            "OPENAI_API_KEY",
            "sk-",
            "raw transcript text",
            "raw subtitle text",
            "raw generated subtitle",
            "raw corrected subtitle",
            "provider payload",
            "provider request payload"
    );

    private final LocalizationJobQueryService queryService;
    private final DemoShareSheetService shareSheetService;
    private final DemoRunMonitorService monitorService;
    private final DemoPresenterPackService presenterPackService;
    private final DeliveryManifestService deliveryManifestService;
    private final JobEvidenceReportService evidenceReportService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DemoRunSnapshotServiceImpl(
            LocalizationJobQueryService queryService,
            DemoShareSheetService shareSheetService,
            DemoRunMonitorService monitorService,
            DemoPresenterPackService presenterPackService,
            DeliveryManifestService deliveryManifestService,
            JobEvidenceReportService evidenceReportService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.queryService = queryService;
        this.shareSheetService = shareSheetService;
        this.monitorService = monitorService;
        this.presenterPackService = presenterPackService;
        this.deliveryManifestService = deliveryManifestService;
        this.evidenceReportService = evidenceReportService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public DemoRunSnapshotVo buildSnapshot(String jobId) {
        SnapshotParts parts = parts(jobId);
        return snapshot(parts, Instant.now(clock));
    }

    @Override
    public StoredDemoRunSnapshotPackageBo openSnapshotPackage(String jobId) {
        SnapshotParts parts = parts(jobId);
        DemoRunSnapshotVo snapshot = snapshot(parts, Instant.now(clock));

        ByteArrayOutputStream archiveBytes = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(archiveBytes)) {
            writeEntry(zipOutputStream, "index.html", indexHtml(snapshot).getBytes(StandardCharsets.UTF_8));
            writeEntry(zipOutputStream, "manifest.json", writeJson(manifest(snapshot)));
            writeEntry(zipOutputStream, "README.md", snapshot.markdown().getBytes(StandardCharsets.UTF_8));
            writeEntry(zipOutputStream, "demo-share-sheet.md",
                    safeMarkdown("Demo Share Sheet", parts.shareSheet().markdown(), parts.job())
                            .getBytes(StandardCharsets.UTF_8));
            writeEntry(zipOutputStream, "demo-share-sheet.json", writeJson(safeShareSheet(parts.shareSheet())));
            writeEntry(zipOutputStream, "demo-run-monitor.md",
                    safeMarkdown("Demo Run Monitor", parts.monitor().markdown(), parts.job())
                            .getBytes(StandardCharsets.UTF_8));
            writeEntry(zipOutputStream, "demo-run-monitor.json", writeJson(safeRunMonitor(parts.monitor())));
            writeEntry(zipOutputStream, "presenter-pack.json", writeJson(safePresenterPack(parts.presenterPack())));
            writeEntry(zipOutputStream, "delivery-manifest.md",
                    safeMarkdown("Delivery Manifest", deliveryManifestService.buildMarkdownManifest(jobId), parts.job())
                            .getBytes(StandardCharsets.UTF_8));
            writeEntry(zipOutputStream, "diagnostics.json", writeJson(safeDiagnostics(parts.diagnostics())));
            writeEntry(zipOutputStream, "evidence.md",
                    safeMarkdown("Evidence", evidenceReportService.buildMarkdownReport(jobId), parts.job())
                            .getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create demo run snapshot package.", ex);
        }

        byte[] content = archiveBytes.toByteArray();
        return new StoredDemoRunSnapshotPackageBo(
                "linguaframe-job-%s-demo-run-snapshot.zip".formatted(safeFilenamePart(parts.job().jobId())),
                CONTENT_TYPE,
                content.length,
                new ByteArrayInputStream(content)
        );
    }

    private SnapshotParts parts(String jobId) {
        LocalizationJobVo job = queryService.getJob(jobId);
        return new SnapshotParts(
                job,
                queryService.getDiagnosticsReport(jobId),
                shareSheetService.buildShareSheet(jobId),
                monitorService.buildMonitor(jobId),
                presenterPackService.buildPresenterPack(jobId)
        );
    }

    private DemoRunSnapshotVo snapshot(SnapshotParts parts, Instant generatedAt) {
        LocalizationJobVo job = parts.job();
        String readiness = readiness(job, parts.shareSheet(), parts.monitor());
        List<DemoRunSnapshotSectionVo> sections = sections(readiness, parts.shareSheet(), parts.monitor());
        List<DemoRunSnapshotLinkVo> links = links(job.jobId(), parts.shareSheet(), parts.presenterPack());
        String headline = headline(parts.shareSheet(), job);
        String summary = summary(job, readiness);
        String markdown = markdown(job, generatedAt, readiness, headline, summary, sections, links);
        return new DemoRunSnapshotVo(
                job.jobId(),
                job.videoId(),
                job.targetLanguage(),
                valueOrDefault(job.demoProfileId(), "manual"),
                generatedAt,
                readiness,
                headline,
                summary,
                sections,
                PACKAGE_ENTRIES,
                links,
                EXCLUSION_POLICY,
                markdown
        );
    }

    private String readiness(LocalizationJobVo job, DemoShareSheetVo shareSheet, DemoRunMonitorVo monitor) {
        if (job.status() == LocalizationJobStatus.PROCESSING || job.status() == LocalizationJobStatus.QUEUED) {
            return "IN_PROGRESS";
        }
        if (job.status() == LocalizationJobStatus.COMPLETED
                && "READY".equals(shareSheet.readiness())
                && "READY".equals(monitor.attentionLevel())) {
            return "READY";
        }
        return "NEEDS_ATTENTION";
    }

    private List<DemoRunSnapshotSectionVo> sections(
            String readiness,
            DemoShareSheetVo shareSheet,
            DemoRunMonitorVo monitor
    ) {
        return List.of(
                section("INDEX_HTML", "Offline index", readiness, "index.html", "Self-contained HTML entry point for reviewers."),
                section("SHARE_SHEET", "Demo share sheet", shareSheet.readiness(), "demo-share-sheet.md", shareSheet.summary()),
                section("RUN_MONITOR", "Demo run monitor", monitor.attentionLevel(), "demo-run-monitor.md", monitor.summary()),
                section("PRESENTER_PACK", "Presenter pack", readiness, "presenter-pack.json", "Presenter-ready run metadata and safe package links."),
                section("DELIVERY", "Delivery manifest", readiness, "delivery-manifest.md", "Reviewed handoff and generated audit metadata."),
                section("DIAGNOSTICS", "Diagnostics", readiness, "diagnostics.json", "Machine-readable sanitized job diagnostics."),
                section("EVIDENCE", "Evidence report", readiness, "evidence.md", "Readable safe job evidence.")
        );
    }

    private DemoRunSnapshotSectionVo section(String kind, String title, String status, String filename, String summary) {
        return new DemoRunSnapshotSectionVo(kind, title, status, filename, sanitize(summary));
    }

    private List<DemoRunSnapshotLinkVo> links(
            String jobId,
            DemoShareSheetVo shareSheet,
            DemoPresenterPackVo presenterPack
    ) {
        List<DemoRunSnapshotLinkVo> links = new ArrayList<>();
        links.add(new DemoRunSnapshotLinkVo(
                "DEMO_RUN_SNAPSHOT_DOWNLOAD",
                "Static demo snapshot ZIP",
                "/api/jobs/" + jobId + "/demo-run-snapshot/download"
        ));
        links.add(new DemoRunSnapshotLinkVo("DEMO_SHARE_SHEET", "Demo share sheet", "/api/jobs/" + jobId + "/demo-share-sheet"));
        links.add(new DemoRunSnapshotLinkVo("DEMO_RUN_MONITOR", "Demo run monitor", "/api/jobs/" + jobId + "/demo-run-monitor"));
        links.add(new DemoRunSnapshotLinkVo("JOB_DETAIL", "Job detail", "/api/jobs/" + jobId));
        for (DemoShareSheetLinkVoAdapter link : shareSheet.links().stream()
                .map(item -> new DemoShareSheetLinkVoAdapter(item.kind(), item.label(), item.url()))
                .toList()) {
            links.add(new DemoRunSnapshotLinkVo(link.kind(), link.label(), link.url()));
        }
        for (DemoPresenterPackDownloadVo download : presenterPack.downloads()) {
            boolean exists = links.stream().anyMatch(link -> link.kind().equals(download.kind()));
            if (!exists) {
                links.add(new DemoRunSnapshotLinkVo(download.kind(), download.label(), download.url()));
            }
        }
        return List.copyOf(links);
    }

    private String headline(DemoShareSheetVo shareSheet, LocalizationJobVo job) {
        String headline = shareSheet.headline();
        if (headline == null || headline.isBlank()) {
            return "%s demo to %s".formatted(valueOrDefault(job.demoProfileId(), "manual"), job.targetLanguage());
        }
        return sanitize(headline);
    }

    private String summary(LocalizationJobVo job, String readiness) {
        String advice = switch (readiness) {
            case "READY" -> "Open index.html from the ZIP for offline review.";
            case "IN_PROGRESS" -> "Wait for completion before using this as final reviewer evidence.";
            default -> "Open diagnostics before sharing this reviewer workspace.";
        };
        return "This offline reviewer snapshot captures metadata-only evidence for job %s while it is %s. %s"
                .formatted(job.jobId(), job.status(), advice);
    }

    private String markdown(
            LocalizationJobVo job,
            Instant generatedAt,
            String readiness,
            String headline,
            String summary,
            List<DemoRunSnapshotSectionVo> sections,
            List<DemoRunSnapshotLinkVo> links
    ) {
        List<String> lines = new ArrayList<>();
        lines.add("# LinguaFrame Demo Snapshot");
        lines.add("");
        lines.add("- Job: " + job.jobId());
        lines.add("- Video: " + job.videoId());
        lines.add("- Target language: " + job.targetLanguage());
        lines.add("- Demo profile: " + valueOrDefault(job.demoProfileId(), "manual"));
        lines.add("- Status: " + job.status());
        lines.add("- Generated at: " + generatedAt);
        lines.add("- Readiness: " + readiness);
        lines.add("- Headline: " + headline);
        lines.add("");
        lines.add(summary);
        lines.add("");
        lines.add("## Sections");
        for (DemoRunSnapshotSectionVo section : sections) {
            lines.add("- %s (%s): %s".formatted(section.title(), section.filename(), section.status()));
        }
        lines.add("");
        lines.add("## Safe Links");
        for (DemoRunSnapshotLinkVo link : links) {
            lines.add("- %s: %s".formatted(link.label(), link.url()));
        }
        lines.add("");
        lines.add("## Excludes");
        for (String exclusion : EXCLUSION_POLICY) {
            lines.add("- " + exclusion);
        }
        lines.add("");
        if ("READY".equals(readiness)) {
            lines.add("Next action: Open index.html from the ZIP for offline review.");
        } else if ("IN_PROGRESS".equals(readiness)) {
            lines.add("Next action: Wait for completion before using this as final reviewer evidence.");
        } else {
            lines.add("Next action: Open diagnostics before sharing this reviewer workspace.");
        }
        lines.add("");
        return String.join("\n", lines);
    }

    private Map<String, Object> manifest(DemoRunSnapshotVo snapshot) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("generatedAt", snapshot.generatedAt());
        value.put("jobId", snapshot.jobId());
        value.put("videoId", snapshot.videoId());
        value.put("targetLanguage", snapshot.targetLanguage());
        value.put("demoProfileId", snapshot.demoProfileId());
        value.put("readiness", snapshot.readiness());
        value.put("headline", snapshot.headline());
        value.put("entries", snapshot.packageEntries());
        value.put("sections", snapshot.sections());
        value.put("links", snapshot.links());
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("includesMediaBytes", false);
        safety.put("includesUploadedSourceVideo", false);
        safety.put("includesGeneratedArtifacts", false);
        safety.put("includesRawTranscriptText", false);
        safety.put("includesRawSubtitleText", false);
        safety.put("includesCorrectedDraftText", false);
        safety.put("includesObjectKeys", false);
        safety.put("includesLocalPaths", false);
        safety.put("includesProviderPayloads", false);
        safety.put("includesCredentials", false);
        safety.put("includesDemoTokens", false);
        value.put("safety", safety);
        return value;
    }

    private Map<String, Object> safeDiagnostics(JobDiagnosticsReportVo diagnostics) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("generatedAt", diagnostics.generatedAt());
        value.put("jobId", diagnostics.job().jobId());
        value.put("videoId", diagnostics.job().videoId());
        value.put("targetLanguage", diagnostics.job().targetLanguage());
        value.put("status", diagnostics.job().status());
        value.put("artifactCount", diagnostics.artifactCount());
        value.put("artifacts", diagnostics.artifacts());
        value.put("usageSummary", diagnostics.job().usageSummary());
        value.put("cacheSummary", diagnostics.job().cacheSummary());
        value.put("qualityEvaluation", diagnostics.job().qualityEvaluation());
        value.put("pipelineProgress", diagnostics.job().pipelineProgress());
        return value;
    }

    private Map<String, Object> safeShareSheet(DemoShareSheetVo shareSheet) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("jobId", shareSheet.jobId());
        value.put("videoId", shareSheet.videoId());
        value.put("generatedAt", shareSheet.generatedAt());
        value.put("readiness", shareSheet.readiness());
        value.put("headline", sanitize(shareSheet.headline()));
        value.put("summary", sanitize(shareSheet.summary()));
        value.put("outcomeBullets", shareSheet.outcomeBullets().stream().map(this::sanitize).toList());
        value.put("recommendedNextAction", sanitize(shareSheet.recommendedNextAction()));
        value.put("links", shareSheet.links());
        value.put("markdown", safeMarkdown("Demo Share Sheet", shareSheet.markdown(), null));
        return value;
    }

    private Map<String, Object> safeRunMonitor(DemoRunMonitorVo monitor) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("jobId", monitor.jobId());
        value.put("videoId", monitor.videoId());
        value.put("status", monitor.status());
        value.put("dispatchStatus", monitor.dispatchStatus());
        value.put("generatedAt", monitor.generatedAt());
        value.put("elapsedMs", monitor.elapsedMs());
        value.put("currentStage", monitor.currentStage());
        value.put("completedStageCount", monitor.completedStageCount());
        value.put("totalStageCount", monitor.totalStageCount());
        value.put("failedStageCount", monitor.failedStageCount());
        value.put("slowestStage", monitor.slowestStage());
        value.put("slowestStageDurationMs", monitor.slowestStageDurationMs());
        value.put("attentionLevel", monitor.attentionLevel());
        value.put("summary", sanitize(monitor.summary()));
        value.put("recommendedNextAction", sanitize(monitor.recommendedNextAction()));
        value.put("stages", monitor.stages());
        value.put("links", monitor.links());
        value.put("markdown", safeMarkdown("Demo Run Monitor", monitor.markdown(), null));
        return value;
    }

    private Map<String, Object> safePresenterPack(DemoPresenterPackVo presenterPack) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("anchorJobId", presenterPack.anchorJobId());
        value.put("videoId", presenterPack.videoId());
        value.put("generatedAt", presenterPack.generatedAt());
        value.put("headline", sanitize(presenterPack.headline()));
        value.put("readinessStatus", presenterPack.readinessStatus());
        value.put("recommendedBaselineJobId", presenterPack.recommendedBaselineJobId());
        value.put("bestQualityJobId", presenterPack.bestQualityJobId());
        value.put("lowestCostJobId", presenterPack.lowestCostJobId());
        value.put("runs", presenterPack.runs());
        value.put("downloads", presenterPack.downloads());
        value.put("presenterNotesMarkdown", safeMarkdown("Presenter Pack", presenterPack.presenterNotesMarkdown(), null));
        return value;
    }

    private String indexHtml(DemoRunSnapshotVo snapshot) {
        List<String> sectionItems = snapshot.sections().stream()
                .map(section -> "<li><strong>%s</strong> <a href=\"%s\">%s</a><span>%s</span></li>".formatted(
                        html(section.title()),
                        htmlAttribute(section.filename()),
                        html(section.filename()),
                        html(" - " + section.status() + " - " + section.summary())
                ))
                .toList();
        List<String> linkItems = snapshot.links().stream()
                .map(link -> "<li><a href=\"%s\">%s</a><span>%s</span></li>".formatted(
                        htmlAttribute(link.url()),
                        html(link.label()),
                        html(" - " + link.kind())
                ))
                .toList();
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>LinguaFrame Demo Snapshot</title>
                  <style>
                    body { font-family: Arial, sans-serif; margin: 32px; color: #172026; line-height: 1.45; }
                    main { max-width: 920px; }
                    h1 { font-size: 28px; margin-bottom: 8px; }
                    h2 { font-size: 18px; margin-top: 28px; }
                    .meta { color: #51606d; }
                    li { margin: 6px 0; }
                    code { background: #eef2f5; padding: 2px 4px; border-radius: 4px; }
                  </style>
                </head>
                <body>
                <main>
                  <h1>LinguaFrame Demo Snapshot</h1>
                  <p class="meta">%s</p>
                  <p>%s</p>
                  <h2>Run</h2>
                  <ul>
                    <li>Job: <code>%s</code></li>
                    <li>Video: <code>%s</code></li>
                    <li>Target language: <code>%s</code></li>
                    <li>Readiness: <code>%s</code></li>
                  </ul>
                  <h2>Packaged Files</h2>
                  <ul>
                    %s
                  </ul>
                  <h2>Live Links</h2>
                  <ul>
                    %s
                  </ul>
                  <h2>Safety</h2>
                  <p>This package contains metadata-only reviewer evidence and excludes %s.</p>
                </main>
                </body>
                </html>
                """.formatted(
                html(snapshot.headline()),
                html(snapshot.summary()),
                html(snapshot.jobId()),
                html(snapshot.videoId()),
                html(snapshot.targetLanguage()),
                html(snapshot.readiness()),
                String.join("\n", sectionItems),
                String.join("\n", linkItems),
                html(String.join(", ", EXCLUSION_POLICY))
        );
    }

    private String safeMarkdown(String title, String markdown, LocalizationJobVo job) {
        if (!containsForbiddenMarker(markdown)) {
            return markdown;
        }
        return String.join("\n",
                "# LinguaFrame " + title,
                "",
                "- Job: " + (job == null ? "N/A" : job.jobId()),
                "- Status: " + (job == null ? "N/A" : job.status()),
                "- Original report was omitted from this snapshot because it contained fields outside the package safety contract.",
                "- Use diagnostics and snapshot manifest entries for safe metadata.",
                ""
        );
    }

    private boolean containsForbiddenMarker(String text) {
        return text != null && FORBIDDEN_MARKERS.stream().anyMatch(text::contains);
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replaceAll("(?i)<script[^>]*>.*?</script>", "").trim();
        if (!containsForbiddenMarker(value)) {
            return sanitized;
        }
        for (String marker : FORBIDDEN_MARKERS) {
            sanitized = sanitized.replace(marker, "[redacted]");
        }
        return sanitized;
    }

    private byte[] writeJson(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to write demo run snapshot JSON.", ex);
        }
    }

    private void writeEntry(ZipOutputStream zipOutputStream, String name, byte[] content) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(content);
        zipOutputStream.closeEntry();
    }

    private String html(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String htmlAttribute(String value) {
        return html(value).replace("\n", "");
    }

    private String safeFilenamePart(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record SnapshotParts(
            LocalizationJobVo job,
            JobDiagnosticsReportVo diagnostics,
            DemoShareSheetVo shareSheet,
            DemoRunMonitorVo monitor,
            DemoPresenterPackVo presenterPack
    ) {
    }

    private record DemoShareSheetLinkVoAdapter(String kind, String label, String url) {
    }
}
