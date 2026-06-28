package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.StoredDemoRunPackageBo;

public interface DemoRunPackageService {

    StoredDemoRunPackageBo openDemoRunPackage(String jobId);
}
