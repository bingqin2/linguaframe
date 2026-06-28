package com.linguaframe.job.service;

import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.vo.JobPipelineProgressVo;

import java.util.List;

public interface JobPipelineProgressService {

    JobPipelineProgressVo summarize(LocalizationJobRecord record, List<JobTimelineEventRecord> timelineEvents);
}
