package com.linguaframe.operator.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.operator.domain.vo.DemoSampleMediaCatalogVo;
import com.linguaframe.operator.domain.vo.DemoSampleMediaCommandVo;
import com.linguaframe.operator.domain.vo.DemoSampleMediaConfiguredPathVo;
import com.linguaframe.operator.domain.vo.DemoSampleMediaItemVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsLinkVo;
import com.linguaframe.operator.service.DemoSampleMediaCatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DemoSampleMediaCatalogServiceImpl implements DemoSampleMediaCatalogService {

    private static final String READY = "READY";
    private static final String ATTENTION = "ATTENTION";
    private static final String CONFIGURED = "CONFIGURED";
    private static final String MISSING = "MISSING";
    private static final String UNCONFIGURED = "UNCONFIGURED";
    private static final String TEARS_SAMPLE_ID = "tears-of-steel-casting";

    private final LinguaFrameProperties properties;
    private final Map<String, String> environment;

    @Autowired
    public DemoSampleMediaCatalogServiceImpl(LinguaFrameProperties properties) {
        this(properties, System.getenv());
    }

    public DemoSampleMediaCatalogServiceImpl(LinguaFrameProperties properties, Map<String, String> environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public DemoSampleMediaCatalogVo catalog() {
        int durationLimitSeconds = properties.getMedia().getMaxDurationSeconds();
        List<DemoSampleMediaConfiguredPathVo> configuredPaths = List.of(
                configuredPath("LINGUAFRAME_TEARS_SAMPLE_PATH"),
                configuredPath("LINGUAFRAME_DEMO_SAMPLE_PATH")
        );
        String overallStatus = configuredPaths.stream().anyMatch(path -> CONFIGURED.equals(path.status()))
                ? READY
                : ATTENTION;
        List<DemoSampleMediaCommandVo> commands = commands();
        return new DemoSampleMediaCatalogVo(
                Instant.now(),
                overallStatus,
                durationLimitSeconds,
                TEARS_SAMPLE_ID,
                items(durationLimitSeconds),
                configuredPaths,
                commands,
                notes(overallStatus, durationLimitSeconds, configuredPaths, commands),
                documentationLinks()
        );
    }

    private DemoSampleMediaConfiguredPathVo configuredPath(String envVar) {
        String rawPath = environment.getOrDefault(envVar, "").trim();
        if (rawPath.isBlank()) {
            return new DemoSampleMediaConfiguredPathVo(
                    envVar,
                    UNCONFIGURED,
                    "",
                    "",
                    null,
                    envVar + " is not configured. Use the catalog sources before choosing a local sample.",
                    false
            );
        }

        Path path = Path.of(rawPath);
        String filename = path.getFileName() == null ? "configured-sample" : path.getFileName().toString();
        String extension = extension(filename);
        if (!Files.isRegularFile(path)) {
            return new DemoSampleMediaConfiguredPathVo(
                    envVar,
                    MISSING,
                    filename,
                    extension,
                    null,
                    envVar + " points to a missing local sample. Re-download the sample or update the env file.",
                    false
            );
        }

        return new DemoSampleMediaConfiguredPathVo(
                envVar,
                CONFIGURED,
                filename,
                extension,
                safeSize(path),
                envVar + " is configured and points to an existing local sample.",
                false
        );
    }

    private Long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception ex) {
            return null;
        }
    }

    private String extension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private List<DemoSampleMediaItemVo> items(int durationLimitSeconds) {
        return List.of(
                item(
                        TEARS_SAMPLE_ID,
                        "Tears of Steel casting clip",
                        "Blender Studio",
                        "https://studio.blender.org/films/tears-of-steel/",
                        "Credit Blender Studio / Tears of Steel and keep the local downloaded file out of git.",
                        "Check the Blender Studio film page for current license and attribution notes before sharing.",
                        "Best full local product demo: live-action dialogue, sci-fi scene context, CGI, translation, quality evaluation, and subtitle burn-in.",
                        "Current local casting sample is intended to stay complete and under " + durationLimitSeconds + " seconds.",
                        "scripts/demo/docker-e2e-tears-of-steel-full.sh",
                        List.of("recommended", "dialogue", "full-demo")
                ),
                item(
                        "big-buck-bunny-w3schools",
                        "Big Buck Bunny / W3Schools sample",
                        "W3Schools sample video",
                        "https://www.w3schools.com/html/mov_bbb.mp4",
                        "W3Schools marks the sample as courtesy of Big Buck Bunny.",
                        "Use for lightweight smoke checks; confirm attribution before external presentation.",
                        "Fast upload and pipeline check when you only need a stable MP4 sample.",
                        "Short sample should fit within " + durationLimitSeconds + " seconds.",
                        "LINGUAFRAME_DEMO_SAMPLE_PATH=/path/to/mov_bbb.mp4 scripts/demo/docker-e2e-success.sh",
                        List.of("quick-smoke", "stable-url")
                ),
                item(
                        "sintel",
                        "Sintel",
                        "Blender Studio",
                        "https://studio.blender.org/films/sintel/",
                        "Credit Blender Studio / Sintel when used.",
                        "Check the Blender Studio page before use; full film length usually exceeds the current upload limit.",
                        "Longer story context for translation naturalness after the project supports longer demos.",
                        "Use a complete file only when it is under " + durationLimitSeconds + " seconds, or wait for a larger configured limit.",
                        "",
                        List.of("context-translation", "future-long-demo")
                ),
                item(
                        "nasa-library",
                        "NASA Image and Video Library",
                        "NASA",
                        "https://images.nasa.gov/",
                        "Use each asset's metadata page for attribution.",
                        "Check each item metadata and license before downloading or presenting.",
                        "Technology-themed demos: rockets, space missions, explainers, or public science footage.",
                        "Pick a complete asset under " + durationLimitSeconds + " seconds.",
                        "",
                        List.of("technology", "public-library")
                ),
                item(
                        "internet-archive-movies",
                        "Internet Archive Movies",
                        "Internet Archive",
                        "https://archive.org/details/movies",
                        "Use each item's rights statement and metadata for attribution.",
                        "License varies per item; verify public-domain or Creative Commons status before use.",
                        "Speech, documentary, lecture, or newsreel samples for realistic voice testing.",
                        "Pick a complete item or complete excerpt under " + durationLimitSeconds + " seconds.",
                        "",
                        List.of("speech", "documentary", "license-varies")
                )
        );
    }

    private DemoSampleMediaItemVo item(
            String id,
            String title,
            String source,
            String sourceUrl,
            String attribution,
            String licenseGuidance,
            String recommendedUse,
            String durationGuidance,
            String command,
            List<String> tags
    ) {
        return new DemoSampleMediaItemVo(
                id,
                title,
                source,
                sourceUrl,
                attribution,
                licenseGuidance,
                recommendedUse,
                durationGuidance,
                command,
                tags
        );
    }

    private List<DemoSampleMediaCommandVo> commands() {
        return List.of(
                command(
                        "Inspect sample catalog",
                        "scripts/demo/demo-sample-media-catalog.sh",
                        "Download the same safe catalog JSON shown in the browser."
                ),
                command(
                        "Run full Tears sample",
                        "scripts/demo/docker-e2e-tears-of-steel-full.sh",
                        "Process the configured complete Tears of Steel casting sample."
                ),
                command(
                        "Run quick smoke sample",
                        "LINGUAFRAME_DEMO_SAMPLE_PATH=/path/to/sample.mp4 scripts/demo/docker-e2e-success.sh",
                        "Run the short deterministic smoke path with an explicit local sample."
                )
        );
    }

    private DemoSampleMediaCommandVo command(String label, String command, String description) {
        return new DemoSampleMediaCommandVo(label, command, description);
    }

    private List<PrivateDemoOperationsLinkVo> documentationLinks() {
        return List.of(
                new PrivateDemoOperationsLinkVo(
                        "Demo references",
                        "docs/product/demo-references.md",
                        "Public sample sources, attribution, and local-file handling notes."
                ),
                new PrivateDemoOperationsLinkVo(
                        "Docker E2E demo",
                        "docs/agent/docker-e2e-demo.md",
                        "Commands for short, full, cache, and OpenAI demo paths."
                ),
                new PrivateDemoOperationsLinkVo(
                        "Smoke checklist",
                        "docs/agent/smoke-test-checklist.md",
                        "Repeatable browser and terminal validation checks."
                )
        );
    }

    private String notes(
            String overallStatus,
            int durationLimitSeconds,
            List<DemoSampleMediaConfiguredPathVo> configuredPaths,
            List<DemoSampleMediaCommandVo> commands
    ) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("# LinguaFrame Demo Sample Media Catalog");
        lines.add("");
        lines.add("- Overall status: " + overallStatus);
        lines.add("- Upload duration limit: " + durationLimitSeconds + " seconds");
        lines.add("- Recommended sample: " + TEARS_SAMPLE_ID);
        lines.add("- This catalog does not download media, upload files, call providers, edit `.env`, start Docker, or create jobs.");
        lines.add("- If local samples are unavailable, use the remote public references to choose a complete file under the configured limit.");
        lines.add("");
        lines.add("## Configured Local Samples");
        for (DemoSampleMediaConfiguredPathVo path : configuredPaths) {
            lines.add("- " + path.envVar() + ": " + path.status() + " " + path.filename());
        }
        lines.add("");
        lines.add("## Recommended Commands");
        for (DemoSampleMediaCommandVo command : commands) {
            lines.add("- `" + command.command() + "`");
        }
        return String.join("\n", lines);
    }
}
