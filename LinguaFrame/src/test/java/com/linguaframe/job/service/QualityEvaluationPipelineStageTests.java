package com.linguaframe.job.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.bo.QualityEvaluationCacheLookupBo;
import com.linguaframe.job.domain.bo.QualityEvaluationResultBo;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.QualityEvaluationStatus;
import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.domain.vo.QualityEvaluationCacheHitVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.service.impl.InMemoryPromptTemplateRegistry;
import com.linguaframe.job.service.impl.QualityEvaluationPipelineStage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class QualityEvaluationPipelineStageTests {

    private final LinguaFrameProperties properties = new LinguaFrameProperties();
    private final RecordingTranscriptService transcriptService = new RecordingTranscriptService();
    private final RecordingSubtitleService subtitleService = new RecordingSubtitleService();
    private final RecordingQualityEvaluationService qualityEvaluationService = new RecordingQualityEvaluationService();
    private final RecordingCostBudgetGuardService budgetGuardService = new RecordingCostBudgetGuardService();
    private final RecordingQualityEvaluationCacheKeyService cacheKeyService = new RecordingQualityEvaluationCacheKeyService();
    private final RecordingQualityEvaluationCacheService cacheService = new RecordingQualityEvaluationCacheService();

    @Test
    void cacheHitSkipsBudgetAndProviderBackedEvaluation() {
        properties.getEvaluation().setEnabled(true);
        properties.getEvaluation().setProvider("openai");
        properties.getEvaluation().getOpenai().setModel("gpt-4o-mini");
        cacheService.hit = new QualityEvaluationCacheHitVo(
                "quality-evaluation-cache-key-stage",
                "source-job-quality-cache",
                cachedResult()
        );
        LocalizationJobExecutionContextBo context = context("quality-cache-hit-job");

        stage().execute(context);

        assertThat(cacheKeyService.language).isEqualTo("zh-CN");
        assertThat(cacheKeyService.provider).isEqualTo("OPENAI");
        assertThat(cacheKeyService.model).isEqualTo("gpt-4o-mini");
        assertThat(cacheKeyService.promptVersion).isEqualTo("openai-translation-quality-evaluation-v1");
        assertThat(budgetGuardService.jobId).isNull();
        assertThat(qualityEvaluationService.evaluateCalls).isZero();
        assertThat(qualityEvaluationService.cachedJobId).isEqualTo("quality-cache-hit-job");
        assertThat(qualityEvaluationService.cachedResult).isEqualTo(cachedResult());
        assertThat(context.consumeProviderCacheHits())
                .singleElement()
                .satisfies(hit -> {
                    assertThat(hit.operation()).isEqualTo(ModelCallOperation.EVALUATION);
                    assertThat(hit.cacheKey()).isEqualTo("quality-evaluation-cache-key-stage");
                    assertThat(hit.sourceJobId()).isEqualTo("source-job-quality-cache");
                });
    }

    @Test
    void cacheMissCallsBudgetAndProviderBackedEvaluationThenStoresResult() {
        properties.getEvaluation().setEnabled(true);
        properties.getEvaluation().setProvider("demo");

        stage().execute(context("quality-cache-miss-job"));

        assertThat(cacheKeyService.provider).isEqualTo("DEMO");
        assertThat(cacheKeyService.model).isEqualTo("demo-quality-evaluation");
        assertThat(cacheKeyService.promptVersion).isEqualTo("demo-quality-evaluation-v1");
        assertThat(budgetGuardService.jobId).isEqualTo("quality-cache-miss-job");
        assertThat(budgetGuardService.stage).isEqualTo(LocalizationJobStage.TRANSLATION_QUALITY_EVALUATION);
        assertThat(qualityEvaluationService.evaluateCalls).isEqualTo(1);
        assertThat(cacheService.storedJobId).isEqualTo("quality-cache-miss-job");
        assertThat(cacheService.storedResult.score()).isEqualTo(92);
    }

    private QualityEvaluationPipelineStage stage() {
        return new QualityEvaluationPipelineStage(
                properties,
                transcriptService,
                subtitleService,
                qualityEvaluationService,
                budgetGuardService,
                cacheKeyService,
                cacheService,
                new InMemoryPromptTemplateRegistry()
        );
    }

    private LocalizationJobExecutionContextBo context(String jobId) {
        Instant now = Instant.parse("2026-06-27T07:00:00Z");
        return new LocalizationJobExecutionContextBo(
                new LocalizationJobRecord(jobId, "quality-cache-video", "zh-CN", LocalizationJobStatus.PROCESSING, now),
                new QueuedLocalizationJobMessage(
                        jobId,
                        "quality-cache-video",
                        "source-videos/quality-cache-video/sample.mp4",
                        "zh-CN",
                        now
                ),
                now
        );
    }

    private QualityEvaluationResultBo cachedResult() {
        return new QualityEvaluationResultBo(88, "CACHED_GOOD", 89, 87, 90, 86, List.of("Cached issue"), List.of("Cached fix"));
    }

    private static class RecordingQualityEvaluationCacheKeyService implements QualityEvaluationCacheKeyService {

        private String language;
        private String provider;
        private String model;
        private String promptVersion;

        @Override
        public QualityEvaluationCacheLookupBo build(
                String language,
                String provider,
                String model,
                String promptVersion,
                List<TranscriptSegmentVo> sourceSegments,
                List<SubtitleSegmentVo> targetSegments
        ) {
            this.language = language;
            this.provider = provider;
            this.model = model;
            this.promptVersion = promptVersion;
            return new QualityEvaluationCacheLookupBo(
                    "quality-evaluation-cache-key-stage",
                    "source-hash-stage",
                    "target-hash-stage",
                    language,
                    provider,
                    model,
                    promptVersion
            );
        }
    }

    private static class RecordingQualityEvaluationCacheService implements QualityEvaluationCacheService {

        private QualityEvaluationCacheHitVo hit;
        private String storedJobId;
        private QualityEvaluationResultBo storedResult;

        @Override
        public Optional<QualityEvaluationCacheHitVo> findCachedEvaluation(QualityEvaluationCacheLookupBo lookup) {
            return Optional.ofNullable(hit);
        }

        @Override
        public void storeEvaluation(QualityEvaluationCacheLookupBo lookup, String jobId, QualityEvaluationResultBo result) {
            storedJobId = jobId;
            storedResult = result;
        }
    }

    private static class RecordingCostBudgetGuardService implements CostBudgetGuardService {

        private String jobId;
        private LocalizationJobStage stage;

        @Override
        public void assertWithinBudget(String jobId, LocalizationJobStage stage) {
            this.jobId = jobId;
            this.stage = stage;
        }
    }

    private static class RecordingQualityEvaluationService implements QualityEvaluationService {

        private int evaluateCalls;
        private String cachedJobId;
        private QualityEvaluationResultBo cachedResult;

        @Override
        public QualityEvaluationVo evaluateAndStore(
                String jobId,
                String language,
                List<TranscriptSegmentVo> sourceSegments,
                List<SubtitleSegmentVo> targetSegments
        ) {
            evaluateCalls++;
            return new QualityEvaluationVo(
                    "quality-evaluation-miss",
                    jobId,
                    language,
                    92,
                    "GOOD",
                    95,
                    92,
                    94,
                    88,
                    List.of("No blocking issue."),
                    List.of("Review terminology."),
                    QualityEvaluationStatus.SUCCEEDED,
                    null,
                    Instant.parse("2026-06-27T07:00:10Z")
            );
        }

        @Override
        public QualityEvaluationVo storeCachedEvaluation(
                String jobId,
                String language,
                QualityEvaluationResultBo result
        ) {
            cachedJobId = jobId;
            cachedResult = result;
            return new QualityEvaluationVo(
                    "quality-evaluation-cache-hit",
                    jobId,
                    language,
                    result.score(),
                    result.verdict(),
                    result.completeness(),
                    result.readability(),
                    result.timingPreservation(),
                    result.naturalness(),
                    result.issues(),
                    result.suggestedFixes(),
                    QualityEvaluationStatus.SUCCEEDED,
                    null,
                    Instant.parse("2026-06-27T07:00:10Z")
            );
        }

        @Override
        public Optional<QualityEvaluationVo> latestForJob(String jobId) {
            return Optional.empty();
        }
    }

    private static class RecordingTranscriptService implements TranscriptService {

        @Override
        public List<TranscriptSegmentVo> replaceTranscript(String jobId, com.linguaframe.job.domain.bo.TranscriptionResultBo result) {
            return List.of();
        }

        @Override
        public List<TranscriptSegmentVo> listTranscript(String jobId) {
            return List.of(new TranscriptSegmentVo(0, 0L, 1_000L, "Hello from LinguaFrame."));
        }
    }

    private static class RecordingSubtitleService implements SubtitleService {

        @Override
        public List<SubtitleSegmentVo> replaceSubtitles(
                String jobId,
                String language,
                com.linguaframe.job.domain.bo.TranslationResultBo result
        ) {
            return List.of();
        }

        @Override
        public List<SubtitleSegmentVo> listSubtitles(String jobId, String language) {
            return List.of(new SubtitleSegmentVo(language, 0, 0L, 1_000L, "LinguaFrame 向你问好。"));
        }
    }
}
