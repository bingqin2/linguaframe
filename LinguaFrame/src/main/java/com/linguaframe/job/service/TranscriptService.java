package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.TranscriptionResultBo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;

import java.util.List;

public interface TranscriptService {

    List<TranscriptSegmentVo> replaceTranscript(String jobId, TranscriptionResultBo result);

    List<TranscriptSegmentVo> listTranscript(String jobId);
}
