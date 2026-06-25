package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.enums.LocalizationJobStage;

public interface LocalizationPipelineStage {

    LocalizationJobStage stage();

    void execute(LocalizationJobExecutionContextBo context);
}
