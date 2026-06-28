package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.DeliveryManifestVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackDownloadVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackVo;
import com.linguaframe.job.domain.vo.DemoShareSheetLinkVo;
import com.linguaframe.job.domain.vo.DemoShareSheetVo;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.service.DeliveryManifestService;
import com.linguaframe.job.service.DemoPresenterPackService;
import com.linguaframe.job.service.DemoShareSheetService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DemoShareSheetServiceImpl implements DemoShareSheetService {

    private static final List<String> CURATED_LINK_KINDS = List.of(
            "DEMO_RUN_PACKAGE",
            "HANDOFF_PACKAGE",
            "EVIDENCE_BUNDLE",
            "QUALITY_EVIDENCE_MARKDOWN",
            "DELIVERY_MANIFEST_MARKDOWN",
            "ARTIFACT_ARCHIVE",
            "SOURCE_MEDIA"
    );

    private final LocalizationJobQueryService queryService;
    private final DeliveryManifestService deliveryManifestService;
    private final DemoPresenterPackService presenterPackService;
    private final Clock clock;

    public DemoShareSheetServiceImpl(
            LocalizationJobQueryService queryService,
            DeliveryManifestService deliveryManifestService,
            DemoPresenterPackService presenterPackService,
            Clock clock
    ) {
        this.queryService = queryService;
        this.deliveryManifestService = deliveryManifestService;
        this.presenterPackService = presenterPackService;
        this.clock = clock;
    }

    @Override
    public DemoShareSheetVo buildShareSheet(String jobId) {
        LocalizationJobVo job = queryService.getJob(jobId);
        DeliveryManifestVo manifest = deliveryManifestService.buildManifest(jobId);
        DemoPresenterPackVo presenterPack = presenterPackService.buildPresenterPack(jobId);
        String readiness = readiness(job, manifest, presenterPack);
        List<String> outcomeBullets = outcomeBullets(job, manifest);
        List<DemoShareSheetLinkVo> links = curatedLinks(presenterPack);
        String summary = summary(job, manifest);
        String recommendedNextAction = recommendedNextAction(readiness);
        String headline = headline(presenterPack.headline(), job);
        Instant generatedAt = Instant.now(clock);
        String markdown = renderMarkdown(
                job.jobId(),
                generatedAt,
                readiness,
                headline,
                summary,
                outcomeBullets,
                recommendedNextAction,
                links
        );
        return new DemoShareSheetVo(
                job.jobId(),
                job.videoId(),
                generatedAt,
                readiness,
                headline,
                summary,
                outcomeBullets,
                recommendedNextAction,
                links,
                markdown
        );
    }

    @Override
    public String buildMarkdownShareSheet(String jobId) {
        return buildShareSheet(jobId).markdown();
    }

    private String readiness(LocalizationJobVo job, DeliveryManifestVo manifest, DemoPresenterPackVo presenterPack) {
        if (job.status() == LocalizationJobStatus.COMPLETED
                && manifest.handoffReady()
                && "READY".equals(presenterPack.readinessStatus())) {
            return "READY";
        }
        return "NEEDS_ATTENTION";
    }

    private String summary(LocalizationJobVo job, DeliveryManifestVo manifest) {
        String quality = qualityScore(job.qualityEvaluation());
        return "%s job for %s is %s with quality %s and handoffReady=%s."
                .formatted(profile(job.demoProfileId()), job.targetLanguage(), job.status(), quality, manifest.handoffReady());
    }

    private List<String> outcomeBullets(LocalizationJobVo job, DeliveryManifestVo manifest) {
        JobUsageSummaryVo usage = job.usageSummary();
        JobCacheSummaryVo cache = job.cacheSummary();
        List<String> bullets = new ArrayList<>();
        bullets.add("Status: " + job.status());
        bullets.add("Quality score: " + qualityScore(job.qualityEvaluation()));
        bullets.add("Model calls: %d, estimated cost: %s USD".formatted(
                usage.modelCallCount(),
                formatMoney(usage.estimatedCostUsd())
        ));
        bullets.add("Generated artifacts: %d, provider cache hits: %d".formatted(
                cache.generatedArtifactCount(),
                cache.providerCacheHitCount()
        ));
        bullets.add("Handoff ready: " + manifest.handoffReady());
        return List.copyOf(bullets);
    }

    private List<DemoShareSheetLinkVo> curatedLinks(DemoPresenterPackVo presenterPack) {
        Map<String, DemoPresenterPackDownloadVo> downloadsByKind = presenterPack.downloads().stream()
                .collect(Collectors.toMap(DemoPresenterPackDownloadVo::kind, Function.identity(), (first, ignored) -> first));
        return CURATED_LINK_KINDS.stream()
                .map(downloadsByKind::get)
                .filter(download -> download != null)
                .map(download -> new DemoShareSheetLinkVo(download.kind(), download.label(), download.url()))
                .toList();
    }

    private String recommendedNextAction(String readiness) {
        if ("READY".equals(readiness)) {
            return "Open the demo run package or reviewed handoff package for reviewer delivery.";
        }
        return "Review diagnostics and failure triage before sharing this run.";
    }

    private String renderMarkdown(
            String jobId,
            Instant generatedAt,
            String readiness,
            String headline,
            String summary,
            List<String> outcomeBullets,
            String recommendedNextAction,
            List<DemoShareSheetLinkVo> links
    ) {
        List<String> lines = new ArrayList<>();
        lines.add("# " + headline);
        lines.add("");
        lines.add("- Job: " + jobId);
        lines.add("- Generated at: " + generatedAt);
        lines.add("- Readiness: " + readiness);
        lines.add("- Recommended next action: " + recommendedNextAction);
        lines.add("");
        lines.add(summary);
        lines.add("");
        lines.add("## Outcome");
        for (String bullet : outcomeBullets) {
            lines.add("- " + bullet);
        }
        lines.add("");
        lines.add("## Safe Links");
        lines.add("- Demo share sheet Markdown: /api/jobs/" + jobId + "/demo-share-sheet/markdown/download");
        for (DemoShareSheetLinkVo link : links) {
            lines.add("- %s: %s".formatted(link.label(), link.url()));
        }
        return String.join("\n", lines) + "\n";
    }

    private String qualityScore(QualityEvaluationVo qualityEvaluation) {
        if (qualityEvaluation == null) {
            return "N/A";
        }
        return "%d (%s)".formatted(qualityEvaluation.score(), qualityEvaluation.verdict());
    }

    private String profile(String profileId) {
        return profileId == null || profileId.isBlank() ? "manual" : profileId;
    }

    private String headline(String presenterHeadline, LocalizationJobVo job) {
        if (presenterHeadline == null || presenterHeadline.isBlank() || presenterHeadline.startsWith("N/A demo")) {
            return "%s demo to %s".formatted(profile(job.demoProfileId()), job.targetLanguage());
        }
        return presenterHeadline;
    }

    private String formatMoney(BigDecimal value) {
        return value == null ? "0" : value.toPlainString();
    }
}
