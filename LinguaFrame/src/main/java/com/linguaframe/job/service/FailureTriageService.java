package com.linguaframe.job.service;

import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.vo.FailureTriageVo;
import com.linguaframe.job.domain.vo.ModelCallVo;

import java.util.List;

public interface FailureTriageService {

    FailureTriageVo triage(
            LocalizationJobRecord record,
            List<JobTimelineEventRecord> timelineEvents,
            List<ModelCallVo> modelCalls
    );
}
