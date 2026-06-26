package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.CreateModelCallRecordCommand;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.ModelCallVo;

import java.util.List;

public interface ModelCallAuditService {

    ModelCallVo recordSuccess(CreateModelCallRecordCommand command);

    ModelCallVo recordFailure(CreateModelCallRecordCommand command, String safeErrorSummary);

    List<ModelCallVo> listModelCalls(String jobId);

    JobUsageSummaryVo summarizeJob(String jobId);
}
