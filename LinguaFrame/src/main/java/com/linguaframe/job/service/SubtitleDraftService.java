package com.linguaframe.job.service;

import com.linguaframe.job.domain.dto.UpdateSubtitleDraftRequest;
import com.linguaframe.job.domain.enums.SubtitleDraftExportFormat;
import com.linguaframe.job.domain.vo.SubtitleDraftSummaryVo;

public interface SubtitleDraftService {

    SubtitleDraftSummaryVo getDraft(String jobId, String language);

    SubtitleDraftSummaryVo updateDraft(String jobId, String language, UpdateSubtitleDraftRequest request);

    SubtitleDraftSummaryVo clearDraft(String jobId, String language);

    byte[] exportDraft(String jobId, String language, SubtitleDraftExportFormat format);
}
