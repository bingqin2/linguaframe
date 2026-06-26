package com.linguaframe.job.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateModelCallRecordCommand;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.exception.CostBudgetExceededException;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.ModelCallVo;
import com.linguaframe.job.service.impl.CostBudgetGuardServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CostBudgetGuardServiceTests {

    @Test
    void disabledGuardAllowsOverBudgetJob() {
        LinguaFrameProperties properties = properties(false, "0.01");
        CostBudgetGuardService service = new CostBudgetGuardServiceImpl(
                properties,
                new FixedSummaryModelCallAuditService("1.00")
        );

        service.assertWithinBudget("budget-job-disabled", LocalizationJobStage.TARGET_SUBTITLE_EXPORT);
    }

    @Test
    void zeroLimitMeansNoBudgetLimit() {
        LinguaFrameProperties properties = properties(true, "0");
        CostBudgetGuardService service = new CostBudgetGuardServiceImpl(
                properties,
                new FixedSummaryModelCallAuditService("1.00")
        );

        service.assertWithinBudget("budget-job-zero-limit", LocalizationJobStage.DUBBING_AUDIO_GENERATION);
    }

    @Test
    void underBudgetJobIsAllowed() {
        LinguaFrameProperties properties = properties(true, "0.02");
        CostBudgetGuardService service = new CostBudgetGuardServiceImpl(
                properties,
                new FixedSummaryModelCallAuditService("0.01")
        );

        service.assertWithinBudget("budget-job-under", LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT);
    }

    @Test
    void exactlyAtLimitThrowsBeforeProviderCall() {
        LinguaFrameProperties properties = properties(true, "0.01");
        CostBudgetGuardService service = new CostBudgetGuardServiceImpl(
                properties,
                new FixedSummaryModelCallAuditService("0.01")
        );

        assertThatThrownBy(() -> service.assertWithinBudget(
                "budget-job-at-limit",
                LocalizationJobStage.TRANSLATION_QUALITY_EVALUATION
        ))
                .isInstanceOf(CostBudgetExceededException.class)
                .hasMessageContaining("Job cost budget exceeded before TRANSLATION_QUALITY_EVALUATION")
                .hasMessageContaining("current estimated cost 0.01 USD")
                .hasMessageContaining("limit 0.01 USD");
    }

    @Test
    void overLimitThrowsBeforeProviderCall() {
        LinguaFrameProperties properties = properties(true, "0.01");
        CostBudgetGuardService service = new CostBudgetGuardServiceImpl(
                properties,
                new FixedSummaryModelCallAuditService("0.02")
        );

        assertThatThrownBy(() -> service.assertWithinBudget(
                "budget-job-over-limit",
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT
        ))
                .isInstanceOf(CostBudgetExceededException.class)
                .hasMessageContaining("Job cost budget exceeded before TARGET_SUBTITLE_EXPORT")
                .hasMessageContaining("current estimated cost 0.02 USD")
                .hasMessageContaining("limit 0.01 USD");
    }

    private LinguaFrameProperties properties(boolean budgetGuardEnabled, String maxJobCostUsd) {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getCost().setBudgetGuardEnabled(budgetGuardEnabled);
        properties.getCost().setMaxJobCostUsd(new BigDecimal(maxJobCostUsd));
        return properties;
    }

    private static class FixedSummaryModelCallAuditService implements ModelCallAuditService {

        private final BigDecimal estimatedCostUsd;

        private FixedSummaryModelCallAuditService(String estimatedCostUsd) {
            this.estimatedCostUsd = new BigDecimal(estimatedCostUsd);
        }

        @Override
        public ModelCallVo recordSuccess(CreateModelCallRecordCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ModelCallVo recordFailure(CreateModelCallRecordCommand command, String safeErrorSummary) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ModelCallVo> listModelCalls(String jobId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JobUsageSummaryVo summarizeJob(String jobId) {
            return new JobUsageSummaryVo(
                    1,
                    0,
                    10L,
                    estimatedCostUsd,
                    null,
                    null,
                    null,
                    null
            );
        }
    }
}
