package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.repository.JobArtifactRepository;
import com.linguaframe.job.service.ArtifactCacheService;
import com.linguaframe.job.service.JobArtifactService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ArtifactCacheServiceImpl implements ArtifactCacheService {

    private final JobArtifactRepository artifactRepository;
    private final JobArtifactService artifactService;

    public ArtifactCacheServiceImpl(JobArtifactRepository artifactRepository, JobArtifactService artifactService) {
        this.artifactRepository = artifactRepository;
        this.artifactService = artifactService;
    }

    @Override
    public Optional<JobArtifactVo> tryReuseArtifact(LocalizationJobExecutionContextBo context, JobArtifactType type) {
        return artifactRepository.findReusableArtifact(
                context.job().videoId(),
                context.job().targetLanguage(),
                type,
                context.job().subtitleStylePreset()
                )
                .map(source -> artifactService.createReusedArtifact(context.job().id(), source));
    }
}
