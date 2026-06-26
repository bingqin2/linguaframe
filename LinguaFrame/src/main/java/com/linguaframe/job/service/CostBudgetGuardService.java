package com.linguaframe.job.service;

import com.linguaframe.job.domain.enums.LocalizationJobStage;

public interface CostBudgetGuardService {

    void assertWithinBudget(String jobId, LocalizationJobStage stage);
}
