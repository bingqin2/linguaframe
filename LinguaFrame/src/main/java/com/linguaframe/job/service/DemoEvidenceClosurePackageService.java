package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.StoredDemoEvidenceClosurePackageBo;
import com.linguaframe.job.domain.vo.DemoEvidenceClosurePackageVo;

public interface DemoEvidenceClosurePackageService {

    DemoEvidenceClosurePackageVo buildClosure(String jobId, String preUploadJson);

    String renderMarkdown(DemoEvidenceClosurePackageVo closure);

    StoredDemoEvidenceClosurePackageBo openClosurePackage(String jobId, String preUploadJson);
}
