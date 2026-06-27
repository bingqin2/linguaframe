package com.linguaframe.job.domain.bo;

import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.ProviderCacheHitVo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class LocalizationJobExecutionContextBo {

    private final LocalizationJobRecord job;
    private final QueuedLocalizationJobMessage message;
    private final Instant startedAt;
    private final List<JobArtifactVo> cacheHits = new ArrayList<>();
    private final List<ProviderCacheHitVo> providerCacheHits = new ArrayList<>();

    public LocalizationJobExecutionContextBo(
            LocalizationJobRecord job,
            QueuedLocalizationJobMessage message,
            Instant startedAt
    ) {
        this.job = job;
        this.message = message;
        this.startedAt = startedAt;
    }

    public LocalizationJobRecord job() {
        return job;
    }

    public QueuedLocalizationJobMessage message() {
        return message;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public void recordCacheHit(JobArtifactVo artifact) {
        cacheHits.add(artifact);
    }

    public List<JobArtifactVo> consumeCacheHits() {
        List<JobArtifactVo> hits = List.copyOf(cacheHits);
        cacheHits.clear();
        return hits;
    }

    public void recordProviderCacheHit(ProviderCacheHitVo hit) {
        providerCacheHits.add(hit);
    }

    public List<ProviderCacheHitVo> consumeProviderCacheHits() {
        List<ProviderCacheHitVo> hits = List.copyOf(providerCacheHits);
        providerCacheHits.clear();
        return hits;
    }
}
