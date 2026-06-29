package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.StoredNarrationScriptPackageBo;
import com.linguaframe.job.domain.dto.ImportNarrationScriptPackageDto;
import com.linguaframe.job.domain.vo.NarrationScriptPackageImportVo;
import com.linguaframe.job.domain.vo.NarrationScriptPackageVo;

public interface NarrationScriptPackageService {

    NarrationScriptPackageVo getPackage(String jobId);

    String renderMarkdown(String jobId);

    StoredNarrationScriptPackageBo openPackage(String jobId);

    NarrationScriptPackageImportVo importPackage(String jobId, ImportNarrationScriptPackageDto request);
}
