package com.linguaframe.job.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.demo.domain.vo.NarrationDemoPresetVo;
import com.linguaframe.demo.service.NarrationDemoPresetService;
import com.linguaframe.job.domain.dto.NarrationDemoRenderPreflightRequestDto;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.NarrationDemoRenderPreflightCheckVo;
import com.linguaframe.job.domain.vo.NarrationDemoRenderPreflightVo;
import com.linguaframe.job.domain.vo.NarrationEvidenceVo;
import com.linguaframe.job.domain.vo.NarrationScriptPackageVo;
import com.linguaframe.job.domain.vo.NarrationWorkspaceVo;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.NarrationDemoRenderPreflightService;
import com.linguaframe.job.service.NarrationEvidenceService;
import com.linguaframe.job.service.NarrationScriptPackageService;
import com.linguaframe.job.service.NarrationWorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class NarrationDemoRenderPreflightServiceImpl implements NarrationDemoRenderPreflightService {

    private static final String READY = "READY";
    private static final String ATTENTION = "ATTENTION";
    private static final String BLOCKED = "BLOCKED";

    private final LinguaFrameProperties properties;
    private final NarrationDemoPresetService presetService;
    private final NarrationWorkspaceService workspaceService;
    private final NarrationScriptPackageService scriptPackageService;
    private final NarrationEvidenceService evidenceService;
    private final JobArtifactService artifactService;

    public NarrationDemoRenderPreflightServiceImpl(
            LinguaFrameProperties properties,
            NarrationDemoPresetService presetService,
            NarrationWorkspaceService workspaceService,
            NarrationScriptPackageService scriptPackageService,
            NarrationEvidenceService evidenceService,
            JobArtifactService artifactService
    ) {
        this.properties = properties;
        this.presetService = presetService;
        this.workspaceService = workspaceService;
        this.scriptPackageService = scriptPackageService;
        this.evidenceService = evidenceService;
        this.artifactService = artifactService;
    }

    @Override
    public NarrationDemoRenderPreflightVo preflight(String jobId, NarrationDemoRenderPreflightRequestDto request) {
        NarrationDemoRenderPreflightRequestDto safeRequest = request == null
                ? new NarrationDemoRenderPreflightRequestDto("", false, true)
                : request;
        String presetId = safeRequest.presetId() == null ? "" : safeRequest.presetId().trim();
        Optional<NarrationDemoPresetVo> preset = StringUtils.hasText(presetId)
                ? presetService.findById(presetId)
                : Optional.empty();
        NarrationWorkspaceVo workspace = workspaceService.getWorkspace(jobId);
        NarrationScriptPackageVo scriptPackage = scriptPackageService.getPackage(jobId);
        NarrationEvidenceVo evidence = evidenceService.getEvidence(jobId);
        List<JobArtifactVo> artifacts = artifactService.listArtifacts(jobId);

        List<NarrationDemoRenderPreflightCheckVo> checks = new ArrayList<>();
        List<String> requiredConfirmations = new ArrayList<>();

        if (preset.isPresent()) {
            checks.add(check("PRESET", "Preset", "PASS", "Preset " + presetId + " is available."));
        } else {
            checks.add(check("PRESET", "Preset", "BLOCK", "Select a valid narration demo preset before rendering."));
        }

        int existingSegments = workspace == null ? 0 : workspace.segmentCount();
        if (existingSegments > 0 && !safeRequest.replaceExisting()) {
            requiredConfirmations.add("REPLACE_EXISTING");
            checks.add(check("REPLACE_CONFIRMATION", "Replace confirmation", "BLOCK", "Current narration workspace has " + existingSegments + " segments and replacement is not confirmed."));
        } else if (existingSegments > 0) {
            requiredConfirmations.add("REPLACE_EXISTING");
            checks.add(check("REPLACE_CONFIRMATION", "Replace confirmation", "WARN", "Rendering will replace " + existingSegments + " existing narration segments."));
        } else {
            checks.add(check("REPLACE_CONFIRMATION", "Replace confirmation", "PASS", "No existing narration segments will be replaced."));
        }

        String providerMode = providerMode();
        boolean paidProvider = !"demo".equals(providerMode);
        if (paidProvider) {
            requiredConfirmations.add("PAID_PROVIDER");
            checks.add(check("TTS_PROVIDER", "TTS provider", "WARN", "Narration render can call the configured " + providerMode + " TTS provider."));
        } else {
            checks.add(check("TTS_PROVIDER", "TTS provider", "PASS", "Demo TTS provider is configured."));
        }

        if (safeRequest.generateNarratedVideo()) {
            if (hasBaseVideo(artifacts)) {
                checks.add(check("NARRATED_VIDEO_INPUT", "Narrated video input", "PASS", "A base video artifact is available for narrated-video generation."));
            } else {
                checks.add(check("NARRATED_VIDEO_INPUT", "Narrated video input", "BLOCK", "Generate or provide a base video before narrated-video render."));
            }
        } else {
            checks.add(check("NARRATED_VIDEO_INPUT", "Narrated video input", "WARN", "Narrated video generation is disabled; render will produce narration audio only."));
        }

        if (scriptPackage != null && !READY.equals(scriptPackage.status())) {
            checks.add(check("SCRIPT_PACKAGE", "Script package", "WARN", "Current script package status is " + scriptPackage.status() + "."));
        }
        if (evidence != null && !READY.equals(evidence.status())) {
            checks.add(check("NARRATION_EVIDENCE", "Narration evidence", "WARN", "Current narration evidence status is " + evidence.status() + "."));
        }

        return new NarrationDemoRenderPreflightVo(
                jobId,
                presetId,
                overallStatus(checks),
                List.copyOf(checks),
                preset.map(NarrationDemoPresetVo::segmentCount).orElse(0),
                preset.map(NarrationDemoPresetVo::totalCharacterCount).orElse(0),
                providerMode,
                paidProvider,
                existingSegments,
                safeRequest.generateNarratedVideo(),
                List.copyOf(requiredConfirmations),
                "LINGUAFRAME_DEMO_JOB_ID=" + jobId + " scripts/demo/narration-demo-render.sh",
                List.of(
                        "/api/jobs/" + jobId + "/narration-demo/render",
                        "/api/jobs/" + jobId + "/narration-evidence",
                        "/api/jobs/" + jobId + "/narration-script-package"
                )
        );
    }

    private String providerMode() {
        String provider = properties.getTts().getProvider();
        if (!StringUtils.hasText(provider)) {
            return "demo";
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasBaseVideo(List<JobArtifactVo> artifacts) {
        return artifacts.stream().anyMatch(artifact ->
                artifact.type() == JobArtifactType.BURNED_VIDEO
                        || artifact.type() == JobArtifactType.DUBBED_VIDEO
                        || artifact.type() == JobArtifactType.REVIEWED_BURNED_VIDEO
        );
    }

    private String overallStatus(List<NarrationDemoRenderPreflightCheckVo> checks) {
        if (checks.stream().anyMatch(check -> "BLOCK".equals(check.status()))) {
            return BLOCKED;
        }
        if (checks.stream().anyMatch(check -> "WARN".equals(check.status()))) {
            return ATTENTION;
        }
        return READY;
    }

    private NarrationDemoRenderPreflightCheckVo check(String key, String label, String status, String message) {
        return new NarrationDemoRenderPreflightCheckVo(key, label, status, message);
    }
}
