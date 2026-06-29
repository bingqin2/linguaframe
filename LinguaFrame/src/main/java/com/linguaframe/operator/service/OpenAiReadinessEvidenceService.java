package com.linguaframe.operator.service;

import com.linguaframe.operator.domain.vo.OpenAiReadinessEvidenceVo;

public interface OpenAiReadinessEvidenceService {

    OpenAiReadinessEvidenceVo getEvidence();

    String evidenceMarkdown();
}
