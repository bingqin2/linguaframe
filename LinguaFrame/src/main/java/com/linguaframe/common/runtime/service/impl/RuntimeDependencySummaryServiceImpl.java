package com.linguaframe.common.runtime.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.common.runtime.domain.vo.DemoReadinessVo;
import com.linguaframe.common.runtime.domain.vo.FfmpegReadinessVo;
import com.linguaframe.common.runtime.domain.vo.MediaReadinessVo;
import com.linguaframe.common.runtime.domain.vo.NetworkDependencyVo;
import com.linguaframe.common.runtime.domain.vo.ProviderReadinessVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeDependencySummaryVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeFeatureFlagVo;
import com.linguaframe.common.runtime.domain.vo.StorageDependencyVo;
import com.linguaframe.common.runtime.domain.vo.WorkerReadinessVo;
import com.linguaframe.common.runtime.service.RuntimeDependencySummaryService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class RuntimeDependencySummaryServiceImpl implements RuntimeDependencySummaryService {

    private final LinguaFrameProperties properties;

    public RuntimeDependencySummaryServiceImpl(LinguaFrameProperties properties) {
        this.properties = properties;
    }

    @Override
    public RuntimeDependencySummaryVo getSummary() {
        return new RuntimeDependencySummaryVo(
                new NetworkDependencyVo(
                        "mysql",
                        properties.getDatabase().getHost(),
                        properties.getDatabase().getPort()
                ),
                new NetworkDependencyVo(
                        "redis",
                        properties.getRedis().getHost(),
                        properties.getRedis().getPort()
                ),
                new NetworkDependencyVo(
                        "rabbitmq",
                        properties.getRabbitmq().getHost(),
                        properties.getRabbitmq().getPort()
                ),
                new StorageDependencyVo(
                        "minio",
                        properties.getStorage().getEndpoint(),
                        properties.getStorage().getBucket()
                ),
                readiness()
        );
    }

    private DemoReadinessVo readiness() {
        return new DemoReadinessVo(
                properties.getDemo().isAccessGateEnabled(),
                new WorkerReadinessVo(
                        properties.getWorker().isDispatchEnabled(),
                        properties.getWorker().isExecutionEnabled(),
                        properties.getWorker().getRole(),
                        properties.getWorker().getMaxRetries(),
                        properties.getWorker().getDispatchBatchSize(),
                        properties.getWorker().getDispatchIntervalMs()
                ),
                new MediaReadinessVo(
                        properties.getMedia().getMaxFileSizeMb(),
                        properties.getMedia().getMaxDurationSeconds()
                ),
                new FfmpegReadinessVo(
                        properties.getFfmpeg().isAudioEnabled(),
                        properties.getFfmpeg().isBurnInEnabled(),
                        hasText(properties.getFfmpeg().getBinaryPath()),
                        hasText(properties.getFfmpeg().getWorkDir()),
                        properties.getFfmpeg().getAudioTimeoutSeconds(),
                        properties.getFfmpeg().getBurnInTimeoutSeconds()
                ),
                Map.of(
                        "transcription", new ProviderReadinessVo(
                                properties.getTranscription().isEnabled(),
                                properties.getTranscription().getProvider(),
                                properties.getTranscription().getOpenai().getModel(),
                                hasText(properties.getTranscription().getOpenai().getApiKey())
                        ),
                        "translation", new ProviderReadinessVo(
                                properties.getTranslation().isEnabled(),
                                properties.getTranslation().getProvider(),
                                properties.getTranslation().getOpenai().getModel(),
                                hasText(properties.getTranslation().getOpenai().getApiKey())
                        ),
                        "tts", new ProviderReadinessVo(
                                properties.getTts().isEnabled(),
                                properties.getTts().getProvider(),
                                properties.getTts().getOpenai().getModel(),
                                hasText(properties.getTts().getOpenai().getApiKey())
                        ),
                        "evaluation", new ProviderReadinessVo(
                                properties.getEvaluation().isEnabled(),
                                properties.getEvaluation().getProvider(),
                                properties.getEvaluation().getOpenai().getModel(),
                                hasText(properties.getEvaluation().getOpenai().getApiKey())
                        )
                ),
                Map.of(
                        "jobStatusCache", new RuntimeFeatureFlagVo(properties.getJobStatusCache().isEnabled()),
                        "uploadRateLimit", new RuntimeFeatureFlagVo(properties.getRateLimit().isEnabled()),
                        "retentionCleanup", new RuntimeFeatureFlagVo(properties.getRetention().isEnabled()),
                        "costTracking", new RuntimeFeatureFlagVo(properties.getCost().isEnabled()),
                        "budgetGuard", new RuntimeFeatureFlagVo(properties.getCost().isBudgetGuardEnabled())
                )
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
