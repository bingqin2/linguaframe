package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.JobComparisonVo;

public interface JobComparisonService {

    JobComparisonVo compareJobs(String baselineJobId, String comparisonJobId);

    String buildMarkdownComparison(String baselineJobId, String comparisonJobId);
}
