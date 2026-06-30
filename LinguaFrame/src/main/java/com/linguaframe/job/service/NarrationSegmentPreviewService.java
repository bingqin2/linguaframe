package com.linguaframe.job.service;

import com.linguaframe.job.domain.dto.PreviewNarrationSegmentRequestDto;
import com.linguaframe.job.domain.vo.NarrationSegmentPreviewVo;

public interface NarrationSegmentPreviewService {

    NarrationSegmentPreviewVo previewSegment(String jobId, PreviewNarrationSegmentRequestDto request);
}
