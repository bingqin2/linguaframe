package com.linguaframe.media.domain.vo;

import java.util.List;

public record UploadSourceReuseVo(
        String sourceContentSha256,
        int candidateCount,
        String recommendedAction,
        String recommendedExistingJobId,
        List<UploadSourceReuseCandidateVo> candidates
) {
    public static UploadSourceReuseVo empty() {
        return new UploadSourceReuseVo(null, 0, "UPLOAD_NEW_SOURCE", null, List.of());
    }
}
