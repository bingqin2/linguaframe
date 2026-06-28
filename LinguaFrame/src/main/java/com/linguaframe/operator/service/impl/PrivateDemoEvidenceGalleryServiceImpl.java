package com.linguaframe.operator.service.impl;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.DeliveryManifestVo;
import com.linguaframe.job.domain.vo.LocalizationJobSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.service.DeliveryManifestService;
import com.linguaframe.job.service.DemoPresenterPackService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryDownloadVo;
import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryJobVo;
import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryVo;
import com.linguaframe.operator.service.PrivateDemoEvidenceGalleryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class PrivateDemoEvidenceGalleryServiceImpl implements PrivateDemoEvidenceGalleryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final String READY = "READY";
    private static final String ATTENTION = "ATTENTION";
    private static final String EMPTY = "EMPTY";

    private final LocalizationJobQueryService queryService;
    private final DeliveryManifestService deliveryManifestService;
    private final DemoPresenterPackService presenterPackService;

    public PrivateDemoEvidenceGalleryServiceImpl(
            LocalizationJobQueryService queryService,
            DeliveryManifestService deliveryManifestService,
            DemoPresenterPackService presenterPackService
    ) {
        this.queryService = queryService;
        this.deliveryManifestService = deliveryManifestService;
        this.presenterPackService = presenterPackService;
    }

    @Override
    public PrivateDemoEvidenceGalleryVo evidenceGallery(Integer limit) {
        List<LocalizationJobSummaryVo> summaries = queryService
                .listJobs(LocalizationJobStatus.COMPLETED, normalizeLimit(limit), 0)
                .jobs();
        List<PrivateDemoEvidenceGalleryJobVo> rows = summaries.stream()
                .map(this::toRow)
                .toList();

        String recommendedJobId = selectRecommendedJobId(rows);
        List<PrivateDemoEvidenceGalleryJobVo> markedRows = rows.stream()
                .map(row -> withRecommended(row, row.jobId().equals(recommendedJobId)))
                .toList();
        int handoffReadyCount = (int) markedRows.stream()
                .filter(PrivateDemoEvidenceGalleryJobVo::handoffReady)
                .count();
        String overallStatus = markedRows.isEmpty() ? EMPTY : handoffReadyCount > 0 ? READY : ATTENTION;
        List<PrivateDemoEvidenceGalleryDownloadVo> galleryDownloads = markedRows.stream()
                .filter(PrivateDemoEvidenceGalleryJobVo::recommended)
                .findFirst()
                .map(PrivateDemoEvidenceGalleryJobVo::downloads)
                .orElse(List.of());

        return new PrivateDemoEvidenceGalleryVo(
                Instant.now(),
                overallStatus,
                markedRows.size(),
                handoffReadyCount,
                recommendedJobId,
                markedRows,
                galleryDownloads,
                notes(overallStatus, recommendedJobId, markedRows)
        );
    }

    private PrivateDemoEvidenceGalleryJobVo toRow(LocalizationJobSummaryVo summary) {
        LocalizationJobVo detail = queryService.getJob(summary.jobId());
        boolean handoffReady = isHandoffReady(summary.jobId());
        boolean presenterPackReady = isPresenterPackReady(summary.jobId());
        List<String> attentionReasons = new ArrayList<>();
        if (!handoffReady) {
            attentionReasons.add("Delivery handoff is not ready.");
        }
        if (!presenterPackReady) {
            attentionReasons.add("Presenter pack is not ready.");
        }
        Integer qualityScore = detail.qualityEvaluation() == null ? null : detail.qualityEvaluation().score();
        String qualityVerdict = detail.qualityEvaluation() == null ? null : detail.qualityEvaluation().verdict();
        BigDecimal estimatedCost = detail.usageSummary() == null
                ? summary.estimatedCostUsd()
                : detail.usageSummary().estimatedCostUsd();
        int modelCallCount = detail.usageSummary() == null ? 0 : detail.usageSummary().modelCallCount();
        int providerCacheHitCount = detail.cacheSummary() == null ? 0 : detail.cacheSummary().providerCacheHitCount();
        return new PrivateDemoEvidenceGalleryJobVo(
                summary.jobId(),
                summary.videoId(),
                summary.filename(),
                summary.targetLanguage(),
                summary.demoProfileId(),
                summary.status(),
                summary.createdAt(),
                summary.completedAt(),
                qualityScore,
                qualityVerdict,
                estimatedCost == null ? BigDecimal.ZERO : estimatedCost,
                modelCallCount,
                providerCacheHitCount,
                handoffReady,
                presenterPackReady,
                false,
                List.copyOf(attentionReasons),
                downloads(summary.jobId(), summary.videoId())
        );
    }

    private boolean isHandoffReady(String jobId) {
        try {
            DeliveryManifestVo manifest = deliveryManifestService.buildManifest(jobId);
            return manifest.handoffReady();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean isPresenterPackReady(String jobId) {
        try {
            presenterPackService.buildPresenterPack(jobId);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String selectRecommendedJobId(List<PrivateDemoEvidenceGalleryJobVo> rows) {
        return rows.stream()
                .filter(PrivateDemoEvidenceGalleryJobVo::handoffReady)
                .max(recommendationComparator())
                .or(() -> rows.stream().max(Comparator.comparing(PrivateDemoEvidenceGalleryJobVo::completedAt)))
                .map(PrivateDemoEvidenceGalleryJobVo::jobId)
                .orElse(null);
    }

    private Comparator<PrivateDemoEvidenceGalleryJobVo> recommendationComparator() {
        return Comparator
                .comparing((PrivateDemoEvidenceGalleryJobVo row) -> row.qualityScore() == null ? -1 : row.qualityScore())
                .thenComparing(row -> row.estimatedCostUsd() == null ? BigDecimal.ZERO : row.estimatedCostUsd(), Comparator.reverseOrder())
                .thenComparing(PrivateDemoEvidenceGalleryJobVo::completedAt);
    }

    private PrivateDemoEvidenceGalleryJobVo withRecommended(
            PrivateDemoEvidenceGalleryJobVo row,
            boolean recommended
    ) {
        return new PrivateDemoEvidenceGalleryJobVo(
                row.jobId(),
                row.videoId(),
                row.filename(),
                row.targetLanguage(),
                row.demoProfileId(),
                row.status(),
                row.createdAt(),
                row.completedAt(),
                row.qualityScore(),
                row.qualityVerdict(),
                row.estimatedCostUsd(),
                row.modelCallCount(),
                row.providerCacheHitCount(),
                row.handoffReady(),
                row.presenterPackReady(),
                recommended,
                row.attentionReasons(),
                row.downloads()
        );
    }

    private List<PrivateDemoEvidenceGalleryDownloadVo> downloads(String jobId, String videoId) {
        return List.of(
                download("Job detail", "/api/jobs/" + jobId, "application/json", "Safe job detail metadata."),
                download("Diagnostics", "/api/jobs/" + jobId + "/diagnostics", "application/json", "Diagnostics metadata and artifact hashes."),
                download("Evidence Markdown", "/api/jobs/" + jobId + "/evidence/markdown/download", "text/markdown", "Reviewer-facing evidence report."),
                download("Evidence bundle", "/api/jobs/" + jobId + "/evidence/bundle/download", "application/zip", "Metadata-only evidence ZIP."),
                download("Quality evidence", "/api/jobs/" + jobId + "/quality-evaluation/evidence/markdown/download", "text/markdown", "Quality evaluation evidence."),
                download("Delivery manifest", "/api/jobs/" + jobId + "/delivery-manifest/markdown/download", "text/markdown", "Delivery handoff manifest."),
                download("Handoff package", "/api/jobs/" + jobId + "/handoff-package/download", "application/zip", "Safe reviewer handoff ZIP."),
                download("Demo run package", "/api/jobs/" + jobId + "/demo-run-package/download", "application/zip", "Complete safe demo run package."),
                download("AI audit package", "/api/jobs/" + jobId + "/ai-audit-package/download", "application/zip", "Prompt, model-call, usage, and cost audit package."),
                download("Presenter pack", "/api/jobs/" + jobId + "/demo-presenter-pack", "application/json", "Presenter-facing recommended evidence pack."),
                download("Source media", "/api/videos/" + videoId + "/source", "video/mp4", "Controlled source media download route.")
        );
    }

    private PrivateDemoEvidenceGalleryDownloadVo download(
            String label,
            String href,
            String contentType,
            String description
    ) {
        return new PrivateDemoEvidenceGalleryDownloadVo(label, href, contentType, description);
    }

    private String notes(String overallStatus, String recommendedJobId, List<PrivateDemoEvidenceGalleryJobVo> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append("# LinguaFrame Private Demo Evidence Gallery\n\n");
        builder.append("- Overall status: ").append(overallStatus).append('\n');
        builder.append("- Completed jobs: ").append(rows.size()).append('\n');
        if (recommendedJobId == null) {
            builder.append("- No completed demo jobs are available yet.\n");
        } else {
            builder.append("- Recommended job: ").append(recommendedJobId).append('\n');
        }
        builder.append('\n');
        for (PrivateDemoEvidenceGalleryJobVo row : rows) {
            builder.append("## ").append(row.jobId());
            if (row.recommended()) {
                builder.append(" (recommended)");
            }
            builder.append('\n');
            builder.append("- File: ").append(row.filename()).append('\n');
            builder.append("- Demo profile: ").append(row.demoProfileId() == null ? "manual" : row.demoProfileId()).append('\n');
            builder.append("- Handoff ready: ").append(row.handoffReady()).append('\n');
            builder.append("- Quality score: ").append(row.qualityScore() == null ? "not available" : row.qualityScore()).append('\n');
            builder.append("- Demo run package: /api/jobs/").append(row.jobId()).append("/demo-run-package/download\n\n");
        }
        return builder.toString();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
