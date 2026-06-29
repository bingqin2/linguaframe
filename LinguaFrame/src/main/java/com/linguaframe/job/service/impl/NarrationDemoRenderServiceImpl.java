package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.dto.ApplyNarrationDemoPresetDto;
import com.linguaframe.job.domain.dto.RenderNarrationDemoDto;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.NarratedVideoGenerationVo;
import com.linguaframe.job.domain.vo.NarrationDemoPresetApplyVo;
import com.linguaframe.job.domain.vo.NarrationDemoRenderStepVo;
import com.linguaframe.job.domain.vo.NarrationDemoRenderVo;
import com.linguaframe.job.domain.vo.NarrationEvidenceVo;
import com.linguaframe.job.domain.vo.NarrationGenerationVo;
import com.linguaframe.job.domain.vo.NarrationScriptPackageVo;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.NarratedVideoService;
import com.linguaframe.job.service.NarrationAudioService;
import com.linguaframe.job.service.NarrationDemoPresetApplyService;
import com.linguaframe.job.service.NarrationDemoRenderService;
import com.linguaframe.job.service.NarrationEvidenceService;
import com.linguaframe.job.service.NarrationScriptPackageService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class NarrationDemoRenderServiceImpl implements NarrationDemoRenderService {

    private static final String PRESET_APPLY = "PRESET_APPLY";
    private static final String NARRATION_AUDIO = "NARRATION_AUDIO";
    private static final String NARRATED_VIDEO = "NARRATED_VIDEO";
    private static final String SCRIPT_PACKAGE = "SCRIPT_PACKAGE";
    private static final String NARRATION_EVIDENCE = "NARRATION_EVIDENCE";

    private final NarrationDemoPresetApplyService presetApplyService;
    private final NarrationAudioService narrationAudioService;
    private final NarratedVideoService narratedVideoService;
    private final NarrationScriptPackageService scriptPackageService;
    private final NarrationEvidenceService evidenceService;
    private final JobArtifactService artifactService;

    public NarrationDemoRenderServiceImpl(
            NarrationDemoPresetApplyService presetApplyService,
            NarrationAudioService narrationAudioService,
            NarratedVideoService narratedVideoService,
            NarrationScriptPackageService scriptPackageService,
            NarrationEvidenceService evidenceService,
            JobArtifactService artifactService
    ) {
        this.presetApplyService = presetApplyService;
        this.narrationAudioService = narrationAudioService;
        this.narratedVideoService = narratedVideoService;
        this.scriptPackageService = scriptPackageService;
        this.evidenceService = evidenceService;
        this.artifactService = artifactService;
    }

    @Override
    public NarrationDemoRenderVo render(String jobId, RenderNarrationDemoDto request) {
        if (request == null || !request.replaceExisting()) {
            throw new IllegalArgumentException("Narration demo render requires replaceExisting=true.");
        }
        if (!StringUtils.hasText(request.presetId())) {
            throw new IllegalArgumentException("Narration demo preset id is required.");
        }

        List<NarrationDemoRenderStepVo> steps = new ArrayList<>();
        NarrationDemoPresetApplyVo presetApply = presetApplyService.apply(
                jobId,
                new ApplyNarrationDemoPresetDto(request.presetId(), true)
        );
        steps.add(step(PRESET_APPLY, "Apply narration preset", "SUCCEEDED", "Applied preset " + presetApply.presetId() + "."));

        NarrationGenerationVo narrationAudio = null;
        NarratedVideoGenerationVo narratedVideo = null;
        boolean audioFailed = false;
        boolean videoFailed = false;

        try {
            narrationAudio = narrationAudioService.generateAudio(jobId);
            steps.add(step(NARRATION_AUDIO, "Generate narration audio", "SUCCEEDED", "Generated " + narrationAudio.filename() + "."));
        } catch (RuntimeException ex) {
            audioFailed = true;
            steps.add(step(NARRATION_AUDIO, "Generate narration audio", "FAILED", safeMessage(ex)));
            steps.add(step(NARRATED_VIDEO, "Generate narrated video", "SKIPPED", "Skipped because narration audio generation failed."));
        }

        if (!audioFailed) {
            if (request.generateNarratedVideo()) {
                try {
                    narratedVideo = narratedVideoService.generateVideo(jobId);
                    steps.add(step(NARRATED_VIDEO, "Generate narrated video", "SUCCEEDED", "Generated " + narratedVideo.filename() + "."));
                } catch (RuntimeException ex) {
                    videoFailed = true;
                    steps.add(step(NARRATED_VIDEO, "Generate narrated video", "FAILED", safeMessage(ex)));
                }
            } else {
                steps.add(step(NARRATED_VIDEO, "Generate narrated video", "SKIPPED", "Narrated video generation was disabled for this render."));
            }
        }

        NarrationScriptPackageVo scriptPackage = scriptPackageService.getPackage(jobId);
        steps.add(step(SCRIPT_PACKAGE, "Refresh narration script package", "SUCCEEDED", "Script package refreshed."));
        NarrationEvidenceVo evidence = evidenceService.getEvidence(jobId);
        steps.add(step(NARRATION_EVIDENCE, "Refresh narration evidence", "SUCCEEDED", "Narration evidence refreshed."));
        List<JobArtifactVo> artifacts = artifactService.listArtifacts(jobId);

        return new NarrationDemoRenderVo(
                jobId,
                request.presetId(),
                overallStatus(audioFailed, videoFailed),
                List.copyOf(steps),
                presetApply,
                narrationAudio,
                narratedVideo,
                scriptPackage,
                evidence,
                generatedArtifactCount(artifacts)
        );
    }

    private String overallStatus(boolean audioFailed, boolean videoFailed) {
        if (audioFailed) {
            return "FAILED";
        }
        if (videoFailed) {
            return "PARTIAL";
        }
        return "READY";
    }

    private int generatedArtifactCount(List<JobArtifactVo> artifacts) {
        return (int) artifacts.stream()
                .filter(artifact -> artifact.type() == JobArtifactType.NARRATION_AUDIO || artifact.type() == JobArtifactType.NARRATED_VIDEO)
                .count();
    }

    private NarrationDemoRenderStepVo step(String key, String label, String status, String message) {
        return new NarrationDemoRenderStepVo(key, label, status, message);
    }

    private String safeMessage(RuntimeException ex) {
        if (StringUtils.hasText(ex.getMessage())) {
            return ex.getMessage();
        }
        return ex.getClass().getSimpleName();
    }
}
