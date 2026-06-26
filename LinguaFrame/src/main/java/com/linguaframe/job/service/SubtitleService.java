package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.TranslationResultBo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;

import java.util.List;

public interface SubtitleService {

    List<SubtitleSegmentVo> replaceSubtitles(String jobId, String language, TranslationResultBo result);

    List<SubtitleSegmentVo> listSubtitles(String jobId, String language);
}
