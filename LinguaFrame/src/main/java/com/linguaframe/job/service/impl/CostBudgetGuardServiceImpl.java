package com.linguaframe.job.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.exception.CostBudgetExceededException;
import com.linguaframe.job.service.CostBudgetGuardService;
import com.linguaframe.job.service.ModelCallAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
public class CostBudgetGuardServiceImpl implements CostBudgetGuardService {

    private final LinguaFrameProperties properties;
    private final ModelCallAuditService modelCallAuditService;
    private final Clock clock;

    @Autowired
    public CostBudgetGuardServiceImpl(
            LinguaFrameProperties properties,
            ModelCallAuditService modelCallAuditService
    ) {
        this(properties, modelCallAuditService, Clock.systemUTC());
    }

    public CostBudgetGuardServiceImpl(
            LinguaFrameProperties properties,
            ModelCallAuditService modelCallAuditService,
            Clock clock
    ) {
        this.properties = properties;
        this.modelCallAuditService = modelCallAuditService;
        this.clock = clock;
    }

    @Override
    public void assertWithinBudget(String jobId, LocalizationJobStage stage) {
        if (!properties.getCost().isBudgetGuardEnabled()) {
            return;
        }
        BigDecimal limit = properties.getCost().getMaxJobCostUsd();
        if (limit.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal currentCost = modelCallAuditService.summarizeJob(jobId).estimatedCostUsd();
        if (currentCost.compareTo(limit) >= 0) {
            throw new CostBudgetExceededException(
                    "Job cost budget exceeded before " + stage
                            + ": current estimated cost " + currentCost
                            + " USD, limit " + limit + " USD."
            );
        }
        assertWithinDailyBudget(stage);
    }

    private void assertWithinDailyBudget(LocalizationJobStage stage) {
        if (!properties.getCost().isDailyBudgetGuardEnabled()) {
            return;
        }
        BigDecimal limit = properties.getCost().getMaxDailyCostUsd();
        if (limit.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        LocalDate utcDate = LocalDate.now(clock);
        Instant since = utcDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        BigDecimal currentCost = modelCallAuditService.summarizeDailyBudget(
                properties.getCost().getBudgetIdentity(),
                since
        );
        if (currentCost.compareTo(limit) >= 0) {
            throw new CostBudgetExceededException(
                    "Daily cost budget exceeded before " + stage
                            + " on " + utcDate
                            + ": current estimated cost " + currentCost
                            + " USD, limit " + limit + " USD."
            );
        }
    }
}
