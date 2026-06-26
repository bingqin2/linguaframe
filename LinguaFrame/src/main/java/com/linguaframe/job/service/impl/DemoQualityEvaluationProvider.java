package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.bo.QualityEvaluationRequestBo;
import com.linguaframe.job.domain.bo.QualityEvaluationResultBo;
import com.linguaframe.job.service.QualityEvaluationProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "linguaframe.evaluation", name = "provider", havingValue = "demo", matchIfMissing = true)
public class DemoQualityEvaluationProvider implements QualityEvaluationProvider {

    @Override
    public QualityEvaluationResultBo evaluate(QualityEvaluationRequestBo request) {
        return new QualityEvaluationResultBo(
                92,
                "GOOD",
                95,
                92,
                94,
                88,
                List.of("Demo evaluation found no blocking subtitle quality issues."),
                List.of("Review tone and terminology before publishing.")
        );
    }
}
