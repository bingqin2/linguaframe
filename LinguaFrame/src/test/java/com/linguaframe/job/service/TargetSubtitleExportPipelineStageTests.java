package com.linguaframe.job.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.bo.TranslationCacheLookupBo;
import com.linguaframe.job.domain.bo.TranslationResultBo;
import com.linguaframe.job.domain.bo.TranslationSegmentBo;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.domain.vo.TranslationCacheHitVo;
import com.linguaframe.job.service.impl.TargetSubtitleExportPipelineStage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TargetSubtitleExportPipelineStageTests {

    private final LinguaFrameProperties properties = new LinguaFrameProperties();
    private final RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
    private final RecordingTranscriptService transcriptService = new RecordingTranscriptService();
    private final RecordingTranslationProvider translationProvider = new RecordingTranslationProvider();
    private final RecordingSubtitleService subtitleService = new RecordingSubtitleService();
    private final RecordingSubtitleExportService subtitleExportService = new RecordingSubtitleExportService();
    private final RecordingCostBudgetGuardService budgetGuardService = new RecordingCostBudgetGuardService();
    private final RecordingTranslationCacheKeyService cacheKeyService = new RecordingTranslationCacheKeyService();
    private final RecordingTranslationCacheService cacheService = new RecordingTranslationCacheService();

    @Test
    void cacheMissCallsProviderStoresResultAndWritesTargetSubtitleArtifacts() {
        properties.getTranslation().setEnabled(true);
        properties.getTranslation().setProvider("demo");
        TargetSubtitleExportPipelineStage stage = stage();

        stage.execute(context("target-subtitle-cache-miss-job"));

        assertThat(cacheKeyService.provider).isEqualTo("DEMO");
        assertThat(cacheKeyService.model).isEqualTo("demo-translation");
        assertThat(cacheKeyService.promptVersion).isEqualTo("demo-translation-v1");
        assertThat(budgetGuardService.jobId).isEqualTo("target-subtitle-cache-miss-job");
        assertThat(translationProvider.called).isTrue();
        assertThat(cacheService.storedJobId).isEqualTo("target-subtitle-cache-miss-job");
        assertThat(cacheService.storedResult).isEqualTo(translationProvider.result);
        assertThat(artifactService.commands)
                .extracting(CreateJobArtifactCommand::type)
                .containsExactly(
                        JobArtifactType.TARGET_SUBTITLE_JSON,
                        JobArtifactType.TARGET_SUBTITLE_SRT,
                        JobArtifactType.TARGET_SUBTITLE_VTT
                );
    }

    @Test
    void cacheHitSkipsProviderAndBudgetGuardAndRecordsProviderCacheHit() {
        properties.getTranslation().setEnabled(true);
        cacheService.hit = new TranslationCacheHitVo(
                "translation-cache-key-stage",
                "source-job-cache-hit",
                new TranslationResultBo(List.of(new TranslationSegmentBo(0, 0L, 1_000L, "缓存翻译")))
        );
        TargetSubtitleExportPipelineStage stage = stage();
        LocalizationJobExecutionContextBo context = context("target-subtitle-cache-hit-job");

        stage.execute(context);

        assertThat(translationProvider.called).isFalse();
        assertThat(budgetGuardService.jobId).isNull();
        assertThat(subtitleService.result).isEqualTo(cacheService.hit.result());
        assertThat(context.consumeProviderCacheHits())
                .extracting(hit -> hit.operation() + ":" + hit.cacheKey() + ":" + hit.sourceJobId())
                .containsExactly("TRANSLATION:translation-cache-key-stage:source-job-cache-hit");
        assertThat(artifactService.commands)
                .extracting(CreateJobArtifactCommand::type)
                .containsExactly(
                        JobArtifactType.TARGET_SUBTITLE_JSON,
                        JobArtifactType.TARGET_SUBTITLE_SRT,
                        JobArtifactType.TARGET_SUBTITLE_VTT
                );
    }

    private TargetSubtitleExportPipelineStage stage() {
        return new TargetSubtitleExportPipelineStage(
                properties,
                artifactService,
                transcriptService,
                translationProvider,
                subtitleService,
                subtitleExportService,
                budgetGuardService,
                cacheKeyService,
                cacheService
        );
    }

    private LocalizationJobExecutionContextBo context(String jobId) {
        Instant now = Instant.parse("2026-06-27T02:30:00Z");
        return new LocalizationJobExecutionContextBo(
                new LocalizationJobRecord(jobId, "target-subtitle-cache-video", "zh-CN", LocalizationJobStatus.PROCESSING, now),
                new QueuedLocalizationJobMessage(
                        jobId,
                        "target-subtitle-cache-video",
                        "source-videos/target-subtitle-cache-video/sample.mp4",
                        "zh-CN",
                        now
                ),
                now
        );
    }

    private static class RecordingTranslationCacheKeyService implements TranslationCacheKeyService {

        private String provider;
        private String model;
        private String promptVersion;

        @Override
        public TranslationCacheLookupBo build(
                String targetLanguage,
                String provider,
                String model,
                String promptVersion,
                String translationStyle,
                String translationGlossaryHash,
                List<TranscriptSegmentVo> segments
        ) {
            this.provider = provider;
            this.model = model;
            this.promptVersion = promptVersion;
            return new TranslationCacheLookupBo(
                    "translation-cache-key-stage",
                    "translation-source-hash-stage",
                    targetLanguage.trim(),
                    provider,
                    model,
                    promptVersion,
                    translationStyle,
                    translationGlossaryHash
            );
        }
    }

    private static class RecordingTranslationCacheService implements TranslationCacheService {

        private TranslationCacheHitVo hit;
        private String storedJobId;
        private TranslationResultBo storedResult;

        @Override
        public Optional<TranslationCacheHitVo> findCachedTranslation(TranslationCacheLookupBo lookup) {
            return Optional.ofNullable(hit);
        }

        @Override
        public void storeTranslation(TranslationCacheLookupBo lookup, String jobId, TranslationResultBo result) {
            storedJobId = jobId;
            storedResult = result;
        }
    }

    private static class RecordingCostBudgetGuardService implements CostBudgetGuardService {

        private String jobId;

        @Override
        public void assertWithinBudget(String jobId, LocalizationJobStage stage) {
            this.jobId = jobId;
        }
    }

    private static class RecordingTranscriptService implements TranscriptService {

        @Override
        public List<TranscriptSegmentVo> replaceTranscript(String jobId, com.linguaframe.job.domain.bo.TranscriptionResultBo result) {
            return List.of();
        }

        @Override
        public List<TranscriptSegmentVo> listTranscript(String jobId) {
            return List.of(new TranscriptSegmentVo(0, 0L, 1_000L, "Hello."));
        }
    }

    private static class RecordingTranslationProvider implements TranslationProvider {

        private boolean called;
        private final TranslationResultBo result = new TranslationResultBo(List.of(
                new TranslationSegmentBo(0, 0L, 1_000L, "你好。")
        ));

        @Override
        public TranslationResultBo translate(String jobId, String targetLanguage, List<TranscriptSegmentVo> transcriptSegments) {
            called = true;
            return result;
        }
    }

    private static class RecordingSubtitleService implements SubtitleService {

        private TranslationResultBo result;

        @Override
        public List<SubtitleSegmentVo> replaceSubtitles(String jobId, String language, TranslationResultBo result) {
            this.result = result;
            return result.segments().stream()
                    .map(segment -> new SubtitleSegmentVo(language, segment.index(), segment.startMs(), segment.endMs(), segment.text()))
                    .toList();
        }

        @Override
        public List<SubtitleSegmentVo> listSubtitles(String jobId, String language) {
            return List.of();
        }
    }

    private static class RecordingSubtitleExportService implements SubtitleExportService {

        @Override
        public byte[] exportTranscriptJson(List<TranscriptSegmentVo> segments) {
            return new byte[0];
        }

        @Override
        public byte[] exportSrt(List<TranscriptSegmentVo> segments) {
            return new byte[0];
        }

        @Override
        public byte[] exportVtt(List<TranscriptSegmentVo> segments) {
            return new byte[0];
        }

        @Override
        public byte[] exportSubtitleJson(List<SubtitleSegmentVo> segments) {
            return "json".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public byte[] exportSubtitleSrt(List<SubtitleSegmentVo> segments) {
            return "srt".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public byte[] exportSubtitleVtt(List<SubtitleSegmentVo> segments) {
            return "vtt".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static class RecordingJobArtifactService implements JobArtifactService {

        private final List<CreateJobArtifactCommand> commands = new ArrayList<>();

        @Override
        public JobArtifactVo createArtifact(CreateJobArtifactCommand command) {
            commands.add(command);
            return new JobArtifactVo(
                    "artifact-" + commands.size(),
                    command.jobId(),
                    command.type(),
                    command.filename(),
                    command.contentType(),
                    command.content().length,
                    "hash-" + commands.size(),
                    false,
                    null,
                    Instant.parse("2026-06-27T02:31:00Z")
            );
        }

        @Override
        public JobArtifactVo createReusedArtifact(String jobId, com.linguaframe.job.domain.entity.JobArtifactRecord source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<JobArtifactVo> listArtifacts(String jobId) {
            return List.of();
        }

        @Override
        public com.linguaframe.job.domain.bo.StoredObjectResourceBo openArtifact(String jobId, String artifactId) {
            throw new UnsupportedOperationException();
        }
    }
}
