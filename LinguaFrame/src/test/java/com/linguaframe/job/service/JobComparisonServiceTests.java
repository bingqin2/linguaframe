package com.linguaframe.job.service;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.QualityEvaluationStatus;
import com.linguaframe.job.domain.vo.DeliveryManifestVo;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobComparisonVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.service.impl.JobComparisonServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JobComparisonServiceTests {

    @Test
    void comparesProfilesSettingsQualityCostCacheAndDeliveryReadiness() {
        LocalizationJobVo baseline = job(
                "job-baseline",
                "quick-baseline",
                "NATURAL",
                "STANDARD",
                0,
                "",
                "OFF",
                80,
                "PASS",
                2,
                "0.01250000",
                1,
                3,
                0
        );
        LocalizationJobVo comparison = job(
                "job-showcase",
                "tears-showcase",
                "FORMAL",
                "HIGH_CONTRAST",
                3,
                "abc123",
                "BALANCED",
                91,
                "PASS",
                4,
                "0.02500000",
                2,
                4,
                1
        );
        JobComparisonService service = new JobComparisonServiceImpl(
                new RecordingLocalizationJobQueryService(baseline, comparison),
                new RecordingDeliveryManifestService(true, false)
        );

        JobComparisonVo result = service.compareJobs("job-baseline", "job-showcase");

        assertThat(result.baseline().jobId()).isEqualTo("job-baseline");
        assertThat(result.comparison().jobId()).isEqualTo("job-showcase");
        assertThat(result.baseline().demoProfileId()).isEqualTo("quick-baseline");
        assertThat(result.comparison().demoProfileId()).isEqualTo("tears-showcase");
        assertThat(result.sameVideo()).isTrue();
        assertThat(result.settingDiffs()).extracting(diff -> diff.field())
                .containsExactly(
                        "demoProfileId",
                        "translationStyle",
                        "subtitleStylePreset",
                        "translationGlossary",
                        "subtitlePolishingMode"
                );
        assertThat(result.delta().qualityScore()).isEqualTo(11);
        assertThat(result.delta().modelCallCount()).isEqualTo(2);
        assertThat(result.delta().estimatedCostUsd()).isEqualByComparingTo("0.01250000");
        assertThat(result.delta().artifactCacheHitCount()).isEqualTo(1);
        assertThat(result.delta().providerCacheHitCount()).isEqualTo(1);
        assertThat(result.baseline().handoffReady()).isTrue();
        assertThat(result.comparison().handoffReady()).isFalse();
    }

    @Test
    void markdownComparisonContainsSafeMetadataOnly() {
        LocalizationJobVo baseline = job("job-baseline", null, "NATURAL", "STANDARD", 0, "", "OFF", 80,
                "PASS", 1, "0.01000000", 0, 2, 0);
        LocalizationJobVo comparison = job("job-showcase", "tears-showcase", "FORMAL", "HIGH_CONTRAST", 3,
                "abc123", "BALANCED", 91, "PASS", 3, "0.02500000", 1, 4, 2);
        JobComparisonService service = new JobComparisonServiceImpl(
                new RecordingLocalizationJobQueryService(baseline, comparison),
                new RecordingDeliveryManifestService(false, true)
        );

        String markdown = service.buildMarkdownComparison("job-baseline", "job-showcase");

        assertThat(markdown).contains("# LinguaFrame Job Comparison");
        assertThat(markdown).contains("- Baseline job: job-baseline");
        assertThat(markdown).contains("- Comparison job: job-showcase");
        assertThat(markdown).contains("- Baseline demo profile: manual");
        assertThat(markdown).contains("- Comparison demo profile: tears-showcase");
        assertThat(markdown).contains("- Quality score delta: +11");
        assertThat(markdown).contains("- Estimated cost delta: +$0.01500000");
        assertThat(markdown).doesNotContain("source-videos/");
        assertThat(markdown).doesNotContain("objectKey");
        assertThat(markdown).doesNotContain("raw transcript text");
        assertThat(markdown).doesNotContain("raw subtitle text");
        assertThat(markdown).doesNotContain("provider payload");
        assertThat(markdown).doesNotContain("OPENAI_API_KEY");
    }

    private static LocalizationJobVo job(
            String jobId,
            String demoProfileId,
            String translationStyle,
            String subtitleStylePreset,
            int glossaryCount,
            String glossaryHash,
            String polishingMode,
            int qualityScore,
            String qualityVerdict,
            int modelCalls,
            String estimatedCostUsd,
            int cacheHits,
            int generatedArtifacts,
            int providerCacheHits
    ) {
        return new LocalizationJobVo(
                jobId,
                "shared-video",
                "zh-CN",
                "",
                translationStyle,
                subtitleStylePreset,
                glossaryCount,
                glossaryHash,
                polishingMode,
                demoProfileId,
                LocalizationJobStatus.COMPLETED,
                Instant.parse("2026-06-28T00:00:00Z"),
                Instant.parse("2026-06-28T00:00:01Z"),
                Instant.parse("2026-06-28T00:00:10Z"),
                null,
                null,
                null,
                0,
                null,
                0,
                null,
                List.of(),
                new JobUsageSummaryVo(modelCalls, 0, 1200L, new BigDecimal(estimatedCostUsd), 100, 80, null, 500),
                new JobCacheSummaryVo(cacheHits, generatedArtifacts, providerCacheHits),
                List.of(),
                new QualityEvaluationVo(
                        "eval-" + jobId,
                        jobId,
                        "zh-CN",
                        qualityScore,
                        qualityVerdict,
                        qualityScore,
                        qualityScore,
                        qualityScore,
                        qualityScore,
                        List.of(),
                        List.of(),
                        QualityEvaluationStatus.SUCCEEDED,
                        null,
                        Instant.parse("2026-06-28T00:00:20Z")
                ),
                null,
                null
        );
    }

    private record RecordingLocalizationJobQueryService(
            LocalizationJobVo baseline,
            LocalizationJobVo comparison
    ) implements LocalizationJobQueryService {

        @Override
        public LocalizationJobListVo listJobs(LocalizationJobStatus status, Integer limit, Integer offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LocalizationJobVo getJob(String jobId) {
            if (baseline.jobId().equals(jobId)) {
                return baseline;
            }
            if (comparison.jobId().equals(jobId)) {
                return comparison;
            }
            throw new java.util.NoSuchElementException(jobId);
        }

        @Override
        public JobDiagnosticsReportVo getDiagnosticsReport(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private record RecordingDeliveryManifestService(
            boolean baselineHandoffReady,
            boolean comparisonHandoffReady
    ) implements DeliveryManifestService {

        @Override
        public DeliveryManifestVo buildManifest(String jobId) {
            return new DeliveryManifestVo(
                    jobId,
                    "shared-video",
                    "zh-CN",
                    "STANDARD",
                    0,
                    "",
                    "OFF",
                    null,
                    LocalizationJobStatus.COMPLETED,
                    Instant.parse("2026-06-28T00:00:30Z"),
                    jobId.equals("job-baseline") ? baselineHandoffReady : comparisonHandoffReady,
                    3,
                    false,
                    2,
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        @Override
        public String buildMarkdownManifest(String jobId) {
            throw new UnsupportedOperationException();
        }
    }
}
