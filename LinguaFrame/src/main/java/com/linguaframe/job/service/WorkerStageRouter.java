package com.linguaframe.job.service;

import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.WorkerRole;
import com.linguaframe.job.domain.vo.WorkerStagePlanVo;

import java.util.List;

public interface WorkerStageRouter {

    WorkerStagePlanVo plan(
            WorkerRole role,
            LocalizationJobStage startStage,
            List<LocalizationPipelineStage> orderedStages
    );
}
