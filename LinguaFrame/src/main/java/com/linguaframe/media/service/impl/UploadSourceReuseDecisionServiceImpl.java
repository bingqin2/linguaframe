package com.linguaframe.media.service.impl;

import com.linguaframe.media.domain.vo.UploadSourceReuseCandidateVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseDecisionActionVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseDecisionLinkVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseDecisionVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseVo;
import com.linguaframe.media.service.UploadSourceReuseDecisionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class UploadSourceReuseDecisionServiceImpl implements UploadSourceReuseDecisionService {

    @Override
    public UploadSourceReuseDecisionVo decide(UploadSourceReuseVo sourceReuse) {
        UploadSourceReuseVo safeSourceReuse = sourceReuse == null ? UploadSourceReuseVo.empty() : sourceReuse;
        Optional<UploadSourceReuseCandidateVo> recommended = recommendedCandidate(safeSourceReuse);
        String status = status(safeSourceReuse, recommended);
        return new UploadSourceReuseDecisionVo(
                status,
                headline(status, safeSourceReuse),
                summary(status, safeSourceReuse),
                safeSourceReuse.recommendedAction(),
                safeSourceReuse.recommendedExistingJobId(),
                safeSourceReuse.candidateCount(),
                actions(status, recommended),
                links(recommended),
                safetyNotes(status),
                safeSourceReuse
        );
    }

    private Optional<UploadSourceReuseCandidateVo> recommendedCandidate(UploadSourceReuseVo sourceReuse) {
        String jobId = sourceReuse.recommendedExistingJobId();
        if (jobId == null || jobId.isBlank()) {
            return Optional.empty();
        }
        return sourceReuse.candidates().stream()
                .filter(candidate -> jobId.equals(candidate.jobId()))
                .findFirst();
    }

    private String status(UploadSourceReuseVo sourceReuse, Optional<UploadSourceReuseCandidateVo> recommended) {
        return switch (sourceReuse.recommendedAction()) {
            case "REVIEW_EXISTING_COMPLETED_RUN" -> "REUSE_COMPLETED_RUN";
            case "WAIT_FOR_ACTIVE_RUN" -> "WAIT_FOR_ACTIVE_RUN";
            case "UPLOAD_NEW_SOURCE" -> sourceReuse.candidateCount() > 0 ? "REVIEW_DUPLICATES" : "UPLOAD_NEW_SOURCE";
            default -> recommended.isPresent() ? "REVIEW_DUPLICATES" : "UPLOAD_NEW_SOURCE";
        };
    }

    private String headline(String status, UploadSourceReuseVo sourceReuse) {
        return switch (status) {
            case "REUSE_COMPLETED_RUN" -> "Existing completed run found for this source.";
            case "WAIT_FOR_ACTIVE_RUN" -> "An active run is already processing this source.";
            case "REVIEW_DUPLICATES" -> "Duplicate source candidates need review.";
            default -> "No previous source match found.";
        };
    }

    private String summary(String status, UploadSourceReuseVo sourceReuse) {
        return switch (status) {
            case "REUSE_COMPLETED_RUN" -> "Review the completed job evidence before uploading this same source again.";
            case "WAIT_FOR_ACTIVE_RUN" -> "Wait for the active job to finish, then reuse its evidence if it completes successfully.";
            case "REVIEW_DUPLICATES" -> "Review prior same-source jobs before choosing whether to upload another run.";
            default -> "Upload can proceed because no same-owner source fingerprint match was found.";
        };
    }

    private List<UploadSourceReuseDecisionActionVo> actions(
            String status,
            Optional<UploadSourceReuseCandidateVo> recommended
    ) {
        List<UploadSourceReuseDecisionActionVo> actions = new ArrayList<>();
        if ("REUSE_COMPLETED_RUN".equals(status) && recommended.isPresent()) {
            UploadSourceReuseCandidateVo candidate = recommended.get();
            actions.add(action("openJob", "Open existing job", "LINK", true, "Inspect the completed same-source job.", candidate.jobDetailHref()));
            actions.add(action("downloadPackage", "Download demo run package", "DOWNLOAD", true, "Use the existing safe handoff package instead of rerunning.", candidate.demoRunPackageHref()));
            actions.add(action("refreshPlan", "Refresh upload plan", "REFRESH", true, "Re-run this read-only plan before making a final upload decision.", null));
            return List.copyOf(actions);
        }
        if ("WAIT_FOR_ACTIVE_RUN".equals(status) && recommended.isPresent()) {
            UploadSourceReuseCandidateVo candidate = recommended.get();
            actions.add(action("openActiveJob", "Open active job", "LINK", true, "Track the already-running same-source job.", candidate.jobDetailHref()));
            actions.add(action("waitForCompletion", "Wait for completion", "WAIT", false, "Reuse evidence after the active job reaches COMPLETED.", null));
            return List.copyOf(actions);
        }
        if ("REVIEW_DUPLICATES".equals(status)) {
            actions.add(action("reviewCandidates", "Review duplicate candidates", "REVIEW", true, "Compare prior same-source jobs before uploading again.", null));
            actions.add(action("uploadAnyway", "Upload anyway", "UPLOAD", true, "Create a new run intentionally after reviewing candidates.", null));
            return List.copyOf(actions);
        }
        actions.add(action("uploadNewSource", "Continue upload", "UPLOAD", true, "No prior source match was found for this owner.", null));
        return List.copyOf(actions);
    }

    private List<UploadSourceReuseDecisionLinkVo> links(Optional<UploadSourceReuseCandidateVo> recommended) {
        if (recommended.isEmpty()) {
            return List.of();
        }
        UploadSourceReuseCandidateVo candidate = recommended.get();
        List<UploadSourceReuseDecisionLinkVo> links = new ArrayList<>();
        addLink(links, "JOB_DETAIL", "Job detail", candidate.jobDetailHref());
        addLink(links, "SHARE_SHEET", "Demo share sheet", candidate.shareSheetHref());
        addLink(links, "EVIDENCE_MARKDOWN", "Evidence Markdown", candidate.evidenceHref());
        addLink(links, "DEMO_RUN_PACKAGE", "Demo run package", candidate.demoRunPackageHref());
        addLink(links, "ACCEPTANCE_GATE", "Acceptance gate", candidate.acceptanceGateHref());
        return List.copyOf(links);
    }

    private void addLink(List<UploadSourceReuseDecisionLinkVo> links, String kind, String label, String href) {
        if (href != null && !href.isBlank()) {
            links.add(new UploadSourceReuseDecisionLinkVo(kind, label, href));
        }
    }

    private List<String> safetyNotes(String status) {
        List<String> notes = new ArrayList<>();
        notes.add("Source reuse decision is read-only and does not store media or call providers.");
        notes.add("Reuse candidates are scoped to the current owner.");
        if ("UPLOAD_NEW_SOURCE".equals(status)) {
            notes.add("No same-owner fingerprint match was found for this file.");
        } else {
            notes.add("Links expose safe job evidence routes only.");
        }
        return List.copyOf(notes);
    }

    private UploadSourceReuseDecisionActionVo action(
            String id,
            String label,
            String kind,
            boolean enabled,
            String detail,
            String href
    ) {
        return new UploadSourceReuseDecisionActionVo(id, label, kind, enabled, detail, href);
    }
}
