package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.StoredDemoRunSnapshotPackageBo;
import com.linguaframe.job.domain.vo.DemoRunSnapshotVo;

public interface DemoRunSnapshotService {

    DemoRunSnapshotVo buildSnapshot(String jobId);

    StoredDemoRunSnapshotPackageBo openSnapshotPackage(String jobId);
}
