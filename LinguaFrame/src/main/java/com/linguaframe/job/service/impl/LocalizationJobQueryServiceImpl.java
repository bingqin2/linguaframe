package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.entity.JobDispatchEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.repository.JobDispatchEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.LocalizationJobQueryService;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class LocalizationJobQueryServiceImpl implements LocalizationJobQueryService {

    private final LocalizationJobRepository jobRepository;
    private final JobDispatchEventRepository dispatchEventRepository;

    public LocalizationJobQueryServiceImpl(
            LocalizationJobRepository jobRepository,
            JobDispatchEventRepository dispatchEventRepository
    ) {
        this.jobRepository = jobRepository;
        this.dispatchEventRepository = dispatchEventRepository;
    }

    @Override
    public LocalizationJobVo getJob(String jobId) {
        LocalizationJobRecord record = jobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Localization job not found."));
        JobDispatchEventRecord dispatchEvent = dispatchEventRepository.findLatestByJobId(jobId).orElse(null);
        return new LocalizationJobVo(
                record.id(),
                record.videoId(),
                record.targetLanguage(),
                record.status(),
                record.createdAt(),
                dispatchEvent == null ? null : dispatchEvent.status(),
                dispatchEvent == null ? 0 : dispatchEvent.attempts(),
                dispatchEvent == null ? null : dispatchEvent.dispatchedAt()
        );
    }
}
