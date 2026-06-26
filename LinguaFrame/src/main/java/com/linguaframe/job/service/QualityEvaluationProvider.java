package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.QualityEvaluationRequestBo;
import com.linguaframe.job.domain.bo.QualityEvaluationResultBo;

public interface QualityEvaluationProvider {

    QualityEvaluationResultBo evaluate(QualityEvaluationRequestBo request);
}
