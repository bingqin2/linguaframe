package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.vo.DeliveryManifestVo;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobComparisonDeltaVo;
import com.linguaframe.job.domain.vo.JobComparisonJobVo;
import com.linguaframe.job.domain.vo.JobComparisonSettingDiffVo;
import com.linguaframe.job.domain.vo.JobComparisonVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.service.DeliveryManifestService;
import com.linguaframe.job.service.JobComparisonService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class JobComparisonServiceImpl implements JobComparisonService {

    private final LocalizationJobQueryService queryService;
    private final DeliveryManifestService deliveryManifestService;
    private final Clock clock;

    @Autowired
    public JobComparisonServiceImpl(
            LocalizationJobQueryService queryService,
            DeliveryManifestService deliveryManifestService
    ) {
        this(queryService, deliveryManifestService, Clock.systemUTC());
    }

    public JobComparisonServiceImpl(
            LocalizationJobQueryService queryService,
            DeliveryManifestService deliveryManifestService,
            Clock clock
    ) {
        this.queryService = queryService;
        this.deliveryManifestService = deliveryManifestService;
        this.clock = clock;
    }

    @Override
    public JobComparisonVo compareJobs(String baselineJobId, String comparisonJobId) {
        LocalizationJobVo baseline = queryService.getJob(baselineJobId);
        LocalizationJobVo comparison = queryService.getJob(comparisonJobId);
        JobComparisonJobVo baselineView = toComparisonJob(baseline, deliveryManifestService.buildManifest(baselineJobId));
        JobComparisonJobVo comparisonView = toComparisonJob(comparison, deliveryManifestService.buildManifest(comparisonJobId));
        return new JobComparisonVo(
                baselineJobId,
                comparisonJobId,
                Objects.equals(baseline.videoId(), comparison.videoId()),
                Instant.now(clock),
                baselineView,
                comparisonView,
                delta(baselineView, comparisonView),
                settingDiffs(baselineView, comparisonView)
        );
    }

    @Override
    public String buildMarkdownComparison(String baselineJobId, String comparisonJobId) {
        JobComparisonVo comparison = compareJobs(baselineJobId, comparisonJobId);
        List<String> lines = new ArrayList<>();
        lines.add("# LinguaFrame Job Comparison");
        lines.add("");
        lines.add("- Baseline job: " + comparison.baselineJobId());
        lines.add("- Comparison job: " + comparison.comparisonJobId());
        lines.add("- Same source video: " + comparison.sameVideo());
        lines.add("- Baseline demo profile: " + display(comparison.baseline().demoProfileId(), "manual"));
        lines.add("- Comparison demo profile: " + display(comparison.comparison().demoProfileId(), "manual"));
        lines.add("- Quality score delta: " + formatSigned(comparison.delta().qualityScore()));
        lines.add("- Model-call delta: " + formatSigned(comparison.delta().modelCallCount()));
        lines.add("- Estimated cost delta: " + formatSignedCost(comparison.delta().estimatedCostUsd()));
        lines.add("- Artifact cache-hit delta: " + formatSigned(comparison.delta().artifactCacheHitCount()));
        lines.add("- Provider cache-hit delta: " + formatSigned(comparison.delta().providerCacheHitCount()));
        lines.add("- Baseline handoff ready: " + comparison.baseline().handoffReady());
        lines.add("- Comparison handoff ready: " + comparison.comparison().handoffReady());
        lines.add("");
        lines.add("Setting differences:");
        if (comparison.settingDiffs().isEmpty()) {
            lines.add("- None");
        } else {
            for (JobComparisonSettingDiffVo diff : comparison.settingDiffs()) {
                lines.add("- " + diff.field() + ": " + diff.baselineValue() + " -> " + diff.comparisonValue());
            }
        }
        return String.join("\n", lines);
    }

    private JobComparisonJobVo toComparisonJob(LocalizationJobVo job, DeliveryManifestVo manifest) {
        JobUsageSummaryVo usage = job.usageSummary();
        JobCacheSummaryVo cache = job.cacheSummary();
        QualityEvaluationVo quality = job.qualityEvaluation();
        return new JobComparisonJobVo(
                job.jobId(),
                job.videoId(),
                job.targetLanguage(),
                job.demoProfileId(),
                job.ttsVoice(),
                job.translationStyle(),
                job.subtitleStylePreset(),
                job.translationGlossaryEntryCount(),
                job.translationGlossaryHash(),
                job.subtitlePolishingMode(),
                job.status(),
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

    private JobComparisonDeltaVo delta(JobComparisonJobVo baseline, JobComparisonJobVo comparison) {
        return new JobComparisonDeltaVo(
                comparison.qualityScore() == null || baseline.qualityScore() == null
                        ? null
                        : comparison.qualityScore() - baseline.qualityScore(),
                comparison.modelCallCount() - baseline.modelCallCount(),
                comparison.estimatedCostUsd().subtract(baseline.estimatedCostUsd()),
                comparison.artifactCacheHitCount() - baseline.artifactCacheHitCount(),
                comparison.generatedArtifactCount() - baseline.generatedArtifactCount(),
                comparison.providerCacheHitCount() - baseline.providerCacheHitCount()
        );
    }

    private List<JobComparisonSettingDiffVo> settingDiffs(JobComparisonJobVo baseline, JobComparisonJobVo comparison) {
        List<JobComparisonSettingDiffVo> diffs = new ArrayList<>();
        addDiff(diffs, "demoProfileId", display(baseline.demoProfileId(), "manual"), display(comparison.demoProfileId(), "manual"));
        addDiff(diffs, "targetLanguage", baseline.targetLanguage(), comparison.targetLanguage());
        addDiff(diffs, "ttsVoice", display(baseline.ttsVoice(), "Default voice"), display(comparison.ttsVoice(), "Default voice"));
        addDiff(diffs, "translationStyle", baseline.translationStyle(), comparison.translationStyle());
        addDiff(diffs, "subtitleStylePreset", baseline.subtitleStylePreset(), comparison.subtitleStylePreset());
        addDiff(
                diffs,
                "translationGlossary",
                glossaryLabel(baseline.translationGlossaryEntryCount(), baseline.translationGlossaryHash()),
                glossaryLabel(comparison.translationGlossaryEntryCount(), comparison.translationGlossaryHash())
        );
        addDiff(diffs, "subtitlePolishingMode", baseline.subtitlePolishingMode(), comparison.subtitlePolishingMode());
        return diffs;
    }

    private void addDiff(List<JobComparisonSettingDiffVo> diffs, String field, String baseline, String comparison) {
        if (!Objects.equals(baseline, comparison)) {
            diffs.add(new JobComparisonSettingDiffVo(field, baseline, comparison));
        }
    }

    private String glossaryLabel(int entryCount, String hash) {
        if (entryCount <= 0) {
            return "none";
        }
        return entryCount + " entries / " + display(hash, "no hash");
    }

    private String display(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String formatSigned(Integer value) {
        if (value == null) {
            return "N/A";
        }
        return formatSigned(value.intValue());
    }

    private String formatSigned(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private String formatSignedCost(BigDecimal value) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : value;
        String sign = normalized.signum() > 0 ? "+" : normalized.signum() < 0 ? "-" : "";
        return sign + "$" + normalized.abs().setScale(8, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
