package com.linguaframe.media.service.impl;

import com.linguaframe.common.quota.OwnerQuotaPreflightService;
import com.linguaframe.common.quota.OwnerQuotaPreflightVo;
import com.linguaframe.media.domain.bo.UploadCostEstimateOptionsBo;
import com.linguaframe.media.domain.vo.DemoUploadReadinessCheckVo;
import com.linguaframe.media.domain.vo.DemoUploadReadinessVo;
import com.linguaframe.media.domain.vo.UploadDecisionPackageVo;
import com.linguaframe.media.domain.vo.UploadExecutionPlanCommandVo;
import com.linguaframe.media.domain.vo.UploadExecutionPlanVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseDecisionLinkVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseDecisionVo;
import com.linguaframe.media.service.DemoUploadReadinessService;
import com.linguaframe.media.service.UploadDecisionPackageService;
import com.linguaframe.media.service.UploadExecutionPlanReportService;
import com.linguaframe.media.service.UploadExecutionPlanService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class UploadDecisionPackageServiceImpl implements UploadDecisionPackageService {

    private static final String STATUS_BLOCKED = "BLOCKED";
    private static final String DECISION_BLOCKED = "BLOCKED";
    private static final String DECISION_REUSE_COMPLETED_RUN = "REUSE_COMPLETED_RUN";
    private static final String DECISION_WAIT_FOR_ACTIVE_RUN = "WAIT_FOR_ACTIVE_RUN";
    private static final String DECISION_UPLOAD_NEW_SOURCE = "UPLOAD_NEW_SOURCE";

    private final UploadExecutionPlanService uploadExecutionPlanService;
    private final OwnerQuotaPreflightService ownerQuotaPreflightService;
    private final DemoUploadReadinessService demoUploadReadinessService;
    private final UploadExecutionPlanReportService uploadExecutionPlanReportService;

    public UploadDecisionPackageServiceImpl(
            UploadExecutionPlanService uploadExecutionPlanService,
            OwnerQuotaPreflightService ownerQuotaPreflightService,
            DemoUploadReadinessService demoUploadReadinessService,
            UploadExecutionPlanReportService uploadExecutionPlanReportService
    ) {
        this.uploadExecutionPlanService = uploadExecutionPlanService;
        this.ownerQuotaPreflightService = ownerQuotaPreflightService;
        this.demoUploadReadinessService = demoUploadReadinessService;
        this.uploadExecutionPlanReportService = uploadExecutionPlanReportService;
    }

    @Override
    public UploadDecisionPackageVo build(MultipartFile file, UploadCostEstimateOptionsBo options) {
        UploadCostEstimateOptionsBo safeOptions = options == null ? UploadCostEstimateOptionsBo.empty() : options;
        UploadExecutionPlanVo plan = uploadExecutionPlanService.plan(file, safeOptions);
        OwnerQuotaPreflightVo ownerQuota = ownerQuotaPreflightService.getPreflight();
        DemoUploadReadinessVo readiness = demoUploadReadinessService.getReadiness(plan.demoProfileId());
        String executionPlanMarkdown = uploadExecutionPlanReportService.renderMarkdown(plan);
        String decision = recommendedDecision(plan, ownerQuota, readiness);
        return new UploadDecisionPackageVo(
                Instant.now(),
                overallStatus(plan, ownerQuota, readiness),
                decision,
                recommendedNextAction(decision, plan),
                plan,
                ownerQuota,
                readiness,
                executionPlanMarkdown,
                safetyNotes(plan, readiness)
        );
    }

    @Override
    public String renderMarkdown(UploadDecisionPackageVo value) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Upload Decision Package\n\n");
        markdown.append("## Summary\n\n");
        markdown.append("- Generated at: ").append(value.generatedAt()).append('\n');
        markdown.append("- Overall status: ").append(safe(value.overallStatus())).append('\n');
        markdown.append("- Recommended decision: ").append(safe(value.recommendedDecision())).append('\n');
        markdown.append("- Recommended next action: ").append(safe(value.recommendedNextAction())).append('\n');
        markdown.append("- Filename: ").append(safe(value.executionPlan().filename())).append('\n');
        markdown.append("- Target language: ").append(safe(value.executionPlan().targetLanguage())).append("\n\n");

        markdown.append("## Owner Quota\n\n");
        markdown.append("- Owner: ").append(safe(value.ownerQuotaPreflight().ownerId())).append('\n');
        markdown.append("- Enabled: ").append(value.ownerQuotaPreflight().enabled()).append('\n');
        markdown.append("- Allowed: ").append(value.ownerQuotaPreflight().allowed()).append('\n');
        markdown.append("- Active jobs: ").append(value.ownerQuotaPreflight().activeJobs()).append('\n');
        markdown.append("- Queued jobs: ").append(value.ownerQuotaPreflight().queuedJobs()).append('\n');
        if (value.ownerQuotaPreflight().blockingReasons().isEmpty()) {
            markdown.append("- Blocking reasons: none\n\n");
        } else {
            for (String reason : value.ownerQuotaPreflight().blockingReasons()) {
                markdown.append("- Blocking reason: ").append(safe(reason)).append('\n');
            }
            markdown.append('\n');
        }

        markdown.append("## Upload Readiness\n\n");
        markdown.append("- Status: ").append(safe(value.uploadReadiness().overallStatus())).append('\n');
        markdown.append("- Demo profile: ").append(safe(value.uploadReadiness().demoProfileId())).append('\n');
        if (value.uploadReadiness().requiredActions().isEmpty()) {
            markdown.append("- Required action: none\n");
        } else {
            for (String action : value.uploadReadiness().requiredActions()) {
                markdown.append("- Required action: ").append(safe(action)).append('\n');
            }
        }
        for (DemoUploadReadinessCheckVo check : value.uploadReadiness().checks()) {
            markdown.append("- ").append(safe(check.label())).append(": ")
                    .append(safe(check.status())).append(" - ")
                    .append(safe(check.detail())).append('\n');
        }
        markdown.append('\n');

        markdown.append("## Execution Plan Summary\n\n");
        markdown.append("- Status: ").append(safe(value.executionPlan().overallStatus())).append('\n');
        markdown.append("- Validation: ").append(value.executionPlan().valid()).append(" / ")
                .append(safe(value.executionPlan().validationCode())).append('\n');
        markdown.append("- Estimated cost: $").append(value.executionPlan().estimatedCostUsd()).append('\n');
        markdown.append("- Estimated duration seconds: ")
                .append(value.executionPlan().estimatedDurationSecondsLower()).append(" - ")
                .append(value.executionPlan().estimatedDurationSecondsUpper()).append("\n\n");

        markdown.append("## Source Reuse Decision\n\n");
        UploadSourceReuseDecisionVo sourceDecision = value.executionPlan().sourceReuseDecision();
        if (sourceDecision == null) {
            markdown.append("- Status: none\n\n");
        } else {
            markdown.append("- Status: ").append(safe(sourceDecision.status())).append('\n');
            markdown.append("- Headline: ").append(safe(sourceDecision.headline())).append('\n');
            markdown.append("- Recommended existing job: ").append(safe(sourceDecision.recommendedExistingJobId())).append('\n');
            markdown.append("- Candidate count: ").append(sourceDecision.candidateCount()).append('\n');
            for (UploadSourceReuseDecisionLinkVo link : sourceDecision.links()) {
                markdown.append("- ").append(safe(link.label())).append(": ").append(safe(link.href())).append('\n');
            }
            markdown.append('\n');
        }

        markdown.append("## Commands\n\n");
        for (UploadExecutionPlanCommandVo command : value.executionPlan().commands()) {
            markdown.append("- ").append(safe(command.label())).append(": `")
                    .append(safe(command.command())).append("` - ")
                    .append(safe(command.description())).append('\n');
        }
        if (value.executionPlan().commands().isEmpty()) {
            markdown.append("- No commands reported.\n");
        }
        markdown.append('\n');

        markdown.append("## Package Contents\n\n");
        markdown.append("- `manifest.json`: Safe aggregate metadata for this decision package.\n");
        markdown.append("- `upload-decision-package.md`: This package-level Markdown summary.\n");
        markdown.append("- `upload-execution-plan.md`: Full safe execution-plan Markdown report.\n\n");

        markdown.append("## Safety Notes\n\n");
        for (String note : value.safetyNotes()) {
            markdown.append("- ").append(safe(note)).append('\n');
        }
        return markdown.toString();
    }

    private String recommendedDecision(
            UploadExecutionPlanVo plan,
            OwnerQuotaPreflightVo ownerQuota,
            DemoUploadReadinessVo readiness
    ) {
        if (STATUS_BLOCKED.equals(plan.overallStatus())
                || !ownerQuota.allowed()
                || STATUS_BLOCKED.equals(readiness.overallStatus())) {
            return DECISION_BLOCKED;
        }
        UploadSourceReuseDecisionVo decision = plan.sourceReuseDecision();
        if (decision == null) {
            return DECISION_UPLOAD_NEW_SOURCE;
        }
        return switch (decision.status()) {
            case DECISION_REUSE_COMPLETED_RUN -> DECISION_REUSE_COMPLETED_RUN;
            case DECISION_WAIT_FOR_ACTIVE_RUN -> DECISION_WAIT_FOR_ACTIVE_RUN;
            default -> DECISION_UPLOAD_NEW_SOURCE;
        };
    }

    private String overallStatus(
            UploadExecutionPlanVo plan,
            OwnerQuotaPreflightVo ownerQuota,
            DemoUploadReadinessVo readiness
    ) {
        if (STATUS_BLOCKED.equals(plan.overallStatus())
                || !ownerQuota.allowed()
                || STATUS_BLOCKED.equals(readiness.overallStatus())) {
            return STATUS_BLOCKED;
        }
        return plan.overallStatus();
    }

    private String recommendedNextAction(String decision, UploadExecutionPlanVo plan) {
        return switch (decision) {
            case DECISION_BLOCKED -> "Resolve blocking upload gates before storing media.";
            case DECISION_REUSE_COMPLETED_RUN -> "Review the existing completed run package before uploading the same source again.";
            case DECISION_WAIT_FOR_ACTIVE_RUN -> "Wait for the active same-source run to finish before starting another upload.";
            default -> plan.recommendedNextAction();
        };
    }

    private List<String> safetyNotes(UploadExecutionPlanVo plan, DemoUploadReadinessVo readiness) {
        List<String> notes = new ArrayList<>();
        notes.add("Decision package is read-only and does not store media or call providers.");
        notes.add("Decision package excludes media bytes, object keys, local paths, transcripts, subtitles, provider payloads, API keys, tokens, and credentials.");
        notes.addAll(plan.safetyNotes());
        notes.addAll(readiness.evidenceRoutes().stream()
                .map(route -> "Safe readiness evidence route: " + route)
                .toList());
        return notes;
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        return value.replace("\r", " ").replace("\n", " ").trim();
    }
}
