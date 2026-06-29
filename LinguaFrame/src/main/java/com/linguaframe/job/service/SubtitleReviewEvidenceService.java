package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.StoredSubtitleReviewEvidencePackageBo;
import com.linguaframe.job.domain.vo.SubtitleReviewEvidenceVo;

public interface SubtitleReviewEvidenceService {

    SubtitleReviewEvidenceVo buildEvidence(String jobId);

    String buildMarkdownEvidence(String jobId);

    StoredSubtitleReviewEvidencePackageBo openEvidencePackage(String jobId);
}
