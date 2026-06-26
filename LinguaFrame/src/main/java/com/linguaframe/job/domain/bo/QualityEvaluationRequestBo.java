package com.linguaframe.job.domain.bo;

import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;

import java.util.List;

public record QualityEvaluationRequestBo(
        String jobId,
        String language,
        List<TranscriptSegmentVo> sourceSegments,
        List<SubtitleSegmentVo> targetSegments
) {
}
