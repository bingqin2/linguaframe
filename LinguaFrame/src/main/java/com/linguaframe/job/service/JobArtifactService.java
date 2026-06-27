package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.StoredArtifactArchiveBo;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.vo.JobArtifactVo;

import java.util.List;

public interface JobArtifactService {

    JobArtifactVo createArtifact(CreateJobArtifactCommand command);

    JobArtifactVo createReusedArtifact(String jobId, JobArtifactRecord source);

    List<JobArtifactVo> listArtifacts(String jobId);

    StoredObjectResourceBo openArtifact(String jobId, String artifactId);

    default StoredArtifactArchiveBo openArtifactArchive(String jobId) {
        throw new UnsupportedOperationException("Artifact archive download is not supported by this service.");
    }
}
