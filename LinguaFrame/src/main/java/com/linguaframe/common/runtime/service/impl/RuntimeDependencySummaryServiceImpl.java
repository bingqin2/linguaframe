package com.linguaframe.common.runtime.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.common.runtime.domain.vo.BudgetReadinessVo;
import com.linguaframe.common.runtime.domain.vo.DemoReadinessVo;
import com.linguaframe.common.runtime.domain.vo.FfmpegReadinessVo;
import com.linguaframe.common.runtime.domain.vo.MediaReadinessVo;
import com.linguaframe.common.runtime.domain.vo.NetworkDependencyVo;
import com.linguaframe.common.runtime.domain.vo.OwnerQuotaReadinessVo;
import com.linguaframe.common.runtime.domain.vo.ProviderReadinessVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeContractVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeDependencySummaryVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeFeatureFlagVo;
import com.linguaframe.common.runtime.domain.vo.StorageDependencyVo;
import com.linguaframe.common.runtime.domain.vo.WorkerReadinessVo;
import com.linguaframe.common.runtime.service.RuntimeDependencySummaryService;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RuntimeDependencySummaryServiceImpl implements RuntimeDependencySummaryService {

    private static final Pattern MIGRATION_VERSION_PATTERN = Pattern.compile("V(\\d+)__.+\\.sql");
    private static final List<String> REQUIRED_ROUTES = List.of(
            "/api/runtime/dependencies",
            "/api/runtime/live-checks",
            "/api/demo-run-profiles",
            "/api/media/uploads/preflight",
            "/api/media/uploads/readiness",
            "/api/media/uploads",
            "/api/media/uploads/{videoId}/source/download",
            "/api/jobs/{jobId}",
            "/api/jobs/{jobId}/diagnostics/download",
            "/api/jobs/{jobId}/evidence/markdown/download",
            "/api/jobs/{jobId}/evidence/bundle/download",
            "/api/jobs/{jobId}/quality-evaluation/evidence/markdown/download",
            "/api/jobs/{jobId}/demo-run-package/download",
            "/api/jobs/{jobId}/ai-audit-package/download",
            "/api/jobs/{jobId}/demo-run-matrix",
            "/api/jobs/{jobId}/demo-presenter-pack",
            "/api/jobs/{jobId}/comparison/{comparisonJobId}",
            "/api/jobs/{jobId}/comparison/{comparisonJobId}/markdown/download",
            "/api/jobs/{jobId}/delivery-manifest",
            "/api/jobs/{jobId}/delivery-manifest/markdown/download",
            "/api/jobs/{jobId}/handoff-package/download",
            "/api/jobs/{jobId}/artifacts/archive/download",
            "/api/operator/private-demo/operations",
            "/api/operator/private-demo/launch-rehearsal"
    );

    private final LinguaFrameProperties properties;
    private final Optional<BuildProperties> buildProperties;

    public RuntimeDependencySummaryServiceImpl(
            LinguaFrameProperties properties,
            Optional<BuildProperties> buildProperties
    ) {
        this.properties = properties;
        this.buildProperties = buildProperties;
    }

    @Override
    public RuntimeDependencySummaryVo getSummary() {
        return new RuntimeDependencySummaryVo(
                runtime(),
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

    private RuntimeContractVo runtime() {
        return new RuntimeContractVo(
                buildProperties.map(BuildProperties::getVersion).orElse("0.0.1-SNAPSHOT"),
                latestMigrationVersion(),
                REQUIRED_ROUTES
        );
    }

    private int latestMigrationVersion() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            int latest = 0;
            Resource[] resources = resolver.getResources("classpath*:db/migration/V*__*.sql");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) {
                    continue;
                }
                Matcher matcher = MIGRATION_VERSION_PATTERN.matcher(filename);
                if (matcher.matches()) {
                    latest = Math.max(latest, Integer.parseInt(matcher.group(1)));
                }
            }
            return latest;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to inspect bundled Flyway migrations", ex);
        }
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
                new BudgetReadinessVo(
                        properties.getCost().isBudgetGuardEnabled(),
                        properties.getCost().getMaxJobCostUsd(),
                        properties.getCost().isDailyBudgetGuardEnabled(),
                        properties.getCost().getMaxDailyCostUsd(),
                        properties.getCost().getBudgetIdentity(),
                        properties.getCost().isEnabled()
                ),
                new OwnerQuotaReadinessVo(
                        properties.getOwnerQuota().isEnabled(),
                        properties.getOwnerQuota().getMaxActiveJobs(),
                        properties.getOwnerQuota().getMaxQueuedJobs(),
                        properties.getOwnerQuota().isDailyBudgetGuardEnabled(),
                        properties.getOwnerQuota().getMaxDailyCostUsd()
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
                        "budgetGuard", new RuntimeFeatureFlagVo(properties.getCost().isBudgetGuardEnabled()),
                        "dailyBudgetGuard", new RuntimeFeatureFlagVo(properties.getCost().isDailyBudgetGuardEnabled()),
                        "ownerQuota", new RuntimeFeatureFlagVo(properties.getOwnerQuota().isEnabled())
                )
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
