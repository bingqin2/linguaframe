package com.linguaframe.job.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.bo.SubtitlePolishingCacheLookupBo;
import com.linguaframe.job.domain.bo.SubtitlePolishingResultBo;
import com.linguaframe.job.domain.bo.TranslationResultBo;
import com.linguaframe.job.domain.bo.TranslationSegmentBo;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.SubtitlePolishingCacheHitVo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.service.impl.SubtitlePolishingPipelineStage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SubtitlePolishingPipelineStageTests {

    private final LinguaFrameProperties properties = new LinguaFrameProperties();
    private final RecordingSubtitleService subtitleService = new RecordingSubtitleService();
    private final RecordingSubtitleExportService subtitleExportService = new RecordingSubtitleExportService();
    private final RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
    private final RecordingSubtitlePolishingProvider polishingProvider = new RecordingSubtitlePolishingProvider();
    private final RecordingSubtitlePolishingCacheKeyService cacheKeyService = new RecordingSubtitlePolishingCacheKeyService();
    private final RecordingSubtitlePolishingCacheService cacheService = new RecordingSubtitlePolishingCacheService();
    private final RecordingCostBudgetGuardService budgetGuardService = new RecordingCostBudgetGuardService();

    @Test
    void offModeSkipsProviderCacheAndArtifactRewrite() {
        SubtitlePolishingPipelineStage stage = stage();

        stage.execute(context("subtitle-polishing-off-job", "OFF"));

        assertThat(polishingProvider.called).isFalse();
        assertThat(cacheKeyService.called).isFalse();
        assertThat(budgetGuardService.jobId).isNull();
        assertThat(subtitleService.replaceCalls).isZero();
        assertThat(artifactService.commands).isEmpty();
    }

    @Test
    void cacheMissCallsProviderStoresResultReplacesSubtitlesAndRewritesTargetArtifacts() {
        SubtitlePolishingPipelineStage stage = stage();

        stage.execute(context("subtitle-polishing-balanced-job", "BALANCED"));

        assertThat(cacheKeyService.subtitlePolishingMode).isEqualTo("BALANCED");
        assertThat(cacheKeyService.provider).isEqualTo("DEMO");
        assertThat(cacheKeyService.model).isEqualTo("demo-subtitle-polishing");
        assertThat(cacheKeyService.promptVersion).isEqualTo("demo-subtitle-polishing-v1");
        assertThat(budgetGuardService.jobId).isEqualTo("subtitle-polishing-balanced-job");
        assertThat(polishingProvider.called).isTrue();
        assertThat(polishingProvider.subtitlePolishingMode).isEqualTo("BALANCED");
        assertThat(cacheService.storedJobId).isEqualTo("subtitle-polishing-balanced-job");
        assertThat(subtitleService.replacedResult.segments())
                .extracting(TranslationSegmentBo::text)
                .containsExactly("更自然的字幕。");
        assertThat(artifactService.commands)
                .extracting(CreateJobArtifactCommand::type)
                .containsExactly(
                        JobArtifactType.TARGET_SUBTITLE_JSON,
                        JobArtifactType.TARGET_SUBTITLE_SRT,
                        JobArtifactType.TARGET_SUBTITLE_VTT
                );
        assertThat(new String(artifactService.commands.get(0).content(), StandardCharsets.UTF_8))
                .contains("更自然的字幕。");
    }

    @Test
    void cacheHitSkipsProviderAndRecordsProviderCacheHit() {
        cacheService.hit = new SubtitlePolishingCacheHitVo(
                "subtitle-polishing-cache-key-stage",
                "source-job-polishing-cache",
                new SubtitlePolishingResultBo(List.of(new TranslationSegmentBo(0, 0L, 1_000L, "缓存润色字幕。")))
        );
        SubtitlePolishingPipelineStage stage = stage();
        LocalizationJobExecutionContextBo context = context("subtitle-polishing-cache-hit-job", "STRICT");

        stage.execute(context);

        assertThat(polishingProvider.called).isFalse();
        assertThat(budgetGuardService.jobId).isNull();
        assertThat(subtitleService.replacedResult.segments())
                .extracting(TranslationSegmentBo::text)
                .containsExactly("缓存润色字幕。");
        assertThat(context.consumeProviderCacheHits())
                .extracting(hit -> hit.operation() + ":" + hit.cacheKey() + ":" + hit.sourceJobId())
                .containsExactly("SUBTITLE_POLISHING:subtitle-polishing-cache-key-stage:source-job-polishing-cache");
    }

    private SubtitlePolishingPipelineStage stage() {
        properties.getTranslation().setProvider("demo");
        return new SubtitlePolishingPipelineStage(
                properties,
                subtitleService,
                subtitleExportService,
                artifactService,
                polishingProvider,
                cacheKeyService,
                cacheService,
                budgetGuardService
        );
    }

    private LocalizationJobExecutionContextBo context(String jobId, String subtitlePolishingMode) {
        Instant now = Instant.parse("2026-06-28T08:00:00Z");
        return new LocalizationJobExecutionContextBo(
                new LocalizationJobRecord(
                        jobId,
                        "subtitle-polishing-video",
                        "zh-CN",
                        null,
                        "NATURAL",
                        "STANDARD",
                        "[]",
                        "",
                        0,
                        subtitlePolishingMode,
                        LocalizationJobStatus.PROCESSING,
                        now
                ),
                new QueuedLocalizationJobMessage(
                        jobId,
                        "subtitle-polishing-video",
                        "source-videos/subtitle-polishing-video/sample.mp4",
                        "zh-CN",
                        now
                ),
                now
        );
    }

    private static class RecordingSubtitleService implements SubtitleService {

        private int replaceCalls;
        private TranslationResultBo replacedResult;

        @Override
        public List<SubtitleSegmentVo> replaceSubtitles(String jobId, String language, TranslationResultBo result) {
            replaceCalls++;
            replacedResult = result;
            return result.segments().stream()
                    .map(segment -> new SubtitleSegmentVo(language, segment.index(), segment.startMs(), segment.endMs(), segment.text()))
                    .toList();
        }

        @Override
        public List<SubtitleSegmentVo> listSubtitles(String jobId, String language) {
            return List.of(new SubtitleSegmentVo(language, 0, 0L, 1_000L, "直译 字幕。"));
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
            return segments.get(0).text().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] exportSubtitleSrt(List<SubtitleSegmentVo> segments) {
            return segments.get(0).text().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] exportSubtitleVtt(List<SubtitleSegmentVo> segments) {
            return segments.get(0).text().getBytes(StandardCharsets.UTF_8);
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
                    Instant.parse("2026-06-28T08:01:00Z")
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

    private static class RecordingSubtitlePolishingProvider implements SubtitlePolishingProvider {

        private boolean called;
        private String subtitlePolishingMode;

        @Override
        public SubtitlePolishingResultBo polish(
                String jobId,
                String targetLanguage,
                String subtitlePolishingMode,
                List<SubtitleSegmentVo> subtitles
        ) {
            called = true;
            this.subtitlePolishingMode = subtitlePolishingMode;
            return new SubtitlePolishingResultBo(List.of(new TranslationSegmentBo(0, 0L, 1_000L, "更自然的字幕。")));
        }
    }

    private static class RecordingSubtitlePolishingCacheKeyService implements SubtitlePolishingCacheKeyService {

        private boolean called;
        private String provider;
        private String model;
        private String promptVersion;
        private String subtitlePolishingMode;

        @Override
        public SubtitlePolishingCacheLookupBo build(
                String targetLanguage,
                String provider,
                String model,
                String promptVersion,
                String subtitlePolishingMode,
                List<SubtitleSegmentVo> subtitles
        ) {
            called = true;
            this.provider = provider;
            this.model = model;
            this.promptVersion = promptVersion;
            this.subtitlePolishingMode = subtitlePolishingMode;
            return new SubtitlePolishingCacheLookupBo(
                    "subtitle-polishing-cache-key-stage",
                    "subtitle-polishing-source-hash-stage",
                    targetLanguage,
                    provider,
                    model,
                    promptVersion,
                    subtitlePolishingMode
            );
        }
    }

    private static class RecordingSubtitlePolishingCacheService implements SubtitlePolishingCacheService {

        private SubtitlePolishingCacheHitVo hit;
        private String storedJobId;

        @Override
        public Optional<SubtitlePolishingCacheHitVo> findCachedPolishing(SubtitlePolishingCacheLookupBo lookup) {
            return Optional.ofNullable(hit);
        }

        @Override
        public void storePolishing(SubtitlePolishingCacheLookupBo lookup, String jobId, SubtitlePolishingResultBo result) {
            storedJobId = jobId;
        }
    }

    private static class RecordingCostBudgetGuardService implements CostBudgetGuardService {

        private String jobId;

        @Override
        public void assertWithinBudget(String jobId, LocalizationJobStage stage) {
            this.jobId = jobId;
        }
    }
}
