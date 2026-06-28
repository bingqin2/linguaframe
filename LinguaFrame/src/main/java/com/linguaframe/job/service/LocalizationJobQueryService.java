package com.linguaframe.job.service;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;

public interface LocalizationJobQueryService {

    LocalizationJobListVo listJobs(LocalizationJobStatus status, Integer limit, Integer offset);

    default LocalizationJobListVo listJobsByVideoId(String videoId, Integer limit) {
        throw new UnsupportedOperationException();
    }

    LocalizationJobVo getJob(String jobId);

    JobDiagnosticsReportVo getDiagnosticsReport(String jobId);
}
