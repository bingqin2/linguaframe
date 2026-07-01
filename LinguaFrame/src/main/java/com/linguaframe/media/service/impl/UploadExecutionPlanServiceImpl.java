package com.linguaframe.media.service.impl;

import com.linguaframe.common.quota.OwnerQuotaPreflightService;
import com.linguaframe.common.quota.OwnerQuotaPreflightVo;
import com.linguaframe.media.domain.bo.UploadCostEstimateOptionsBo;
import com.linguaframe.media.domain.vo.DemoUploadReadinessCheckVo;
import com.linguaframe.media.domain.vo.DemoUploadReadinessVo;
import com.linguaframe.media.domain.vo.UploadCostEstimateBudgetVo;
import com.linguaframe.media.domain.vo.UploadCostEstimateStageVo;
import com.linguaframe.media.domain.vo.UploadCostEstimateVo;
import com.linguaframe.media.domain.vo.UploadExecutionPlanCommandVo;
import com.linguaframe.media.domain.vo.UploadExecutionPlanGateVo;
import com.linguaframe.media.domain.vo.UploadExecutionPlanStageVo;
import com.linguaframe.media.domain.vo.UploadExecutionPlanVo;
import com.linguaframe.media.domain.vo.UploadNarrationScriptIntakeVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseDecisionVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseVo;
import com.linguaframe.media.service.DemoUploadReadinessService;
import com.linguaframe.media.service.UploadCostEstimateService;
import com.linguaframe.media.service.UploadExecutionPlanService;
import com.linguaframe.media.service.UploadSourceReuseDecisionService;
import com.linguaframe.media.service.UploadSourceReuseService;
import com.linguaframe.job.service.NarrationVoiceCatalogService;
import com.linguaframe.job.service.impl.NarrationQuickScriptParser;
import com.linguaframe.job.service.impl.NarrationVoiceCatalogServiceImpl;
import com.linguaframe.common.config.LinguaFrameProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class UploadExecutionPlanServiceImpl implements UploadExecutionPlanService {

    private static final String STATUS_READY = "READY";
    private static final String STATUS_ATTENTION = "ATTENTION";
    private static final String STATUS_BLOCKED = "BLOCKED";

    private final UploadCostEstimateService costEstimateService;
    private final DemoUploadReadinessService demoUploadReadinessService;
    private final OwnerQuotaPreflightService ownerQuotaPreflightService;
    private final UploadSourceReuseService uploadSourceReuseService;
    private final UploadSourceReuseDecisionService uploadSourceReuseDecisionService;
    private final NarrationVoiceCatalogService narrationVoiceCatalogService;
    private final NarrationQuickScriptParser narrationQuickScriptParser = new NarrationQuickScriptParser();

    @Autowired
    public UploadExecutionPlanServiceImpl(
            UploadCostEstimateService costEstimateService,
            DemoUploadReadinessService demoUploadReadinessService,
            OwnerQuotaPreflightService ownerQuotaPreflightService,
            UploadSourceReuseService uploadSourceReuseService,
            UploadSourceReuseDecisionService uploadSourceReuseDecisionService,
            NarrationVoiceCatalogService narrationVoiceCatalogService
    ) {
        this.costEstimateService = costEstimateService;
        this.demoUploadReadinessService = demoUploadReadinessService;
        this.ownerQuotaPreflightService = ownerQuotaPreflightService;
        this.uploadSourceReuseService = uploadSourceReuseService;
        this.uploadSourceReuseDecisionService = uploadSourceReuseDecisionService;
        this.narrationVoiceCatalogService = narrationVoiceCatalogService;
    }

    public UploadExecutionPlanServiceImpl(
            UploadCostEstimateService costEstimateService,
            DemoUploadReadinessService demoUploadReadinessService,
            OwnerQuotaPreflightService ownerQuotaPreflightService,
            UploadSourceReuseService uploadSourceReuseService,
            UploadSourceReuseDecisionService uploadSourceReuseDecisionService
    ) {
        this(costEstimateService, demoUploadReadinessService, ownerQuotaPreflightService, uploadSourceReuseService,
                uploadSourceReuseDecisionService, new NarrationVoiceCatalogServiceImpl(new LinguaFrameProperties()));
    }

    @Override
    public UploadExecutionPlanVo plan(MultipartFile file, UploadCostEstimateOptionsBo options) {
        UploadCostEstimateOptionsBo safeOptions = options == null ? UploadCostEstimateOptionsBo.empty() : options;
        UploadCostEstimateVo estimate = costEstimateService.estimate(file, safeOptions);
        DemoUploadReadinessVo readiness = demoUploadReadinessService.getReadiness(estimate.demoProfileId());
        OwnerQuotaPreflightVo ownerQuota = ownerQuotaPreflightService.getPreflight();
        List<UploadExecutionPlanStageVo> stages = estimate.valid() ? stages(estimate) : List.of();
        UploadNarrationScriptIntakeVo narrationScriptIntake = narrationScriptIntake(safeOptions.narrationScript());
        List<UploadExecutionPlanGateVo> gates = gates(estimate, readiness, ownerQuota, narrationScriptIntake);
        int lower = estimateDurationLower(estimate, stages);
        int upper = estimateDurationUpper(estimate, stages);
        String status = overallStatus(estimate, readiness, ownerQuota, gates);
        UploadSourceReuseVo sourceReuse = uploadSourceReuseService.evaluate(file, estimate, safeOptions);
        UploadSourceReuseDecisionVo sourceReuseDecision = uploadSourceReuseDecisionService.decide(sourceReuse);

        return new UploadExecutionPlanVo(
                status,
                recommendedNextAction(status, estimate),
                estimate.filename(),
                estimate.contentType(),
                estimate.fileSizeBytes(),
                estimate.maxFileSizeBytes(),
                estimate.durationSeconds(),
                estimate.maxDurationSeconds(),
                estimate.valid(),
                estimate.validationCode(),
                estimate.validationMessage(),
                estimate.targetLanguage(),
                estimate.ttsVoice(),
                estimate.translationStyle(),
                estimate.subtitleStylePreset(),
                estimate.translationGlossaryEntryCount(),
                estimate.translationGlossaryHash(),
                estimate.subtitlePolishingMode(),
                estimate.demoProfileId(),
                estimate.estimatedCostUsdLower(),
                estimate.estimatedCostUsd(),
                estimate.estimatedCostUsdUpper(),
                lower,
                upper,
                stages,
                gates,
                commands(status, estimate.demoProfileId()),
                sourceReuse,
                sourceReuseDecision,
                narrationScriptIntake,
                estimate.cacheNotes(),
                safetyNotes(estimate, readiness)
        );
    }

    private UploadNarrationScriptIntakeVo narrationScriptIntake(String narrationScript) {
        boolean supplied = StringUtils.hasText(narrationScript);
        NarrationQuickScriptParser.Result result = narrationQuickScriptParser.parse(narrationScript);
        List<String> errors = new ArrayList<>(result.errors());
        if (result.valid()) {
            for (com.linguaframe.job.domain.dto.SaveNarrationSegmentsRequest.Segment segment : result.segments()) {
                if (!narrationVoiceCatalogService.containsVoice(segment.voice())) {
                    errors.add("Row " + (segment.index() + 1)
                            + ": narration voice must be one of the configured presets.");
                }
            }
        }
        return new UploadNarrationScriptIntakeVo(
                errors.isEmpty() ? STATUS_READY : STATUS_BLOCKED,
                supplied,
                result.segmentCount(),
                result.characterCount(),
                result.voiceSummary(),
                errors
        );
    }

    private List<UploadExecutionPlanStageVo> stages(UploadCostEstimateVo estimate) {
        List<UploadExecutionPlanStageVo> stages = new ArrayList<>();
        for (UploadCostEstimateStageVo stage : estimate.stages()) {
            String executionType = executionType(stage);
            boolean runnable = !STATUS_BLOCKED.equals(stage.status()) && !"DISABLED".equals(stage.status());
            int lower = stageDurationLower(stage, estimate.durationSeconds());
            int upper = stageDurationUpper(stage, estimate.durationSeconds());
            stages.add(new UploadExecutionPlanStageVo(
                    stage.id(),
                    stage.label(),
                    stage.status(),
                    executionType,
                    stage.provider(),
                    stage.model(),
                    runnable,
                    stage.estimatedCostUsd(),
                    lower,
                    upper,
                    stage.detail()
            ));
        }
        return stages;
    }

    private List<UploadExecutionPlanGateVo> gates(
            UploadCostEstimateVo estimate,
            DemoUploadReadinessVo readiness,
            OwnerQuotaPreflightVo ownerQuota,
            UploadNarrationScriptIntakeVo narrationScriptIntake
    ) {
        List<UploadExecutionPlanGateVo> gates = new ArrayList<>();
        gates.add(new UploadExecutionPlanGateVo(
                "uploadValidation",
                "Upload validation",
                estimate.valid() ? STATUS_READY : STATUS_BLOCKED,
                !estimate.valid(),
                estimate.validationMessage(),
                estimate.valid() ? "No validation action required." : "Replace the source video or change upload limits."
        ));
        gates.add(new UploadExecutionPlanGateVo(
                "uploadReadiness",
                "Upload readiness",
                readiness.overallStatus(),
                STATUS_BLOCKED.equals(readiness.overallStatus()),
                readiness.requiredActions().isEmpty()
                        ? "Upload readiness checks did not report required actions."
                        : String.join(" ", readiness.requiredActions()),
                readiness.requiredActions().isEmpty()
                        ? "No readiness action required."
                        : readiness.requiredActions().get(0)
        ));
        for (DemoUploadReadinessCheckVo check : readiness.checks()) {
            gates.add(new UploadExecutionPlanGateVo(
                    check.id(),
                    check.label(),
                    check.status(),
                    check.blocking(),
                    check.detail(),
                    check.nextAction()
            ));
        }
        gates.add(new UploadExecutionPlanGateVo(
                "narrationScriptIntake",
                "Narration script intake",
                narrationScriptIntake.status(),
                STATUS_BLOCKED.equals(narrationScriptIntake.status()),
                narrationScriptIntakeDetail(narrationScriptIntake),
                STATUS_BLOCKED.equals(narrationScriptIntake.status())
                        ? "Fix upload-time narration script rows before uploading media."
                        : "No narration script intake action required."
        ));
        gates.add(new UploadExecutionPlanGateVo(
                "ownerQuota",
                "Owner quota",
                ownerQuota.allowed() ? STATUS_READY : STATUS_BLOCKED,
                !ownerQuota.allowed(),
                ownerQuota.blockingReasons().isEmpty()
                        ? "Owner quota allows upload."
                        : String.join(" ", ownerQuota.blockingReasons()),
                ownerQuota.allowed() ? "No owner quota action required." : "Wait for active work to finish or raise owner limits."
        ));
        for (UploadCostEstimateBudgetVo budget : estimate.budgets()) {
            gates.add(new UploadExecutionPlanGateVo(
                    budget.id(),
                    budget.label(),
                    budget.status(),
                    STATUS_BLOCKED.equals(budget.status()),
                    budget.detail(),
                    STATUS_BLOCKED.equals(budget.status())
                            ? "Lower cost or raise the configured budget guard."
                            : "No budget action required."
            ));
        }
        return gates;
    }

    private String narrationScriptIntakeDetail(UploadNarrationScriptIntakeVo intake) {
        if (STATUS_BLOCKED.equals(intake.status())) {
            return String.join(" ", intake.errors());
        }
        if (!intake.supplied()) {
            return "No upload-time narration script supplied.";
        }
        return "Upload will seed " + intake.segmentCount() + " narration script rows, "
                + intake.characterCount() + " characters, voices: " + intake.voiceSummary() + ".";
    }

    private List<UploadExecutionPlanCommandVo> commands(String status, String demoProfileId) {
        String profile = demoProfileId == null ? "quick-baseline" : demoProfileId;
        String uploadCommand = "LINGUAFRAME_DEMO_PROFILE_ID=" + profile + " scripts/demo/docker-e2e-success.sh";
        if ("tears-showcase".equals(profile)) {
            uploadCommand = "LINGUAFRAME_DEMO_PROFILE_ID=tears-showcase LINGUAFRAME_TEARS_SAMPLE_PATH=/path/to/tos_casting-720p.mp4 scripts/demo/docker-e2e-tears-of-steel-full.sh";
        }
        List<UploadExecutionPlanCommandVo> commands = new ArrayList<>();
        commands.add(new UploadExecutionPlanCommandVo(
                "executionPlan",
                "Refresh execution plan",
                "scripts/demo/upload-execution-plan.sh",
                "Re-run this read-only upload execution plan."
        ));
        commands.add(new UploadExecutionPlanCommandVo(
                "readiness",
                "Check upload readiness",
                "LINGUAFRAME_DEMO_PROFILE_ID=" + profile + " scripts/demo/upload-readiness.sh",
                "Inspect readiness gates without media upload."
        ));
        commands.add(new UploadExecutionPlanCommandVo(
                "upload",
                STATUS_BLOCKED.equals(status) ? "Upload after blockers are fixed" : "Run upload demo",
                uploadCommand,
                "Run the selected demo upload after this plan is READY or accepted with ATTENTION."
        ));
        return commands;
    }

    private String overallStatus(
            UploadCostEstimateVo estimate,
            DemoUploadReadinessVo readiness,
            OwnerQuotaPreflightVo ownerQuota,
            List<UploadExecutionPlanGateVo> gates
    ) {
        if (!estimate.valid() || !ownerQuota.allowed() || STATUS_BLOCKED.equals(estimate.overallStatus())
                || STATUS_BLOCKED.equals(readiness.overallStatus())
                || gates.stream().anyMatch(gate -> gate.blocking() && STATUS_BLOCKED.equals(gate.status()))) {
            return STATUS_BLOCKED;
        }
        if (STATUS_ATTENTION.equals(estimate.overallStatus())
                || STATUS_ATTENTION.equals(readiness.overallStatus())
                || gates.stream().anyMatch(gate -> STATUS_ATTENTION.equals(gate.status()))) {
            return STATUS_ATTENTION;
        }
        return STATUS_READY;
    }

    private String recommendedNextAction(String status, UploadCostEstimateVo estimate) {
        if (!estimate.valid()) {
            return "Replace the source video or choose media inside the configured upload limits.";
        }
        return switch (status) {
            case STATUS_BLOCKED -> "Resolve blocking gates before uploading media.";
            case STATUS_ATTENTION -> "Review warnings and budget impact before uploading media.";
            default -> "Upload can proceed with the selected profile and options.";
        };
    }

    private List<String> safetyNotes(UploadCostEstimateVo estimate, DemoUploadReadinessVo readiness) {
        List<String> notes = new ArrayList<>(estimate.safetyNotes());
        notes.add("Execution plan is read-only and does not store media or call providers.");
        if (!readiness.evidenceRoutes().isEmpty()) {
            notes.add("Readiness evidence routes: " + String.join(", ", readiness.evidenceRoutes()) + ".");
        }
        return notes;
    }

    private String executionType(UploadCostEstimateStageVo stage) {
        if ("DISABLED".equals(stage.status())) {
            return "DISABLED";
        }
        if (stage.paidProviderCall()) {
            return "PAID";
        }
        return "LOCAL";
    }

    private int estimateDurationLower(UploadCostEstimateVo estimate, List<UploadExecutionPlanStageVo> stages) {
        if (!estimate.valid()) {
            return 0;
        }
        return stages.stream()
                .map(UploadExecutionPlanStageVo::estimatedDurationSecondsLower)
                .reduce(0, Integer::sum);
    }

    private int estimateDurationUpper(UploadCostEstimateVo estimate, List<UploadExecutionPlanStageVo> stages) {
        if (!estimate.valid()) {
            return 0;
        }
        return stages.stream()
                .map(UploadExecutionPlanStageVo::estimatedDurationSecondsUpper)
                .reduce(0, Integer::sum);
    }

    private int stageDurationLower(UploadCostEstimateStageVo stage, Integer durationSeconds) {
        int duration = durationSeconds == null ? 0 : durationSeconds;
        return switch (stage.id()) {
            case "audioExtraction" -> Math.max(2, duration / 8);
            case "transcription" -> Math.max(5, duration / 3);
            case "translation", "subtitlePolishing", "qualityEvaluation" -> Math.max(3, duration / 10);
            case "tts" -> Math.max(4, duration / 6);
            case "subtitleBurnIn" -> Math.max(5, duration / 4);
            default -> 1;
        };
    }

    private int stageDurationUpper(UploadCostEstimateStageVo stage, Integer durationSeconds) {
        int lower = stageDurationLower(stage, durationSeconds);
        if ("DISABLED".equals(stage.status())) {
            return lower;
        }
        if (stage.paidProviderCall()) {
            return lower * 3;
        }
        return lower * 2;
    }
}
