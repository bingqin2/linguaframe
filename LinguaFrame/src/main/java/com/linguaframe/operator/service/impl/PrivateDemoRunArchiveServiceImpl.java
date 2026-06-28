package com.linguaframe.operator.service.impl;

import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryDownloadVo;
import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryJobVo;
import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryVo;
import com.linguaframe.operator.domain.vo.PrivateDemoLaunchRehearsalVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveCandidateVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveLinkVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveVo;
import com.linguaframe.operator.service.PrivateDemoEvidenceGalleryService;
import com.linguaframe.operator.service.PrivateDemoLaunchRehearsalService;
import com.linguaframe.operator.service.PrivateDemoOperationsService;
import com.linguaframe.operator.service.PrivateDemoRunArchiveService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class PrivateDemoRunArchiveServiceImpl implements PrivateDemoRunArchiveService {

    private static final String READY = "READY";
    private static final String ATTENTION = "ATTENTION";
    private static final String BLOCKED = "BLOCKED";
    private static final String MISSING = "MISSING";

    private final PrivateDemoOperationsService operationsService;
    private final PrivateDemoLaunchRehearsalService launchRehearsalService;
    private final PrivateDemoEvidenceGalleryService evidenceGalleryService;

    public PrivateDemoRunArchiveServiceImpl(
            PrivateDemoOperationsService operationsService,
            PrivateDemoLaunchRehearsalService launchRehearsalService,
            PrivateDemoEvidenceGalleryService evidenceGalleryService
    ) {
        this.operationsService = operationsService;
        this.launchRehearsalService = launchRehearsalService;
        this.evidenceGalleryService = evidenceGalleryService;
    }

    @Override
    public PrivateDemoRunArchiveVo runArchive() {
        PrivateDemoOperationsVo operations = operationsService.operations();
        PrivateDemoLaunchRehearsalVo launch = launchRehearsalService.launchRehearsal();
        PrivateDemoEvidenceGalleryVo gallery = evidenceGalleryService.evidenceGallery(20);

        PrivateDemoEvidenceGalleryJobVo recommended = recommendedJob(gallery);
        List<PrivateDemoRunArchiveCandidateVo> candidates = gallery.jobs().stream()
                .map(this::candidate)
                .toList();
        List<PrivateDemoRunArchiveLinkVo> links = archiveLinks(recommended);
        String recommendedReadiness = recommendedReadiness(recommended);
        String overallStatus = overallStatus(operations, launch, gallery, recommended);

        return new PrivateDemoRunArchiveVo(
                Instant.now(),
                overallStatus,
                recommended == null ? null : recommended.jobId(),
                recommended == null ? null : recommended.videoId(),
                recommended == null ? null : safeProfile(recommended.demoProfileId()),
                recommendedReadiness,
                operations.overallStatus(),
                launch.overallStatus(),
                launch.recommendedNextStepId(),
                gallery.completedJobCount(),
                gallery.handoffReadyCount(),
                candidates,
                links,
                notes(overallStatus, operations, launch, gallery, recommended, links)
        );
    }

    private PrivateDemoEvidenceGalleryJobVo recommendedJob(PrivateDemoEvidenceGalleryVo gallery) {
        if (gallery.recommendedJobId() == null) {
            return null;
        }
        return gallery.jobs().stream()
                .filter(job -> job.jobId().equals(gallery.recommendedJobId()))
                .findFirst()
                .orElse(null);
    }

    private PrivateDemoRunArchiveCandidateVo candidate(PrivateDemoEvidenceGalleryJobVo job) {
        return new PrivateDemoRunArchiveCandidateVo(
                job.jobId(),
                job.videoId(),
                job.filename(),
                safeProfile(job.demoProfileId()),
                job.status().name(),
                recommendedReadiness(job),
                job.qualityScore(),
                job.estimatedCostUsd() == null ? BigDecimal.ZERO : job.estimatedCostUsd(),
                job.modelCallCount(),
                job.providerCacheHitCount(),
                job.handoffReady(),
                roles(job)
        );
    }

    private List<String> roles(PrivateDemoEvidenceGalleryJobVo job) {
        List<String> roles = new ArrayList<>();
        if (job.recommended()) {
            roles.add("RECOMMENDED");
        }
        if (job.handoffReady()) {
            roles.add("HANDOFF_READY");
        }
        if (job.presenterPackReady()) {
            roles.add("PRESENTER_PACK_READY");
        }
        return List.copyOf(roles);
    }

    private List<PrivateDemoRunArchiveLinkVo> archiveLinks(PrivateDemoEvidenceGalleryJobVo recommended) {
        List<PrivateDemoRunArchiveLinkVo> links = new ArrayList<>();
        links.add(link("Operations readiness", "/api/operator/private-demo/operations", "application/json",
                "Private demo readiness across runtime, dependencies, provider, cost, storage, retention, and evidence."));
        links.add(link("Launch rehearsal", "/api/operator/private-demo/launch-rehearsal", "application/json",
                "Ordered launch and evidence checklist."));
        links.add(link("Evidence gallery", "/api/operator/private-demo/evidence-gallery", "application/json",
                "Completed-run gallery and recommended handoff candidate."));
        if (recommended != null) {
            links.add(link("Presenter pack", "/api/jobs/" + recommended.jobId() + "/demo-presenter-pack",
                    "application/json", "Presenter-facing recommended evidence pack."));
            links.add(link("Demo run package", "/api/jobs/" + recommended.jobId() + "/demo-run-package/download",
                    "application/zip", "Complete metadata-only demo run package."));
            links.add(link("Handoff package", "/api/jobs/" + recommended.jobId() + "/handoff-package/download",
                    "application/zip", "Reviewed handoff package."));
            links.add(link("AI audit package", "/api/jobs/" + recommended.jobId() + "/ai-audit-package/download",
                    "application/zip", "Prompt, model-call, usage, and cost audit package."));
            links.add(link("Evidence bundle", "/api/jobs/" + recommended.jobId() + "/evidence/bundle/download",
                    "application/zip", "Metadata-only evidence ZIP."));
            links.add(link("Diagnostics", "/api/jobs/" + recommended.jobId() + "/diagnostics",
                    "application/json", "Diagnostics metadata and artifact hashes."));
            links.add(link("Result bundle", "/api/jobs/" + recommended.jobId() + "/artifacts/archive/download",
                    "application/zip", "Generated artifact archive for the recommended job."));
            for (PrivateDemoEvidenceGalleryDownloadVo download : recommended.downloads()) {
                if (links.stream().noneMatch(link -> link.href().equals(download.href()))) {
                    links.add(link(download.label(), download.href(), download.contentType(), download.description()));
                }
            }
        }
        return List.copyOf(links);
    }

    private PrivateDemoRunArchiveLinkVo link(String label, String href, String contentType, String description) {
        return new PrivateDemoRunArchiveLinkVo(label, href, contentType, description);
    }

    private String recommendedReadiness(PrivateDemoEvidenceGalleryJobVo recommended) {
        if (recommended == null) {
            return MISSING;
        }
        return recommended.handoffReady() && recommended.presenterPackReady() ? READY : ATTENTION;
    }

    private String overallStatus(
            PrivateDemoOperationsVo operations,
            PrivateDemoLaunchRehearsalVo launch,
            PrivateDemoEvidenceGalleryVo gallery,
            PrivateDemoEvidenceGalleryJobVo recommended
    ) {
        if (BLOCKED.equals(operations.overallStatus()) || BLOCKED.equals(launch.overallStatus())) {
            return BLOCKED;
        }
        if (recommended == null || gallery.completedJobCount() == 0) {
            return ATTENTION;
        }
        if (ATTENTION.equals(operations.overallStatus())
                || ATTENTION.equals(launch.overallStatus())
                || !READY.equals(recommendedReadiness(recommended))) {
            return ATTENTION;
        }
        return READY;
    }

    private String notes(
            String overallStatus,
            PrivateDemoOperationsVo operations,
            PrivateDemoLaunchRehearsalVo launch,
            PrivateDemoEvidenceGalleryVo gallery,
            PrivateDemoEvidenceGalleryJobVo recommended,
            List<PrivateDemoRunArchiveLinkVo> links
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("# LinguaFrame Private Demo Run Archive\n\n");
        builder.append("- Overall: ").append(overallStatus).append('\n');
        builder.append("- Operations readiness: ").append(operations.overallStatus()).append('\n');
        builder.append("- Launch rehearsal: ").append(launch.overallStatus()).append('\n');
        builder.append("- Launch next step: ").append(launch.recommendedNextStepId()).append('\n');
        builder.append("- Completed jobs: ").append(gallery.completedJobCount()).append('\n');
        builder.append("- Handoff-ready jobs: ").append(gallery.handoffReadyCount()).append('\n');
        if (recommended == null) {
            builder.append("- No completed recommended job is available yet.\n");
        } else {
            builder.append("- Recommended job: ").append(recommended.jobId()).append('\n');
            builder.append("- Recommended profile: ").append(safeProfile(recommended.demoProfileId())).append('\n');
            builder.append("- Recommended readiness: ").append(recommendedReadiness(recommended)).append('\n');
        }
        builder.append("\n## Archive links\n\n");
        for (PrivateDemoRunArchiveLinkVo link : links) {
            builder.append("- ").append(link.label()).append(": ").append(link.href()).append('\n');
        }
        if (recommended != null) {
            builder.append("- Demo run package: /api/jobs/")
                    .append(recommended.jobId())
                    .append("/demo-run-package/download\n");
        }
        builder.append("\nPrivate demo run archive is metadata-only. It is not a backup, not a generated media package, and not a substitute for per-job demo run packages.\n");
        return builder.toString();
    }

    private String safeProfile(String profileId) {
        return profileId == null || profileId.isBlank() ? "manual" : profileId;
    }
}
