package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.StoredDemoHandoffPortalPackageBo;
import com.linguaframe.job.domain.vo.DemoHandoffPortalVo;

public interface DemoHandoffPortalService {

    DemoHandoffPortalVo getPortal(String jobId);

    String renderMarkdown(String jobId);

    StoredDemoHandoffPortalPackageBo openPackage(String jobId);
}
