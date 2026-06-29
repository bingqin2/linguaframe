package com.linguaframe.job.domain.vo;

import java.util.List;

public record NarrationScriptPackageImportVo(
        String jobId,
        int importedSegmentCount,
        int totalCharacterCount,
        String voiceSummary,
        boolean replacedExisting,
        List<String> warnings,
        NarrationWorkspaceVo workspace
) {
}
