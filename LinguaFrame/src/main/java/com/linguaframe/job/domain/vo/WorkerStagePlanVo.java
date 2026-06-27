package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.WorkerRole;
import com.linguaframe.job.service.LocalizationPipelineStage;

import java.util.List;

public record WorkerStagePlanVo(
        List<LocalizationPipelineStage> executableStages,
        LocalizationJobStage nextStage,
        WorkerRole nextRole,
        boolean finalSegment
) {
}
