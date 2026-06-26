package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;

import java.util.List;

public interface SubtitleExportService {

    byte[] exportTranscriptJson(List<TranscriptSegmentVo> segments);

    byte[] exportSrt(List<TranscriptSegmentVo> segments);

    byte[] exportVtt(List<TranscriptSegmentVo> segments);

    byte[] exportSubtitleJson(List<SubtitleSegmentVo> segments);

    byte[] exportSubtitleSrt(List<SubtitleSegmentVo> segments);

    byte[] exportSubtitleVtt(List<SubtitleSegmentVo> segments);
}
