package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.LocalizationJobVo;

import java.util.Optional;

public interface LocalizationJobStatusCacheService {

    Optional<LocalizationJobVo> get(String jobId);

    void put(LocalizationJobVo job);

    void evict(String jobId);
}
