package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.DeliveryManifestVo;
import com.linguaframe.job.domain.vo.DemoCompletionCertificateCheckVo;
import com.linguaframe.job.domain.vo.DemoCompletionCertificateLinkVo;
import com.linguaframe.job.domain.vo.DemoCompletionCertificateSectionVo;
import com.linguaframe.job.domain.vo.DemoCompletionCertificateVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackDownloadVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackVo;
import com.linguaframe.job.domain.vo.DemoReplayCardCommandVo;
import com.linguaframe.job.domain.vo.DemoReplayCardLinkVo;
import com.linguaframe.job.domain.vo.DemoReplayCardVo;
import com.linguaframe.job.domain.vo.DemoRunMatrixVo;
import com.linguaframe.job.domain.vo.DemoRunSnapshotVo;
import com.linguaframe.job.domain.vo.DemoShareSheetLinkVo;
import com.linguaframe.job.domain.vo.DemoShareSheetVo;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.service.DeliveryManifestService;
import com.linguaframe.job.service.DemoCompletionCertificateService;
import com.linguaframe.job.service.DemoPresenterPackService;
import com.linguaframe.job.service.DemoReplayCardService;
import com.linguaframe.job.service.DemoRunMatrixService;
import com.linguaframe.job.service.DemoRunSnapshotService;
import com.linguaframe.job.service.DemoShareSheetService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DemoCompletionCertificateServiceImpl implements DemoCompletionCertificateService {

    private static final int MATRIX_LIMIT = 8;

    private final LocalizationJobQueryService queryService;
    private final DeliveryManifestService deliveryManifestService;
    private final DemoPresenterPackService presenterPackService;
    private final DemoReplayCardService replayCardService;
    private final DemoShareSheetService shareSheetService;
    private final DemoRunSnapshotService snapshotService;
    private final DemoRunMatrixService matrixService;
    private final Clock clock;

    public DemoCompletionCertificateServiceImpl(
            LocalizationJobQueryService queryService,
            DeliveryManifestService deliveryManifestService,
            DemoPresenterPackService presenterPackService,
            DemoReplayCardService replayCardService,
            DemoShareSheetService shareSheetService,
            DemoRunSnapshotService snapshotService,
            DemoRunMatrixService matrixService,
            Clock clock
    ) {
        this.queryService = queryService;
        this.deliveryManifestService = deliveryManifestService;
        this.presenterPackService = presenterPackService;
        this.replayCardService = replayCardService;
        this.shareSheetService = shareSheetService;
        this.snapshotService = snapshotService;
        this.matrixService = matrixService;
        this.clock = clock;
    }

    @Override
    public DemoCompletionCertificateVo buildCertificate(String jobId) {
        LocalizationJobVo job = queryService.getJob(jobId);
        DeliveryManifestVo manifest = deliveryManifestService.buildManifest(jobId);
        DemoPresenterPackVo presenterPack = presenterPackService.buildPresenterPack(jobId);
        DemoReplayCardVo replayCard = replayCardService.buildReplayCard(jobId);
        DemoShareSheetVo shareSheet = shareSheetService.buildShareSheet(jobId);
        DemoRunSnapshotVo snapshot = snapshotService.buildSnapshot(jobId);
        DemoRunMatrixVo matrix = matrixService.buildMatrix(jobId, MATRIX_LIMIT);

        List<DemoCompletionCertificateCheckVo> checks = checks(job, manifest, presenterPack, replayCard, shareSheet, snapshot, matrix);
        String status = certificateStatus(checks);
        return new DemoCompletionCertificateVo(
                job.jobId(),
                job.videoId(),
                Instant.now(clock),
                status,
                job.status(),
                job.targetLanguage(),
                job.demoProfileId(),
                headline(job, status),
                summary(job, status, manifest, replayCard),
                recommendedNextAction(status),
                matrix.recommendedBaselineJobId(),
                matrix.bestQualityJobId(),
                matrix.lowestCostJobId(),
                checks,
                sections(job, manifest, presenterPack, replayCard, shareSheet, snapshot, matrix),
                links(job.jobId(), presenterPack, replayCard, shareSheet, snapshot),
                safetyNotes(job)
        );
    }

    private List<DemoCompletionCertificateCheckVo> checks(
            LocalizationJobVo job,
            DeliveryManifestVo manifest,
            DemoPresenterPackVo presenterPack,
            DemoReplayCardVo replayCard,
            DemoShareSheetVo shareSheet,
            DemoRunSnapshotVo snapshot,
            DemoRunMatrixVo matrix
    ) {
        List<DemoCompletionCertificateCheckVo> checks = new ArrayList<>();
        checks.add(check(
                "JOB_COMPLETED",
                "Job completed",
                job.status() == LocalizationJobStatus.COMPLETED ? "PASS" : "FAIL",
                "Job status is " + job.status() + ".",
                job.status() != LocalizationJobStatus.COMPLETED
        ));
        checks.add(check(
                "HANDOFF_READY",
                "Reviewed handoff ready",
                manifest.handoffReady() ? "PASS" : "WARN",
                "Delivery manifest handoffReady=" + manifest.handoffReady() + ".",
                false
        ));
        checks.add(check(
                "PRESENTER_PACK_READY",
                "Presenter pack ready",
                "READY".equals(presenterPack.readinessStatus()) ? "PASS" : "WARN",
                "Presenter pack readiness is " + presenterPack.readinessStatus() + ".",
                false
        ));
        checks.add(check(
                "REPLAY_READY",
                "Replay card ready",
                "READY".equals(replayCard.readiness()) ? "PASS" : "WARN",
                "Replay card readiness is " + replayCard.readiness() + ".",
                false
        ));
        checks.add(check(
                "SHARE_READY",
                "Share sheet ready",
                "READY".equals(shareSheet.readiness()) ? "PASS" : "WARN",
                "Share sheet readiness is " + shareSheet.readiness() + ".",
                false
        ));
        checks.add(check(
                "SNAPSHOT_READY",
                "Static snapshot ready",
                "READY".equals(snapshot.readiness()) ? "PASS" : "WARN",
                "Snapshot readiness is " + snapshot.readiness() + " with " + snapshot.packageEntries().size() + " package entries.",
                false
        ));
        checks.add(check(
                "SAME_SOURCE_MATRIX",
                "Same-source run matrix available",
                matrix.jobs().isEmpty() ? "WARN" : "PASS",
                "Run matrix contains " + matrix.jobs().size() + " same-source row(s).",
                false
        ));
        return List.copyOf(checks);
    }

    private String certificateStatus(List<DemoCompletionCertificateCheckVo> checks) {
        boolean blocked = checks.stream().anyMatch(check -> check.blocking() && "FAIL".equals(check.status()));
        if (blocked) {
            return "BLOCKED";
        }
        boolean warning = checks.stream().anyMatch(check -> !"PASS".equals(check.status()));
        return warning ? "NEEDS_ATTENTION" : "READY";
    }

    private List<DemoCompletionCertificateSectionVo> sections(
            LocalizationJobVo job,
            DeliveryManifestVo manifest,
            DemoPresenterPackVo presenterPack,
            DemoReplayCardVo replayCard,
            DemoShareSheetVo shareSheet,
            DemoRunSnapshotVo snapshot,
            DemoRunMatrixVo matrix
    ) {
        JobUsageSummaryVo usage = job.usageSummary();
        JobCacheSummaryVo cache = job.cacheSummary();
        return List.of(
                section("RUN_IDENTITY", "Run identity", "READY", List.of(
                        "Job: " + job.jobId(),
                        "Video: " + job.videoId(),
                        "Target language: " + job.targetLanguage(),
                        "Demo profile: " + profile(job.demoProfileId())
                )),
                section("DELIVERY", "Delivery readiness", manifest.handoffReady() ? "READY" : "NEEDS_ATTENTION", List.of(
                        "Handoff ready: " + manifest.handoffReady(),
                        "Presenter pack: " + presenterPack.readinessStatus(),
                        "Share sheet: " + shareSheet.readiness(),
                        "Snapshot: " + snapshot.readiness()
                )),
                section("REPRODUCIBILITY", "Reproducibility", replayCard.commands().isEmpty() ? "NEEDS_ATTENTION" : replayCard.readiness(), replayFacts(replayCard)),
                section("EVIDENCE", "Evidence packages", presenterPack.downloads().isEmpty() ? "NEEDS_ATTENTION" : "READY", evidenceFacts(presenterPack, snapshot)),
                section("COST_CACHE", "Cost and cache", "READY", List.of(
                        "Model calls: " + safeInt(usage == null ? null : usage.modelCallCount()),
                        "Estimated cost USD: " + money(usage == null ? null : usage.estimatedCostUsd()),
                        "Artifact cache hits: " + safeInt(cache == null ? null : cache.cacheHitCount()),
                        "Provider cache hits: " + safeInt(cache == null ? null : cache.providerCacheHitCount()),
                        "Run matrix rows: " + matrix.jobs().size()
                ))
        );
    }

    private List<String> replayFacts(DemoReplayCardVo replayCard) {
        List<String> facts = new ArrayList<>();
        facts.add("Replay readiness: " + replayCard.readiness());
        facts.add("Recommended baseline: " + display(replayCard.recommendedBaselineJobId()));
        facts.add("Best quality: " + display(replayCard.bestQualityJobId()));
        facts.add("Lowest cost: " + display(replayCard.lowestCostJobId()));
        for (DemoReplayCardCommandVo command : replayCard.commands()) {
            facts.add(command.label() + ": " + command.command());
        }
        return List.copyOf(facts);
    }

    private List<String> evidenceFacts(DemoPresenterPackVo presenterPack, DemoRunSnapshotVo snapshot) {
        List<String> facts = new ArrayList<>();
        facts.add("Presenter downloads: " + presenterPack.downloads().size());
        facts.add("Snapshot entries: " + snapshot.packageEntries().size());
        for (DemoPresenterPackDownloadVo download : presenterPack.downloads()) {
            facts.add(download.label() + ": " + download.url());
        }
        return List.copyOf(facts);
    }

    private List<DemoCompletionCertificateLinkVo> links(
            String jobId,
            DemoPresenterPackVo presenterPack,
            DemoReplayCardVo replayCard,
            DemoShareSheetVo shareSheet,
            DemoRunSnapshotVo snapshot
    ) {
        Map<String, DemoCompletionCertificateLinkVo> links = new LinkedHashMap<>();
        add(links, link("CERTIFICATE_JSON", "Completion certificate JSON", "/api/jobs/%s/demo-completion-certificate".formatted(jobId)));
        add(links, link("REPLAY_CARD_JSON", "Replay card JSON", "/api/jobs/%s/demo-replay-card".formatted(jobId)));
        add(links, link("SHARE_SHEET_JSON", "Share sheet JSON", "/api/jobs/%s/demo-share-sheet".formatted(jobId)));
        add(links, link("SNAPSHOT_ZIP", "Static snapshot ZIP", "/api/jobs/%s/demo-run-snapshot/download".formatted(jobId)));
        for (DemoPresenterPackDownloadVo download : presenterPack.downloads()) {
            add(links, link(download.kind(), download.label(), download.url()));
        }
        for (DemoReplayCardLinkVo replayLink : replayCard.links()) {
            add(links, link(replayLink.kind(), replayLink.label(), replayLink.url()));
        }
        for (DemoShareSheetLinkVo shareLink : shareSheet.links()) {
            add(links, link(shareLink.kind(), shareLink.label(), shareLink.url()));
        }
        for (var snapshotLink : snapshot.links()) {
            add(links, link(snapshotLink.kind(), snapshotLink.label(), snapshotLink.url()));
        }
        return List.copyOf(links.values());
    }

    private void add(Map<String, DemoCompletionCertificateLinkVo> links, DemoCompletionCertificateLinkVo link) {
        links.putIfAbsent(link.kind() + ":" + link.url(), link);
    }

    private List<String> safetyNotes(LocalizationJobVo job) {
        List<String> notes = new ArrayList<>();
        notes.add("Metadata-only certificate: only IDs, status, readiness, costs, counts, safe routes, and replay commands are included.");
        notes.add("The certificate is generated on demand from existing safe evidence routes and does not create new artifacts.");
        if (job.status() != LocalizationJobStatus.COMPLETED) {
            notes.add("The selected job is not completed, so this certificate cannot be used as final demo proof.");
        }
        return List.copyOf(notes);
    }

    private String headline(LocalizationJobVo job, String status) {
        return "%s completion certificate for %s (%s)".formatted(profile(job.demoProfileId()), job.targetLanguage(), status);
    }

    private String summary(LocalizationJobVo job, String status, DeliveryManifestVo manifest, DemoReplayCardVo replayCard) {
        return "Job %s is %s with certificateStatus=%s, handoffReady=%s, replayReadiness=%s."
                .formatted(job.jobId(), job.status(), status, manifest.handoffReady(), replayCard.readiness());
    }

    private String recommendedNextAction(String status) {
        return switch (status) {
            case "READY" -> "Use the completion certificate, demo run package, and snapshot as final demo handoff evidence.";
            case "BLOCKED" -> "Wait for the job to complete or inspect failure triage before presenting this run.";
            default -> "Review warning checks before sharing this run as final evidence.";
        };
    }

    private DemoCompletionCertificateCheckVo check(String key, String label, String status, String detail, boolean blocking) {
        return new DemoCompletionCertificateCheckVo(key, label, status, detail, blocking);
    }

    private DemoCompletionCertificateSectionVo section(String key, String title, String status, List<String> facts) {
        return new DemoCompletionCertificateSectionVo(key, title, status, facts);
    }

    private DemoCompletionCertificateLinkVo link(String kind, String label, String url) {
        return new DemoCompletionCertificateLinkVo(kind, label, url);
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

    private String safeInt(Integer value) {
        return String.valueOf(value == null ? 0 : value);
    }
}
