package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.vo.JobArtifactVo;

import java.util.List;

public interface JobArtifactService {

    JobArtifactVo createArtifact(CreateJobArtifactCommand command);

    List<JobArtifactVo> listArtifacts(String jobId);

    StoredObjectResourceBo openArtifact(String jobId, String artifactId);
}
