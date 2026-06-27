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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CostBudgetGuardServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-27T12:34:56Z"),
            ZoneOffset.UTC
    );

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

    @Test
    void disabledDailyGuardAllowsOverBudgetDailyCost() {
        LinguaFrameProperties properties = properties(true, "1.00");
        properties.getCost().setDailyBudgetGuardEnabled(false);
        properties.getCost().setMaxDailyCostUsd(new BigDecimal("0.01"));
        CostBudgetGuardService service = new CostBudgetGuardServiceImpl(
                properties,
                new FixedSummaryModelCallAuditService("0.01", "1.00"),
                FIXED_CLOCK
        );

        service.assertWithinBudget("budget-job-daily-disabled", LocalizationJobStage.TARGET_SUBTITLE_EXPORT);
    }

    @Test
    void zeroDailyLimitMeansNoDailyBudgetLimit() {
        LinguaFrameProperties properties = properties(true, "1.00");
        properties.getCost().setDailyBudgetGuardEnabled(true);
        properties.getCost().setMaxDailyCostUsd(BigDecimal.ZERO);
        CostBudgetGuardService service = new CostBudgetGuardServiceImpl(
                properties,
                new FixedSummaryModelCallAuditService("0.01", "1.00"),
                FIXED_CLOCK
        );

        service.assertWithinBudget("budget-job-daily-zero", LocalizationJobStage.DUBBING_AUDIO_GENERATION);
    }

    @Test
    void underDailyBudgetIsAllowed() {
        LinguaFrameProperties properties = properties(true, "1.00");
        properties.getCost().setDailyBudgetGuardEnabled(true);
        properties.getCost().setMaxDailyCostUsd(new BigDecimal("0.02"));
        CostBudgetGuardService service = new CostBudgetGuardServiceImpl(
                properties,
                new FixedSummaryModelCallAuditService("0.01", "0.01"),
                FIXED_CLOCK
        );

        service.assertWithinBudget("budget-job-daily-under", LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT);
    }

    @Test
    void exactlyAtDailyLimitThrowsBeforeProviderCall() {
        LinguaFrameProperties properties = properties(true, "1.00");
        properties.getCost().setDailyBudgetGuardEnabled(true);
        properties.getCost().setMaxDailyCostUsd(new BigDecimal("0.01"));
        CostBudgetGuardService service = new CostBudgetGuardServiceImpl(
                properties,
                new FixedSummaryModelCallAuditService("0.01", "0.01"),
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.assertWithinBudget(
                "budget-job-daily-at-limit",
                LocalizationJobStage.TRANSLATION_QUALITY_EVALUATION
        ))
                .isInstanceOf(CostBudgetExceededException.class)
                .hasMessageContaining("Daily cost budget exceeded before TRANSLATION_QUALITY_EVALUATION")
                .hasMessageContaining("on 2026-06-27")
                .hasMessageContaining("current estimated cost 0.01 USD")
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
        private final BigDecimal dailyEstimatedCostUsd;

        private FixedSummaryModelCallAuditService(String estimatedCostUsd) {
            this(estimatedCostUsd, "0");
        }

        private FixedSummaryModelCallAuditService(String estimatedCostUsd, String dailyEstimatedCostUsd) {
            this.estimatedCostUsd = new BigDecimal(estimatedCostUsd);
            this.dailyEstimatedCostUsd = new BigDecimal(dailyEstimatedCostUsd);
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

        @Override
        public BigDecimal summarizeDailyBudget(String budgetIdentity, Instant since) {
            return dailyEstimatedCostUsd;
        }
    }
}
