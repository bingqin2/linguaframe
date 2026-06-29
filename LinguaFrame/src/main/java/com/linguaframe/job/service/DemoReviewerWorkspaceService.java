package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.DemoReviewerWorkspaceVo;
import com.linguaframe.job.domain.bo.StoredDemoReviewerWorkspacePackageBo;

public interface DemoReviewerWorkspaceService {

    DemoReviewerWorkspaceVo getWorkspace(String jobId);

    String renderMarkdown(String jobId);

    StoredDemoReviewerWorkspacePackageBo openPackage(String jobId);
}
