package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.DeliveryManifestVo;
import com.linguaframe.job.domain.vo.DemoRunMatrixJobVo;
import com.linguaframe.job.domain.vo.DemoRunMatrixVo;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.service.DeliveryManifestService;
import com.linguaframe.job.service.DemoRunMatrixService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class DemoRunMatrixServiceImpl implements DemoRunMatrixService {

    private static final int DEFAULT_LIMIT = 8;
    private static final int MAX_LIMIT = 12;

    private final LocalizationJobQueryService queryService;
    private final DeliveryManifestService deliveryManifestService;
    private final Clock clock;

    @Autowired
    public DemoRunMatrixServiceImpl(
            LocalizationJobQueryService queryService,
            DeliveryManifestService deliveryManifestService
    ) {
        this(queryService, deliveryManifestService, Clock.systemUTC());
    }

    public DemoRunMatrixServiceImpl(
            LocalizationJobQueryService queryService,
            DeliveryManifestService deliveryManifestService,
            Clock clock
    ) {
        this.queryService = queryService;
        this.deliveryManifestService = deliveryManifestService;
        this.clock = clock;
    }

    @Override
    public DemoRunMatrixVo buildMatrix(String anchorJobId, Integer limit) {
        LocalizationJobVo anchor = queryService.getJob(anchorJobId);
        int normalizedLimit = normalizeLimit(limit);
        LocalizationJobListVo jobList = queryService.listJobsByVideoId(anchor.videoId(), normalizedLimit);
        List<DemoRunMatrixJobVo> jobs = jobList.jobs().stream()
                .map(summary -> toMatrixJob(summary, queryService.getJob(summary.jobId())))
                .toList();
        return new DemoRunMatrixVo(
                anchorJobId,
                anchor.videoId(),
                Instant.now(clock),
                jobs,
                recommendedBaselineJobId(jobs),
                bestQualityJobId(jobs),
                lowestCostJobId(jobs)
        );
    }

    private DemoRunMatrixJobVo toMatrixJob(LocalizationJobSummaryVo summary, LocalizationJobVo job) {
        JobUsageSummaryVo usage = job.usageSummary();
        JobCacheSummaryVo cache = job.cacheSummary();
        QualityEvaluationVo quality = job.qualityEvaluation();
        DeliveryManifestVo manifest = deliveryManifestService.buildManifest(job.jobId());
        return new DemoRunMatrixJobVo(
                job.jobId(),
                job.videoId(),
                summary.filename(),
                job.targetLanguage(),
                job.demoProfileId(),
                job.ttsVoice(),
                job.translationStyle(),
                job.subtitleStylePreset(),
                job.translationGlossaryEntryCount(),
                job.translationGlossaryHash(),
                job.subtitlePolishingMode(),
                job.status(),
                job.createdAt(),
                job.completedAt(),
                job.failureStage() == null ? null : job.failureStage().name(),
                job.failureReason(),
                job.retryCount(),
                quality == null ? null : quality.score(),
                quality == null ? null : quality.verdict(),
                usage == null ? 0 : usage.modelCallCount(),
                usage == null ? 0 : usage.failedModelCallCount(),
                usage == null || usage.estimatedCostUsd() == null ? BigDecimal.ZERO : usage.estimatedCostUsd(),
                cache == null ? 0 : cache.cacheHitCount(),
                cache == null ? 0 : cache.generatedArtifactCount(),
                cache == null ? 0 : cache.providerCacheHitCount(),
                manifest.handoffReady()
        );
    }

    private String recommendedBaselineJobId(List<DemoRunMatrixJobVo> jobs) {
        return completedJobs(jobs).stream()
                .filter(job -> "quick-baseline".equals(job.demoProfileId()))
                .min(Comparator.comparing(DemoRunMatrixJobVo::createdAt))
                .or(() -> completedJobs(jobs).stream().min(Comparator.comparing(DemoRunMatrixJobVo::estimatedCostUsd)))
                .map(DemoRunMatrixJobVo::jobId)
                .orElse(null);
    }

    private String bestQualityJobId(List<DemoRunMatrixJobVo> jobs) {
        return completedJobs(jobs).stream()
                .filter(job -> job.qualityScore() != null)
                .max(Comparator.comparing(DemoRunMatrixJobVo::qualityScore)
                        .thenComparing(DemoRunMatrixJobVo::createdAt))
                .map(DemoRunMatrixJobVo::jobId)
                .orElse(null);
    }

    private String lowestCostJobId(List<DemoRunMatrixJobVo> jobs) {
        return completedJobs(jobs).stream()
                .min(Comparator.comparing(DemoRunMatrixJobVo::estimatedCostUsd)
                        .thenComparing(DemoRunMatrixJobVo::createdAt))
                .map(DemoRunMatrixJobVo::jobId)
                .orElse(null);
    }

    private List<DemoRunMatrixJobVo> completedJobs(List<DemoRunMatrixJobVo> jobs) {
        return jobs.stream()
                .filter(job -> job.status() == LocalizationJobStatus.COMPLETED)
                .toList();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
