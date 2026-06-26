package com.linguaframe.job.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.exception.CostBudgetExceededException;
import com.linguaframe.job.service.CostBudgetGuardService;
import com.linguaframe.job.service.ModelCallAuditService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class CostBudgetGuardServiceImpl implements CostBudgetGuardService {

    private final LinguaFrameProperties properties;
    private final ModelCallAuditService modelCallAuditService;

    public CostBudgetGuardServiceImpl(
            LinguaFrameProperties properties,
            ModelCallAuditService modelCallAuditService
    ) {
        this.properties = properties;
        this.modelCallAuditService = modelCallAuditService;
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
    }
}
