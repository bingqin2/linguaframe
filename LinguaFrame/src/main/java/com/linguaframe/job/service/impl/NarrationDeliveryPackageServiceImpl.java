package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.linguaframe.job.domain.bo.StoredNarrationDeliveryPackageBo;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.NarrationDeliveryPackageArtifactVo;
import com.linguaframe.job.domain.vo.NarrationDeliveryPackageCheckVo;
import com.linguaframe.job.domain.vo.NarrationDeliveryPackageLinkVo;
import com.linguaframe.job.domain.vo.NarrationDeliveryPackageVo;
import com.linguaframe.job.domain.vo.NarrationEvidenceVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewResolutionVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewVo;
import com.linguaframe.job.domain.vo.NarrationRecoveryHandoffVo;
import com.linguaframe.job.domain.vo.NarrationRenderReviewVo;
import com.linguaframe.job.domain.vo.NarrationScriptPackageVo;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.NarrationDeliveryPackageService;
import com.linguaframe.job.service.NarrationEvidenceService;
import com.linguaframe.job.service.NarrationPlaybackReviewResolutionService;
import com.linguaframe.job.service.NarrationPlaybackReviewService;
import com.linguaframe.job.service.NarrationRecoveryHandoffService;
import com.linguaframe.job.service.NarrationRenderReviewService;
import com.linguaframe.job.service.NarrationScriptPackageService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class NarrationDeliveryPackageServiceImpl implements NarrationDeliveryPackageService {

    private static final String READY = "READY";
    private static final String BLOCKED = "BLOCKED";
    private static final String ATTENTION = "ATTENTION";

    private final ObjectMapper objectMapper;
    private final JobArtifactService artifactService;
    private final NarrationEvidenceService evidenceService;
    private final NarrationScriptPackageService scriptPackageService;
    private final NarrationRenderReviewService renderReviewService;
    private final NarrationPlaybackReviewService playbackReviewService;
    private final NarrationPlaybackReviewResolutionService playbackResolutionService;
    private final Supplier<NarrationRecoveryHandoffService> recoveryHandoffService;
    private final Clock clock;

    @Autowired
    public NarrationDeliveryPackageServiceImpl(
            JobArtifactService artifactService,
            NarrationEvidenceService evidenceService,
            NarrationScriptPackageService scriptPackageService,
            NarrationRenderReviewService renderReviewService,
            NarrationPlaybackReviewService playbackReviewService,
            NarrationPlaybackReviewResolutionService playbackResolutionService,
            ObjectProvider<NarrationRecoveryHandoffService> recoveryHandoffService
    ) {
        this(new ObjectMapper().registerModule(new JavaTimeModule()), artifactService, evidenceService, scriptPackageService, renderReviewService,
                playbackReviewService, playbackResolutionService, recoveryHandoffService::getObject, Clock.systemUTC());
    }

    public NarrationDeliveryPackageServiceImpl(
            JobArtifactService artifactService,
            NarrationEvidenceService evidenceService,
            NarrationScriptPackageService scriptPackageService,
            NarrationRenderReviewService renderReviewService,
            NarrationPlaybackReviewService playbackReviewService,
            NarrationPlaybackReviewResolutionService playbackResolutionService,
            NarrationRecoveryHandoffService recoveryHandoffService
    ) {
        this(new ObjectMapper().registerModule(new JavaTimeModule()), artifactService, evidenceService, scriptPackageService, renderReviewService,
                playbackReviewService, playbackResolutionService, () -> recoveryHandoffService, Clock.systemUTC());
    }

    public NarrationDeliveryPackageServiceImpl(
            ObjectMapper objectMapper,
            JobArtifactService artifactService,
            NarrationEvidenceService evidenceService,
            NarrationScriptPackageService scriptPackageService,
            NarrationRenderReviewService renderReviewService,
            NarrationPlaybackReviewService playbackReviewService,
            NarrationPlaybackReviewResolutionService playbackResolutionService,
            NarrationRecoveryHandoffService recoveryHandoffService,
            Clock clock
    ) {
        this(objectMapper, artifactService, evidenceService, scriptPackageService, renderReviewService,
                playbackReviewService, playbackResolutionService, () -> recoveryHandoffService, clock);
    }

    private NarrationDeliveryPackageServiceImpl(
            ObjectMapper objectMapper,
            JobArtifactService artifactService,
            NarrationEvidenceService evidenceService,
            NarrationScriptPackageService scriptPackageService,
            NarrationRenderReviewService renderReviewService,
            NarrationPlaybackReviewService playbackReviewService,
            NarrationPlaybackReviewResolutionService playbackResolutionService,
            Supplier<NarrationRecoveryHandoffService> recoveryHandoffService,
            Clock clock
    ) {
        this.objectMapper = objectMapper;
        this.artifactService = artifactService;
        this.evidenceService = evidenceService;
        this.scriptPackageService = scriptPackageService;
        this.renderReviewService = renderReviewService;
        this.playbackReviewService = playbackReviewService;
        this.playbackResolutionService = playbackResolutionService;
        this.recoveryHandoffService = recoveryHandoffService;
        this.clock = clock;
    }

    @Override
    public NarrationDeliveryPackageVo getSummary(String jobId) {
        NarrationEvidenceVo evidence = evidenceService.getEvidence(jobId);
        NarrationScriptPackageVo scriptPackage = scriptPackageService.getPackage(jobId);
        NarrationRenderReviewVo renderReview = renderReviewService.getReview(jobId);
        NarrationPlaybackReviewVo playbackReview = playbackReviewService.getReview(jobId);
        NarrationPlaybackReviewResolutionVo resolution = playbackResolutionService.getResolution(jobId);
        List<NarrationDeliveryPackageArtifactVo> artifacts = artifacts(jobId);
        boolean audioReady = artifacts.stream().anyMatch(artifact -> "NARRATION_AUDIO".equals(artifact.artifactType()));
        boolean videoReady = artifacts.stream().anyMatch(artifact -> "NARRATED_VIDEO".equals(artifact.artifactType()));
        String status = summaryStatus(audioReady, videoReady, evidence, scriptPackage, renderReview, playbackReview, resolution);
        return new NarrationDeliveryPackageVo(
                jobId,
                Instant.now(clock),
                status,
                phase(status),
                nextAction(status, audioReady, videoReady, resolution),
                audioReady,
                videoReady,
                resolution.unresolvedSegmentCount(),
                safeStatus(evidence.status()),
                safeStatus(scriptPackage.status()),
                safeStatus(renderReview.status()),
                safeStatus(playbackReview.status()),
                safeStatus(resolution.status()),
                "SUMMARY_ONLY",
                artifacts,
                summaryChecks(audioReady, videoReady, evidence, scriptPackage, renderReview, playbackReview, resolution),
                safeLinks(jobId, artifacts),
                packageEntries(),
                safetyNotes()
        );
    }

    @Override
    public NarrationDeliveryPackageVo getPackage(String jobId) {
        NarrationEvidenceVo evidence = evidenceService.getEvidence(jobId);
        NarrationScriptPackageVo scriptPackage = scriptPackageService.getPackage(jobId);
        NarrationRenderReviewVo renderReview = renderReviewService.getReview(jobId);
        NarrationPlaybackReviewVo playbackReview = playbackReviewService.getReview(jobId);
        NarrationPlaybackReviewResolutionVo resolution = playbackResolutionService.getResolution(jobId);
        NarrationRecoveryHandoffVo handoff = recoveryHandoffService.get().getHandoff(jobId);
        List<NarrationDeliveryPackageArtifactVo> artifacts = artifacts(jobId);
        boolean audioReady = artifacts.stream().anyMatch(artifact -> "NARRATION_AUDIO".equals(artifact.artifactType()));
        boolean videoReady = artifacts.stream().anyMatch(artifact -> "NARRATED_VIDEO".equals(artifact.artifactType()));
        String status = status(audioReady, videoReady, evidence, scriptPackage, renderReview, playbackReview, resolution, handoff);
        return new NarrationDeliveryPackageVo(
                jobId,
                Instant.now(clock),
                status,
                phase(status),
                nextAction(status, audioReady, videoReady, resolution),
                audioReady,
                videoReady,
                resolution.unresolvedSegmentCount(),
                safeStatus(evidence.status()),
                safeStatus(scriptPackage.status()),
                safeStatus(renderReview.status()),
                safeStatus(playbackReview.status()),
                safeStatus(resolution.status()),
                safeStatus(handoff.status()),
                artifacts,
                checks(audioReady, videoReady, evidence, scriptPackage, renderReview, playbackReview, resolution, handoff),
                safeLinks(jobId, artifacts),
                packageEntries(),
                safetyNotes()
        );
    }

    @Override
    public String renderMarkdown(String jobId) {
        NarrationDeliveryPackageVo delivery = getPackage(jobId);
        List<String> lines = new ArrayList<>();
        lines.add("# LinguaFrame Narration Delivery Package");
        lines.add("");
        lines.add("## Summary");
        lines.add("- Job: " + delivery.jobId());
        lines.add("- Status: " + delivery.status());
        lines.add("- Phase: " + delivery.phase());
        lines.add("- Narration audio ready: " + delivery.audioReady());
        lines.add("- Narrated video ready: " + delivery.videoReady());
        lines.add("- Unresolved playback rows: " + delivery.unresolvedPlaybackCount());
        lines.add("- Recommended next action: " + delivery.recommendedNextAction());
        lines.add("");
        lines.add("## Artifacts");
        if (delivery.artifacts().isEmpty()) {
            lines.add("- No narration delivery artifacts are available.");
        } else {
            for (NarrationDeliveryPackageArtifactVo artifact : delivery.artifacts()) {
                lines.add("- " + artifact.artifactType() + ": " + artifact.filename() + " (" + artifact.contentType() + ") " + artifact.downloadHref());
            }
        }
        lines.add("");
        lines.add("## Checks");
        for (NarrationDeliveryPackageCheckVo check : delivery.checks()) {
            lines.add("- " + check.label() + ": " + check.status() + " - " + check.detail() + " Next: " + check.nextAction());
        }
        lines.add("");
        lines.add("## Safe Links");
        for (NarrationDeliveryPackageLinkVo link : delivery.safeLinks()) {
            lines.add("- " + link.label() + ": " + link.href());
        }
        lines.add("");
        lines.add("## Package Entries");
        for (String entry : delivery.packageEntries()) {
            lines.add("- " + entry);
        }
        lines.add("");
        lines.add("## Safety Notes");
        for (String note : delivery.safetyNotes()) {
            lines.add("- " + note);
        }
        lines.add("");
        return String.join("\n", lines);
    }

    @Override
    public StoredNarrationDeliveryPackageBo openPackage(String jobId) {
        NarrationDeliveryPackageVo delivery = getPackage(jobId);
        byte[] content = zipBytes(jobId, delivery);
        return new StoredNarrationDeliveryPackageBo(
                "linguaframe-job-" + jobId + "-narration-delivery-package.zip",
                "application/zip",
                content.length,
                new ByteArrayInputStream(content)
        );
    }

    private List<NarrationDeliveryPackageArtifactVo> artifacts(String jobId) {
        return artifactService.listArtifacts(jobId).stream()
                .filter(artifact -> artifact.type() == JobArtifactType.NARRATION_AUDIO || artifact.type() == JobArtifactType.NARRATED_VIDEO)
                .sorted(Comparator.comparingInt(artifact -> artifact.type() == JobArtifactType.NARRATION_AUDIO ? 0 : 1))
                .map(artifact -> new NarrationDeliveryPackageArtifactVo(
                        artifact.artifactId(),
                        artifact.type().name(),
                        safe(artifact.filename()),
                        safe(artifact.contentType()),
                        artifact.sizeBytes(),
                        artifact.cacheHit(),
                        "/api/jobs/" + jobId + "/artifacts/" + artifact.artifactId() + "/download"
                ))
                .toList();
    }

    private List<NarrationDeliveryPackageCheckVo> checks(
            boolean audioReady,
            boolean videoReady,
            NarrationEvidenceVo evidence,
            NarrationScriptPackageVo scriptPackage,
            NarrationRenderReviewVo renderReview,
            NarrationPlaybackReviewVo playbackReview,
            NarrationPlaybackReviewResolutionVo resolution,
            NarrationRecoveryHandoffVo handoff
    ) {
        return List.of(
                check("NARRATION_AUDIO", "Narration audio", audioReady ? READY : ATTENTION,
                        audioReady ? "Narration audio artifact is available." : "No narration audio artifact is available.",
                        "Generate narration audio only through the explicit render action.", false),
                check("NARRATED_VIDEO", "Narrated video", videoReady ? READY : ATTENTION,
                        videoReady ? "Narrated video artifact is available." : "No narrated video artifact is available.",
                        "Generate narrated video after narration audio and render review are ready.", false),
                check("NARRATION_EVIDENCE", "Narration evidence", statusFrom(evidence.status()),
                        "Narration evidence status is " + evidence.status() + ".",
                        "Open narration evidence if counts or artifact readiness need review.", true),
                check("NARRATION_SCRIPT_PACKAGE", "Narration script package", statusFrom(scriptPackage.status()),
                        "Narration script package status is " + scriptPackage.status() + ".",
                        "Export or import script package only through the explicit script package workflow.", true),
                check("NARRATION_RENDER_REVIEW", "Narration render review", statusFrom(renderReview.status()),
                        "Narration render review status is " + renderReview.status() + ".",
                        renderReview.nextAction(), true),
                check("NARRATION_PLAYBACK_REVIEW", "Narration playback review", statusFrom(playbackReview.status()),
                        "Narration playback review status is " + playbackReview.status() + ".",
                        playbackReview.nextAction(), true),
                check("NARRATION_PLAYBACK_RESOLUTION", "Narration playback resolution", resolution.unresolvedSegmentCount() > 0 ? BLOCKED : statusFrom(resolution.status()),
                        "Narration playback resolution status is " + resolution.status() + "; unresolved=" + resolution.unresolvedSegmentCount() + ".",
                        resolution.nextAction(), true),
                check("NARRATION_RECOVERY_HANDOFF", "Narration recovery handoff", statusFrom(handoff.status()),
                        "Narration recovery handoff status is " + handoff.status() + ".",
                        handoff.recommendedNextAction(), true)
        );
    }

    private List<NarrationDeliveryPackageCheckVo> summaryChecks(
            boolean audioReady,
            boolean videoReady,
            NarrationEvidenceVo evidence,
            NarrationScriptPackageVo scriptPackage,
            NarrationRenderReviewVo renderReview,
            NarrationPlaybackReviewVo playbackReview,
            NarrationPlaybackReviewResolutionVo resolution
    ) {
        if (evidence.segmentCount() == 0) {
            return List.of(
                    check("NARRATION_DELIVERY_EMPTY", "Narration delivery empty", READY,
                            "No narration rows are saved; narration delivery package does not apply.",
                            "Continue with subtitle or standard demo handoff.", false),
                    check("NARRATION_RECOVERY_HANDOFF", "Narration recovery handoff", "SUMMARY_ONLY",
                            "Recovery handoff is linked but not loaded in final handoff summaries to avoid recursive acceptance-gate aggregation.",
                            "Open narration recovery handoff only when playback resolution is blocked.", false)
            );
        }
        return List.of(
                check("NARRATION_AUDIO", "Narration audio", audioReady ? READY : ATTENTION,
                        audioReady ? "Narration audio artifact is available." : "No narration audio artifact is available.",
                        "Generate narration audio only through the explicit render action.", false),
                check("NARRATED_VIDEO", "Narrated video", videoReady ? READY : ATTENTION,
                        videoReady ? "Narrated video artifact is available." : "No narrated video artifact is available.",
                        "Generate narrated video after narration audio and render review are ready.", false),
                check("NARRATION_EVIDENCE", "Narration evidence", statusFrom(evidence.status()),
                        "Narration evidence status is " + evidence.status() + ".",
                        "Open narration evidence if counts or artifact readiness need review.", true),
                check("NARRATION_SCRIPT_PACKAGE", "Narration script package", statusFrom(scriptPackage.status()),
                        "Narration script package status is " + scriptPackage.status() + ".",
                        "Export or import script package only through the explicit script package workflow.", true),
                check("NARRATION_RENDER_REVIEW", "Narration render review", statusFrom(renderReview.status()),
                        "Narration render review status is " + renderReview.status() + ".",
                        renderReview.nextAction(), true),
                check("NARRATION_PLAYBACK_REVIEW", "Narration playback review", statusFrom(playbackReview.status()),
                        "Narration playback review status is " + playbackReview.status() + ".",
                        playbackReview.nextAction(), true),
                check("NARRATION_PLAYBACK_RESOLUTION", "Narration playback resolution", resolution.unresolvedSegmentCount() > 0 ? BLOCKED : statusFrom(resolution.status()),
                        "Narration playback resolution status is " + resolution.status() + "; unresolved=" + resolution.unresolvedSegmentCount() + ".",
                        resolution.nextAction(), true),
                check("NARRATION_RECOVERY_HANDOFF", "Narration recovery handoff", "SUMMARY_ONLY",
                        "Recovery handoff is linked but not loaded in final handoff summaries to avoid recursive acceptance-gate aggregation.",
                        "Open narration recovery handoff only when playback resolution is blocked.", false)
        );
    }

    private byte[] zipBytes(String jobId, NarrationDeliveryPackageVo delivery) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            writeEntry(zipOutputStream, "manifest.json", manifest(delivery));
            writeEntry(zipOutputStream, "README.md", readme(delivery));
            writeEntry(zipOutputStream, "narration-delivery-package.json", writeJson(delivery));
            writeEntry(zipOutputStream, "narration-delivery-package.md", renderMarkdown(jobId));
            writeEntry(zipOutputStream, "narration-evidence.json", safe(writeJson(evidenceService.getEvidence(jobId))));
            writeEntry(zipOutputStream, "narration-evidence.md", safe(evidenceService.renderMarkdown(jobId)));
            writeEntry(zipOutputStream, "narration-script-package.json", safe(writeJson(scriptPackageService.getPackage(jobId))));
            writeEntry(zipOutputStream, "narration-render-review.json", safe(writeJson(renderReviewService.getReview(jobId))));
            writeEntry(zipOutputStream, "narration-render-review.md", safe(renderReviewService.renderMarkdown(jobId)));
            writeEntry(zipOutputStream, "narration-playback-review.json", safe(writeJson(playbackReviewService.getReview(jobId))));
            writeEntry(zipOutputStream, "narration-playback-review.md", safe(playbackReviewService.renderMarkdown(jobId)));
            writeEntry(zipOutputStream, "narration-playback-resolution.json", safe(writeJson(playbackResolutionService.getResolution(jobId))));
            writeEntry(zipOutputStream, "narration-playback-resolution.md", safe(playbackResolutionService.renderMarkdown(jobId)));
            writeEntry(zipOutputStream, "narration-recovery-handoff.json", safe(writeJson(recoveryHandoffService.get().getHandoff(jobId))));
            writeEntry(zipOutputStream, "narration-recovery-handoff.md", safe(recoveryHandoffService.get().renderMarkdown(jobId)));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build narration delivery package", ex);
        }
        return outputStream.toByteArray();
    }

    private String manifest(NarrationDeliveryPackageVo delivery) {
        return """
                {"jobId":"%s","status":"%s","phase":"%s","audioReady":%s,"videoReady":%s,"unresolvedPlaybackCount":%d,"entryCount":%d,"embedsMediaBytes":false,"includesReviewerNoteBodies":false}
                """.formatted(
                json(delivery.jobId()),
                json(delivery.status()),
                json(delivery.phase()),
                delivery.audioReady(),
                delivery.videoReady(),
                delivery.unresolvedPlaybackCount(),
                delivery.packageEntries().size()
        );
    }

    private String readme(NarrationDeliveryPackageVo delivery) {
        return """
                # LinguaFrame Narration Delivery Package

                Job: %s
                Status: %s

                Open `narration-delivery-package.md` for the delivery checklist. This package is metadata-only. It references generated media through existing download routes and excludes sensitive operational details.
                """.formatted(delivery.jobId(), delivery.status());
    }

    private List<NarrationDeliveryPackageLinkVo> safeLinks(String jobId, List<NarrationDeliveryPackageArtifactVo> artifacts) {
        List<NarrationDeliveryPackageLinkVo> links = new ArrayList<>();
        links.add(link("NARRATION_DELIVERY_PACKAGE", "Narration delivery package", "/api/jobs/" + jobId + "/narration-delivery-package", "application/json", "Narration delivery metadata."));
        links.add(link("NARRATION_DELIVERY_MARKDOWN", "Narration delivery Markdown", "/api/jobs/" + jobId + "/narration-delivery-package/markdown/download", "text/markdown", "Narration delivery checklist."));
        links.add(link("NARRATION_DELIVERY_ZIP", "Narration delivery ZIP", "/api/jobs/" + jobId + "/narration-delivery-package/download", "application/zip", "Offline narration delivery package."));
        links.add(link("NARRATION_EVIDENCE_PACKAGE", "Narration evidence package", "/api/jobs/" + jobId + "/narration-evidence/download", "application/zip", "Safe narration evidence package."));
        links.add(link("NARRATION_SCRIPT_PACKAGE", "Narration script package", "/api/jobs/" + jobId + "/narration-script-package/download", "application/zip", "Explicit reusable narration script package."));
        links.add(link("NARRATION_RECOVERY_HANDOFF", "Narration recovery handoff", "/api/jobs/" + jobId + "/narration-recovery-handoff/download", "application/zip", "Recovery package for unresolved playback rows."));
        for (NarrationDeliveryPackageArtifactVo artifact : artifacts) {
            links.add(link(artifact.artifactType(), artifact.artifactType() + " download", artifact.downloadHref(), artifact.contentType(), "Generated narration media download route."));
        }
        return List.copyOf(links);
    }

    private List<String> packageEntries() {
        return List.of(
                "manifest.json",
                "README.md",
                "narration-delivery-package.json",
                "narration-delivery-package.md",
                "narration-evidence.json",
                "narration-evidence.md",
                "narration-script-package.json",
                "narration-render-review.json",
                "narration-render-review.md",
                "narration-playback-review.json",
                "narration-playback-review.md",
                "narration-playback-resolution.json",
                "narration-playback-resolution.md",
                "narration-recovery-handoff.json",
                "narration-recovery-handoff.md"
        );
    }

    private List<String> safetyNotes() {
        return List.of(
                "Narration delivery package is read-only and metadata-only.",
                "Generated media is referenced by safe download routes; binary media bytes are not embedded.",
                "Sensitive review, storage, provider, local environment, and credential details are excluded.",
                "Use existing explicit render, playback review, and recovery workflows to mutate narration state."
        );
    }

    private String status(
            boolean audioReady,
            boolean videoReady,
            NarrationEvidenceVo evidence,
            NarrationScriptPackageVo scriptPackage,
            NarrationRenderReviewVo renderReview,
            NarrationPlaybackReviewVo playbackReview,
            NarrationPlaybackReviewResolutionVo resolution,
            NarrationRecoveryHandoffVo handoff
    ) {
        if (resolution.unresolvedSegmentCount() > 0 || BLOCKED.equals(handoff.status()) || BLOCKED.equals(evidence.status()) || BLOCKED.equals(scriptPackage.status())) {
            return BLOCKED;
        }
        if (audioReady && videoReady && READY.equals(renderReview.status()) && READY.equals(playbackReview.status()) && READY.equals(resolution.status())) {
            return READY;
        }
        return ATTENTION;
    }

    private String summaryStatus(
            boolean audioReady,
            boolean videoReady,
            NarrationEvidenceVo evidence,
            NarrationScriptPackageVo scriptPackage,
            NarrationRenderReviewVo renderReview,
            NarrationPlaybackReviewVo playbackReview,
            NarrationPlaybackReviewResolutionVo resolution
    ) {
        if (evidence.segmentCount() == 0) {
            return "EMPTY";
        }
        if (resolution.unresolvedSegmentCount() > 0 || BLOCKED.equals(evidence.status()) || BLOCKED.equals(scriptPackage.status())) {
            return BLOCKED;
        }
        if (audioReady && videoReady && READY.equals(renderReview.status()) && READY.equals(playbackReview.status()) && READY.equals(resolution.status())) {
            return READY;
        }
        return ATTENTION;
    }

    private String phase(String status) {
        return switch (status) {
            case READY -> "NARRATION_DELIVERY_READY";
            case BLOCKED -> "NARRATION_DELIVERY_BLOCKED";
            case "EMPTY" -> "NARRATION_DELIVERY_EMPTY";
            default -> "NARRATION_DELIVERY_NEEDS_REVIEW";
        };
    }

    private String nextAction(String status, boolean audioReady, boolean videoReady, NarrationPlaybackReviewResolutionVo resolution) {
        if ("EMPTY".equals(status)) {
            return "No narration rows are saved; continue with standard demo handoff.";
        }
        if (BLOCKED.equals(status)) {
            return "Resolve narration playback rows, save revisions, regenerate narration media, then rerun delivery package.";
        }
        if (!audioReady) {
            return "Generate narration audio through the explicit narration render action.";
        }
        if (!videoReady) {
            return "Generate narrated video after narration audio is ready.";
        }
        if (!READY.equals(resolution.status())) {
            return resolution.nextAction();
        }
        return "Download the narration delivery package and continue with final handoff.";
    }

    private String statusFrom(String status) {
        return switch (safeStatus(status)) {
            case READY -> READY;
            case BLOCKED -> BLOCKED;
            default -> ATTENTION;
        };
    }

    private String safeStatus(String status) {
        return status == null || status.isBlank() ? "UNKNOWN" : status.trim();
    }

    private static NarrationDeliveryPackageCheckVo check(String key, String label, String status, String detail, String nextAction, boolean required) {
        return new NarrationDeliveryPackageCheckVo(key, label, status, safe(detail), safe(nextAction), required);
    }

    private static NarrationDeliveryPackageLinkVo link(String kind, String label, String href, String contentType, String description) {
        return new NarrationDeliveryPackageLinkVo(kind, label, href, contentType, description);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize narration delivery package content", ex);
        }
    }

    private static void writeEntry(ZipOutputStream zipOutputStream, String name, String content) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }

    private static String json(String value) {
        return safe(value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("/Users/", "[redacted]/")
                .replace("source-videos/", "[redacted]/")
                .replace("job-artifacts/", "[redacted]/")
                .replace("objectKey", "[redacted]")
                .replace("provider payload", "[redacted]")
                .replace("Provider request and response payloads", "Provider exchange details")
                .replace("OPENAI_API_KEY", "[redacted]")
                .replace("private-demo-token", "[redacted]")
                .replace("sk-test", "[redacted]");
    }
}
