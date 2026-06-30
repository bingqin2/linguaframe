package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.StoredNarrationDeliveryPackageBo;
import com.linguaframe.job.domain.vo.NarrationDeliveryPackageVo;

public interface NarrationDeliveryPackageService {

    NarrationDeliveryPackageVo getSummary(String jobId);

    NarrationDeliveryPackageVo getPackage(String jobId);

    String renderMarkdown(String jobId);

    StoredNarrationDeliveryPackageBo openPackage(String jobId);
}
