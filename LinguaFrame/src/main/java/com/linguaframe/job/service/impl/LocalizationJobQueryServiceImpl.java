package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.LocalizationJobQueryService;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class LocalizationJobQueryServiceImpl implements LocalizationJobQueryService {

    private final LocalizationJobRepository jobRepository;

    public LocalizationJobQueryServiceImpl(LocalizationJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    public LocalizationJobVo getJob(String jobId) {
        LocalizationJobRecord record = jobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Localization job not found."));
        return new LocalizationJobVo(
                record.id(),
                record.videoId(),
                record.targetLanguage(),
                record.status(),
                record.createdAt()
        );
    }
}
