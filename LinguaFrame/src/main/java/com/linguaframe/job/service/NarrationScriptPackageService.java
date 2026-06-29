package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.StoredNarrationScriptPackageBo;
import com.linguaframe.job.domain.vo.NarrationScriptPackageVo;

public interface NarrationScriptPackageService {

    NarrationScriptPackageVo getPackage(String jobId);

    String renderMarkdown(String jobId);

    StoredNarrationScriptPackageBo openPackage(String jobId);
}
