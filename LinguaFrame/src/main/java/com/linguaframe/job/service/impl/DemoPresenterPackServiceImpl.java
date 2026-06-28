package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.DeliveryManifestVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackDownloadVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackRunVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackVo;
import com.linguaframe.job.domain.vo.DemoRunMatrixJobVo;
import com.linguaframe.job.domain.vo.DemoRunMatrixVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.service.DeliveryManifestService;
import com.linguaframe.job.service.DemoPresenterPackService;
import com.linguaframe.job.service.DemoRunMatrixService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class DemoPresenterPackServiceImpl implements DemoPresenterPackService {

    private static final int MATRIX_LIMIT = 8;

    private final LocalizationJobQueryService queryService;
    private final DemoRunMatrixService matrixService;
    private final DeliveryManifestService deliveryManifestService;
    private final Clock clock;

    public DemoPresenterPackServiceImpl(
            LocalizationJobQueryService queryService,
            DemoRunMatrixService matrixService,
            DeliveryManifestService deliveryManifestService,
            Clock clock
    ) {
        this.queryService = queryService;
        this.matrixService = matrixService;
        this.deliveryManifestService = deliveryManifestService;
        this.clock = clock;
    }

    @Override
    public DemoPresenterPackVo buildPresenterPack(String jobId) {
        LocalizationJobVo anchor = queryService.getJob(jobId);
        DemoRunMatrixVo matrix = matrixService.buildMatrix(jobId, MATRIX_LIMIT);
        DeliveryManifestVo manifest = deliveryManifestService.buildManifest(jobId);
        String readinessStatus = anchor.status() == LocalizationJobStatus.COMPLETED && manifest.handoffReady()
                ? "READY"
                : "NEEDS_ATTENTION";
        List<DemoPresenterPackRunVo> runs = matrix.jobs().stream()
                .map(job -> toRun(job, matrix))
                .toList();
        List<DemoPresenterPackDownloadVo> downloads = downloads(jobId, anchor.videoId());
        String headline = "%s demo to %s".formatted(display(anchor.demoProfileId()), anchor.targetLanguage());
        String notes = presenterNotes(anchor, matrix, readinessStatus, runs, downloads);
        return new DemoPresenterPackVo(
                jobId,
                anchor.videoId(),
                Instant.now(clock),
                headline,
                readinessStatus,
                matrix.recommendedBaselineJobId(),
                matrix.bestQualityJobId(),
                matrix.lowestCostJobId(),
                runs,
                downloads,
                notes
        );
    }

    private DemoPresenterPackRunVo toRun(DemoRunMatrixJobVo job, DemoRunMatrixVo matrix) {
        return new DemoPresenterPackRunVo(
                job.jobId(),
                job.demoProfileId(),
                job.status(),
                job.completedAt(),
                job.qualityScore(),
                job.estimatedCostUsd(),
                job.modelCallCount(),
                job.providerCacheHitCount(),
                job.handoffReady(),
                roles(job.jobId(), matrix)
        );
    }

    private List<String> roles(String jobId, DemoRunMatrixVo matrix) {
        List<String> roles = new ArrayList<>();
        if (jobId.equals(matrix.anchorJobId())) {
            roles.add("ANCHOR");
        }
        if (jobId.equals(matrix.recommendedBaselineJobId())) {
            roles.add("RECOMMENDED_BASELINE");
        }
        if (jobId.equals(matrix.bestQualityJobId())) {
            roles.add("BEST_QUALITY");
        }
        if (jobId.equals(matrix.lowestCostJobId())) {
            roles.add("LOWEST_COST");
        }
        return List.copyOf(roles);
    }

    private List<DemoPresenterPackDownloadVo> downloads(String jobId, String videoId) {
        List<DemoPresenterPackDownloadVo> downloads = new ArrayList<>();
        downloads.add(download("DIAGNOSTICS_JSON", "Diagnostics JSON", "/api/jobs/%s/diagnostics/download".formatted(jobId)));
        downloads.add(download("EVIDENCE_MARKDOWN", "Backend evidence Markdown", "/api/jobs/%s/evidence/markdown/download".formatted(jobId)));
        downloads.add(download("EVIDENCE_BUNDLE", "Evidence bundle", "/api/jobs/%s/evidence/bundle/download".formatted(jobId)));
        downloads.add(download("QUALITY_EVIDENCE_MARKDOWN", "Quality evidence Markdown", "/api/jobs/%s/quality-evaluation/evidence/markdown/download".formatted(jobId)));
        downloads.add(download("DELIVERY_MANIFEST_MARKDOWN", "Delivery manifest Markdown", "/api/jobs/%s/delivery-manifest/markdown/download".formatted(jobId)));
        downloads.add(download("HANDOFF_PACKAGE", "Reviewed handoff package", "/api/jobs/%s/handoff-package/download".formatted(jobId)));
        downloads.add(download("DEMO_RUN_PACKAGE", "Demo run package", "/api/jobs/%s/demo-run-package/download".formatted(jobId)));
        downloads.add(download("AI_AUDIT_PACKAGE", "AI audit package", "/api/jobs/%s/ai-audit-package/download".formatted(jobId)));
        downloads.add(download("ARTIFACT_ARCHIVE", "Artifact archive", "/api/jobs/%s/artifacts/archive/download".formatted(jobId)));
        if (videoId != null && !videoId.isBlank()) {
            downloads.add(download("SOURCE_MEDIA", "Source media", "/api/media/uploads/%s/source/download".formatted(videoId)));
        }
        return List.copyOf(downloads);
    }

    private DemoPresenterPackDownloadVo download(String kind, String label, String url) {
        return new DemoPresenterPackDownloadVo(kind, label, url);
    }

    private String presenterNotes(
            LocalizationJobVo anchor,
            DemoRunMatrixVo matrix,
            String readinessStatus,
            List<DemoPresenterPackRunVo> runs,
            List<DemoPresenterPackDownloadVo> downloads
    ) {
        List<String> lines = new ArrayList<>();
        lines.add("# LinguaFrame Demo Presenter Pack");
        lines.add("");
        lines.add("- Anchor job: " + anchor.jobId());
        lines.add("- Source video: " + anchor.videoId());
        lines.add("- Target language: " + anchor.targetLanguage());
        lines.add("- Demo profile: " + display(anchor.demoProfileId()));
        lines.add("- Readiness: " + readinessStatus);
        lines.add("- Recommended baseline: " + display(matrix.recommendedBaselineJobId()));
        lines.add("- Best quality: " + display(matrix.bestQualityJobId()));
        lines.add("- Lowest cost: " + display(matrix.lowestCostJobId()));
        lines.add("");
        lines.add("## Recommended Runs");
        for (DemoPresenterPackRunVo run : runs) {
            lines.add("- %s [%s] status=%s quality=%s cost=%s modelCalls=%d providerCacheHits=%d handoffReady=%s".formatted(
                    run.jobId(),
                    String.join(",", run.roles()),
                    run.status(),
                    display(run.qualityScore()),
                    display(run.estimatedCostUsd()),
                    run.modelCallCount(),
                    run.providerCacheHitCount(),
                    run.handoffReady()
            ));
        }
        lines.add("");
        lines.add("## Safe Downloads");
        for (DemoPresenterPackDownloadVo download : downloads) {
            lines.add("- %s: %s".formatted(download.label(), download.url()));
        }
        return String.join("\n", lines) + "\n";
    }

    private static String display(Object value) {
        return value == null ? "N/A" : String.valueOf(value);
    }
}
