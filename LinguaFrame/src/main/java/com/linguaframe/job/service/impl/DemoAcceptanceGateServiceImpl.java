package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.DeliveryManifestVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateCheckVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateEvidenceVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateLinkVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateVo;
import com.linguaframe.job.domain.vo.DemoCompletionCertificateVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackDownloadVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackVo;
import com.linguaframe.job.domain.vo.DemoReplayCardVo;
import com.linguaframe.job.domain.vo.DemoRunMatrixVo;
import com.linguaframe.job.domain.vo.DemoRunSnapshotVo;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.service.DeliveryManifestService;
import com.linguaframe.job.service.DemoAcceptanceGateService;
import com.linguaframe.job.service.DemoCompletionCertificateService;
import com.linguaframe.job.service.DemoPresenterPackService;
import com.linguaframe.job.service.DemoReplayCardService;
import com.linguaframe.job.service.DemoRunMatrixService;
import com.linguaframe.job.service.DemoRunSnapshotService;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.media.domain.vo.MediaUploadDetailVo;
import com.linguaframe.media.service.MediaUploadService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DemoAcceptanceGateServiceImpl implements DemoAcceptanceGateService {

    private static final int QUALITY_ATTENTION_THRESHOLD = 80;
    private static final BigDecimal COST_ATTENTION_THRESHOLD_USD = new BigDecimal("1.00");
    private static final Set<JobArtifactType> SUBTITLE_TYPES = Set.of(
            JobArtifactType.TARGET_SUBTITLE_JSON,
            JobArtifactType.TARGET_SUBTITLE_SRT,
            JobArtifactType.TARGET_SUBTITLE_VTT,
            JobArtifactType.REVIEWED_SUBTITLE_JSON,
            JobArtifactType.REVIEWED_SUBTITLE_SRT,
            JobArtifactType.REVIEWED_SUBTITLE_VTT
    );
    private static final Set<JobArtifactType> MEDIA_OUTPUT_TYPES = Set.of(
            JobArtifactType.DUBBING_AUDIO,
            JobArtifactType.BURNED_VIDEO,
            JobArtifactType.DUBBED_VIDEO,
            JobArtifactType.REVIEWED_BURNED_VIDEO
    );
    private static final Set<JobArtifactType> REVIEWED_SUBTITLE_TYPES = Set.of(
            JobArtifactType.REVIEWED_SUBTITLE_JSON,
            JobArtifactType.REVIEWED_SUBTITLE_SRT,
            JobArtifactType.REVIEWED_SUBTITLE_VTT
    );

    private final LocalizationJobQueryService queryService;
    private final MediaUploadService mediaUploadService;
    private final JobArtifactService artifactService;
    private final DeliveryManifestService deliveryManifestService;
    private final DemoCompletionCertificateService completionCertificateService;
    private final DemoPresenterPackService presenterPackService;
    private final DemoReplayCardService replayCardService;
    private final DemoRunSnapshotService snapshotService;
    private final DemoRunMatrixService matrixService;
    private final Clock clock;

    public DemoAcceptanceGateServiceImpl(
            LocalizationJobQueryService queryService,
            MediaUploadService mediaUploadService,
            JobArtifactService artifactService,
            DeliveryManifestService deliveryManifestService,
            DemoCompletionCertificateService completionCertificateService,
            DemoPresenterPackService presenterPackService,
            DemoReplayCardService replayCardService,
            DemoRunSnapshotService snapshotService,
            DemoRunMatrixService matrixService,
            Clock clock
    ) {
        this.queryService = queryService;
        this.mediaUploadService = mediaUploadService;
        this.artifactService = artifactService;
        this.deliveryManifestService = deliveryManifestService;
        this.completionCertificateService = completionCertificateService;
        this.presenterPackService = presenterPackService;
        this.replayCardService = replayCardService;
        this.snapshotService = snapshotService;
        this.matrixService = matrixService;
        this.clock = clock;
    }

    @Override
    public DemoAcceptanceGateVo buildGate(String jobId) {
        LocalizationJobVo job = queryService.getJob(jobId);
        MediaUploadDetailVo source = mediaUploadService.getUpload(job.videoId());
        List<JobArtifactVo> artifacts = artifactService.listArtifacts(jobId);
        DeliveryManifestVo manifest = deliveryManifestService.buildManifest(jobId);
        DemoCompletionCertificateVo certificate = completionCertificateService.buildCertificate(jobId);
        DemoPresenterPackVo presenterPack = presenterPackService.buildPresenterPack(jobId);
        DemoReplayCardVo replayCard = replayCardService.buildReplayCard(jobId);
        DemoRunSnapshotVo snapshot = snapshotService.buildSnapshot(jobId);
        DemoRunMatrixVo matrix = matrixService.buildMatrix(jobId, 8);

        List<DemoAcceptanceGateCheckVo> checks = checks(job, source, artifacts, manifest, certificate, presenterPack, replayCard, snapshot, matrix);
        String status = status(checks);

        return new DemoAcceptanceGateVo(
                job.jobId(),
                job.videoId(),
                Instant.now(clock),
                status,
                job.status(),
                job.targetLanguage(),
                job.demoProfileId(),
                headline(job, status),
                summary(job, status, checks),
                recommendedNextAction(status),
                checks,
                evidence(job, source, artifacts, manifest, certificate, presenterPack, replayCard, snapshot, matrix),
                links(jobId, certificate, presenterPack, replayCard, snapshot),
                safetyNotes(job)
        );
    }

    private List<DemoAcceptanceGateCheckVo> checks(
            LocalizationJobVo job,
            MediaUploadDetailVo source,
            List<JobArtifactVo> artifacts,
            DeliveryManifestVo manifest,
            DemoCompletionCertificateVo certificate,
            DemoPresenterPackVo presenterPack,
            DemoReplayCardVo replayCard,
            DemoRunSnapshotVo snapshot,
            DemoRunMatrixVo matrix
    ) {
        List<DemoAcceptanceGateCheckVo> checks = new ArrayList<>();
        checks.add(check("JOB_COMPLETED", "Job completed", job.status() == LocalizationJobStatus.COMPLETED ? "PASS" : "FAIL",
                "Job status is " + job.status() + ".", true));
        checks.add(check("SOURCE_MEDIA_AVAILABLE", "Source media metadata available", source == null ? "FAIL" : "PASS",
                source == null ? "Source media metadata is missing." : "Source media metadata is available for " + source.videoId() + ".", true));

        long subtitleCount = artifacts.stream().filter(artifact -> SUBTITLE_TYPES.contains(artifact.type())).count();
        checks.add(check("SUBTITLE_OUTPUT_AVAILABLE", "Subtitle output available", subtitleCount > 0 ? "PASS" : "FAIL",
                "Subtitle artifact count is " + subtitleCount + ".", true));

        long mediaOutputCount = artifacts.stream().filter(artifact -> MEDIA_OUTPUT_TYPES.contains(artifact.type())).count();
        checks.add(check("MEDIA_OUTPUT_AVAILABLE", "Playable media output available", mediaOutputCount > 0 ? "PASS" : "FAIL",
                "Playable/downloadable media output count is " + mediaOutputCount + ".", true));

        QualityEvaluationVo quality = job.qualityEvaluation();
        boolean qualityReady = quality != null && !"FAIL".equalsIgnoreCase(quality.verdict());
        checks.add(check("QUALITY_EVALUATION_READY", "Quality evaluation ready", qualityReady ? "PASS" : "FAIL",
                quality == null ? "Quality evaluation is missing." : "Quality verdict is " + quality.verdict() + " with score " + quality.score() + ".", true));

        checks.add(check("COMPLETION_CERTIFICATE_READY", "Completion certificate ready",
                "READY".equals(certificate.certificateStatus()) ? "PASS" : "FAIL",
                "Completion certificate status is " + certificate.certificateStatus() + ".", true));

        checks.add(check("CORE_EVIDENCE_LINKS_READY", "Core evidence links ready",
                hasCoreEvidenceLinks(certificate, presenterPack, replayCard, snapshot) ? "PASS" : "FAIL",
                "Demo run package, presenter pack, replay card, and snapshot routes are checked.", true));

        long reviewedSubtitleCount = artifacts.stream().filter(artifact -> REVIEWED_SUBTITLE_TYPES.contains(artifact.type())).count();
        checks.add(check("REVIEWED_HANDOFF_AVAILABLE", "Reviewed subtitle handoff available",
                reviewedSubtitleCount > 0 || manifest.handoffReady() ? "PASS" : "WARN",
                "Reviewed subtitle artifact count is " + reviewedSubtitleCount + "; handoffReady=" + manifest.handoffReady() + ".", false));

        checks.add(check("BASELINE_RECOMMENDED", "Same-source baseline recommended",
                matrix.recommendedBaselineJobId() == null ? "WARN" : "PASS",
                "Recommended baseline job id is " + display(matrix.recommendedBaselineJobId()) + ".", false));

        checks.add(check("QUALITY_SCORE_DEMO_READY", "Quality score demo-ready",
                quality == null || quality.score() < QUALITY_ATTENTION_THRESHOLD ? "WARN" : "PASS",
                quality == null ? "Quality score is unavailable." : "Quality score is " + quality.score() + ".", false));

        BigDecimal cost = job.usageSummary() == null ? BigDecimal.ZERO : job.usageSummary().estimatedCostUsd();
        checks.add(check("COST_WITHIN_DEMO_EXPECTATION", "Cost within demo expectation",
                cost != null && cost.compareTo(COST_ATTENTION_THRESHOLD_USD) > 0 ? "WARN" : "PASS",
                "Estimated cost USD is " + money(cost) + ".", false));

        return List.copyOf(checks);
    }

    private boolean hasCoreEvidenceLinks(
            DemoCompletionCertificateVo certificate,
            DemoPresenterPackVo presenterPack,
            DemoReplayCardVo replayCard,
            DemoRunSnapshotVo snapshot
    ) {
        return certificate.links().stream().anyMatch(link -> "DEMO_RUN_PACKAGE".equals(link.kind()))
                && !presenterPack.downloads().isEmpty()
                && replayCard.links().stream().anyMatch(link -> "REPLAY_CARD_JSON".equals(link.kind()))
                && snapshot.links().stream().anyMatch(link -> link.kind().contains("SNAPSHOT"));
    }

    private String status(List<DemoAcceptanceGateCheckVo> checks) {
        boolean blocked = checks.stream().anyMatch(check -> check.required() && "FAIL".equals(check.status()));
        if (blocked) {
            return "BLOCKED";
        }
        boolean attention = checks.stream().anyMatch(check -> !"PASS".equals(check.status()));
        return attention ? "ATTENTION" : "READY";
    }

    private List<DemoAcceptanceGateEvidenceVo> evidence(
            LocalizationJobVo job,
            MediaUploadDetailVo source,
            List<JobArtifactVo> artifacts,
            DeliveryManifestVo manifest,
            DemoCompletionCertificateVo certificate,
            DemoPresenterPackVo presenterPack,
            DemoReplayCardVo replayCard,
            DemoRunSnapshotVo snapshot,
            DemoRunMatrixVo matrix
    ) {
        long subtitleCount = artifacts.stream().filter(artifact -> SUBTITLE_TYPES.contains(artifact.type())).count();
        long mediaOutputCount = artifacts.stream().filter(artifact -> MEDIA_OUTPUT_TYPES.contains(artifact.type())).count();
        long reviewedCount = artifacts.stream().filter(artifact -> REVIEWED_SUBTITLE_TYPES.contains(artifact.type())).count();
        QualityEvaluationVo quality = job.qualityEvaluation();

        return List.of(
                evidence("SOURCE_DURATION", "Source duration seconds", source == null ? "N/A" : display(source.durationSeconds()), source == null ? "BLOCKED" : "READY"),
                evidence("SUBTITLE_OUTPUT_COUNT", "Subtitle outputs", String.valueOf(subtitleCount), subtitleCount > 0 ? "READY" : "BLOCKED"),
                evidence("MEDIA_OUTPUT_COUNT", "Playable media outputs", String.valueOf(mediaOutputCount), mediaOutputCount > 0 ? "READY" : "BLOCKED"),
                evidence("REVIEWED_SUBTITLE_COUNT", "Reviewed subtitle outputs", String.valueOf(reviewedCount), reviewedCount > 0 || manifest.handoffReady() ? "READY" : "ATTENTION"),
                evidence("QUALITY_SCORE", "Quality score", quality == null ? "N/A" : String.valueOf(quality.score()), quality != null && quality.score() >= QUALITY_ATTENTION_THRESHOLD ? "READY" : "ATTENTION"),
                evidence("MODEL_CALL_COUNT", "Model calls", job.usageSummary() == null ? "0" : String.valueOf(job.usageSummary().modelCallCount()), "READY"),
                evidence("ESTIMATED_COST_USD", "Estimated cost USD", job.usageSummary() == null ? "0" : money(job.usageSummary().estimatedCostUsd()), "READY"),
                evidence("PROVIDER_CACHE_HITS", "Provider cache hits", job.cacheSummary() == null ? "0" : String.valueOf(job.cacheSummary().providerCacheHitCount()), job.cacheSummary() != null && job.cacheSummary().providerCacheHitCount() > 0 ? "READY" : "ATTENTION"),
                evidence("CERTIFICATE_STATUS", "Completion certificate", certificate.certificateStatus(), "READY".equals(certificate.certificateStatus()) ? "READY" : "BLOCKED"),
                evidence("PRESENTER_PACK_STATUS", "Presenter pack", presenterPack.readinessStatus(), "READY".equals(presenterPack.readinessStatus()) ? "READY" : "ATTENTION"),
                evidence("REPLAY_CARD_STATUS", "Replay card", replayCard.readiness(), "READY".equals(replayCard.readiness()) ? "READY" : "ATTENTION"),
                evidence("SNAPSHOT_STATUS", "Static snapshot", snapshot.readiness(), "READY".equals(snapshot.readiness()) ? "READY" : "ATTENTION"),
                evidence("SAME_SOURCE_RUN_COUNT", "Same-source runs", String.valueOf(matrix.jobs().size()), matrix.jobs().isEmpty() ? "ATTENTION" : "READY")
        );
    }

    private List<DemoAcceptanceGateLinkVo> links(
            String jobId,
            DemoCompletionCertificateVo certificate,
            DemoPresenterPackVo presenterPack,
            DemoReplayCardVo replayCard,
            DemoRunSnapshotVo snapshot
    ) {
        Map<String, DemoAcceptanceGateLinkVo> links = new LinkedHashMap<>();
        add(links, link("ACCEPTANCE_GATE_JSON", "Demo acceptance gate JSON", "/api/jobs/%s/demo-acceptance-gate".formatted(jobId)));
        add(links, link("COMPLETION_CERTIFICATE_JSON", "Completion certificate JSON", "/api/jobs/%s/demo-completion-certificate".formatted(jobId)));
        add(links, link("DEMO_RUN_PACKAGE", "Demo run package", "/api/jobs/%s/demo-run-package/download".formatted(jobId)));
        add(links, link("DEMO_RUN_SNAPSHOT", "Static snapshot ZIP", "/api/jobs/%s/demo-run-snapshot/download".formatted(jobId)));
        add(links, link("DEMO_REPLAY_CARD", "Demo replay card", "/api/jobs/%s/demo-replay-card".formatted(jobId)));
        add(links, link("DEMO_PRESENTER_PACK", "Demo presenter pack", "/api/jobs/%s/demo-presenter-pack".formatted(jobId)));
        for (var certificateLink : certificate.links()) {
            add(links, link(certificateLink.kind(), certificateLink.label(), certificateLink.url()));
        }
        for (DemoPresenterPackDownloadVo download : presenterPack.downloads()) {
            add(links, link(download.kind(), download.label(), download.url()));
        }
        for (var replayLink : replayCard.links()) {
            add(links, link(replayLink.kind(), replayLink.label(), replayLink.url()));
        }
        for (var snapshotLink : snapshot.links()) {
            add(links, link(snapshotLink.kind(), snapshotLink.label(), snapshotLink.url()));
        }
        return List.copyOf(links.values());
    }

    private void add(Map<String, DemoAcceptanceGateLinkVo> links, DemoAcceptanceGateLinkVo link) {
        links.putIfAbsent(link.kind() + ":" + link.url(), link);
    }

    private List<String> safetyNotes(LocalizationJobVo job) {
        List<String> notes = new ArrayList<>();
        notes.add("Metadata-only gate: only IDs, status, counts, scores, costs, safe routes, and readiness labels are included.");
        notes.add("The gate is generated on demand from existing safe evidence surfaces and does not create artifacts or call providers.");
        if (job.status() != LocalizationJobStatus.COMPLETED) {
            notes.add("Incomplete jobs are blocked from demo acceptance until processing reaches COMPLETED.");
        }
        return List.copyOf(notes);
    }

    private String headline(LocalizationJobVo job, String status) {
        return "%s acceptance gate for %s (%s)".formatted(profile(job.demoProfileId()), job.targetLanguage(), status);
    }

    private String summary(LocalizationJobVo job, String status, List<DemoAcceptanceGateCheckVo> checks) {
        long failCount = checks.stream().filter(check -> "FAIL".equals(check.status())).count();
        long warnCount = checks.stream().filter(check -> "WARN".equals(check.status())).count();
        return "Job %s is %s with gateStatus=%s, failedChecks=%d, warningChecks=%d."
                .formatted(job.jobId(), job.status(), status, failCount, warnCount);
    }

    private String recommendedNextAction(String status) {
        return switch (status) {
            case "READY" -> "Present this run using the completion certificate, demo run package, and snapshot.";
            case "BLOCKED" -> "Resolve failed required checks before using this run for the demo.";
            default -> "Review warning checks, then decide whether the run is acceptable for a live demo.";
        };
    }

    private DemoAcceptanceGateCheckVo check(String key, String label, String status, String detail, boolean required) {
        return new DemoAcceptanceGateCheckVo(key, label, status, detail, required);
    }

    private DemoAcceptanceGateEvidenceVo evidence(String key, String label, String value, String status) {
        return new DemoAcceptanceGateEvidenceVo(key, label, value, status);
    }

    private DemoAcceptanceGateLinkVo link(String kind, String label, String url) {
        return new DemoAcceptanceGateLinkVo(kind, label, url);
    }

    private String profile(String profileId) {
        return profileId == null || profileId.isBlank() ? "manual" : profileId;
    }

    private String display(Object value) {
        return value == null ? "N/A" : String.valueOf(value);
    }

    private String money(BigDecimal value) {
        return value == null ? "0" : value.toPlainString();
    }
}
