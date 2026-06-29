package com.linguaframe.job.domain.vo;

public record NarrationDemoPresetApplyVo(
        String jobId,
        String presetId,
        String profileId,
        int importedSegmentCount,
        int totalCharacterCount,
        String voiceSummary,
        boolean replacedExisting,
        boolean generatedMedia,
        NarrationWorkspaceVo workspace,
        NarrationScriptPackageVo scriptPackage,
        String narrationEvidenceStatus
) {
}
