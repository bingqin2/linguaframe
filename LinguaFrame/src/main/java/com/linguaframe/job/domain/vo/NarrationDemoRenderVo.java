package com.linguaframe.job.domain.vo;

import java.util.List;

public record NarrationDemoRenderVo(
        String jobId,
        String presetId,
        String status,
        List<NarrationDemoRenderStepVo> steps,
        NarrationDemoPresetApplyVo presetApply,
        NarrationGenerationVo narrationAudio,
        NarratedVideoGenerationVo narratedVideo,
        NarrationScriptPackageVo scriptPackage,
        NarrationEvidenceVo narrationEvidence,
        int generatedArtifactCount
) {
}
