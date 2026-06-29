package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.NarrationMixSettingsRecord;

import java.util.Optional;

public interface NarrationMixSettingsRepository {

    Optional<NarrationMixSettingsRecord> findByJobId(String jobId);

    NarrationMixSettingsRecord upsert(NarrationMixSettingsRecord settings);

    void deleteByJobId(String jobId);
}
