package com.linguaframe.media.domain.vo;

import java.util.List;

public record UploadSourceReuseDecisionVo(
        String status,
        String headline,
        String summary,
        String recommendedAction,
        String recommendedExistingJobId,
        int candidateCount,
        List<UploadSourceReuseDecisionActionVo> actions,
        List<UploadSourceReuseDecisionLinkVo> links,
        List<String> safetyNotes,
        UploadSourceReuseVo sourceReuse
) {
}
