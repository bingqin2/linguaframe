package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.vo.JobArtifactVo;

import java.util.Optional;

public interface ArtifactCacheService {

    Optional<JobArtifactVo> tryReuseArtifact(LocalizationJobExecutionContextBo context, JobArtifactType type);
}
