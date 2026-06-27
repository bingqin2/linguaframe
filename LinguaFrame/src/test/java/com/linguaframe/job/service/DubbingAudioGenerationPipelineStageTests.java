package com.linguaframe.job.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.bo.TranslationResultBo;
import com.linguaframe.job.domain.bo.TtsCacheLookupBo;
import com.linguaframe.job.domain.bo.TtsRequestBo;
import com.linguaframe.job.domain.bo.TtsResultBo;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.exception.CostBudgetExceededException;
import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TtsCacheHitVo;
import com.linguaframe.job.service.impl.DubbingAudioGenerationPipelineStage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DubbingAudioGenerationPipelineStageTests {

    private final LinguaFrameProperties properties = new LinguaFrameProperties();
    private final RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
    private final RecordingSubtitleService subtitleService = new RecordingSubtitleService(List.of(
            new SubtitleSegmentVo("zh-CN", 1, 1_200L, 2_400L, " 第二句翻译 "),
            new SubtitleSegmentVo("zh-CN", 0, 0L, 1_200L, "第一句翻译")
    ));
    private final RecordingTtsProvider ttsProvider = new RecordingTtsProvider();

    @Test
    void stageReturnsDubbingAudioGeneration() {
        DubbingAudioGenerationPipelineStage stage = stage();

        assertThat(stage.stage()).isEqualTo(LocalizationJobStage.DUBBING_AUDIO_GENERATION);
    }

    @Test
    void skipsWhenTtsIsDisabled() {
        properties.getTts().setEnabled(false);

        stage().execute(context());

        assertThat(ttsProvider.request).isNull();
        assertThat(artifactService.commands).isEmpty();
    }

    @Test
    void createsDubbingAudioArtifactFromTargetSubtitles() {
        properties.getTts().setEnabled(true);

        stage().execute(context());

        assertThat(ttsProvider.request.jobId()).isEqualTo("dubbing-job-1");
        assertThat(ttsProvider.request.language()).isEqualTo("zh-CN");
        assertThat(ttsProvider.request.voice()).isEqualTo("demo-voice");
        assertThat(ttsProvider.request.text()).isEqualTo("第一句翻译\n第二句翻译");
        assertThat(artifactService.commands).hasSize(1);
        CreateJobArtifactCommand command = artifactService.commands.getFirst();
        assertThat(command.jobId()).isEqualTo("dubbing-job-1");
        assertThat(command.type()).isEqualTo(JobArtifactType.DUBBING_AUDIO);
        assertThat(command.filename()).isEqualTo("dubbing-audio.mp3");
        assertThat(command.contentType()).isEqualTo("audio/mpeg");
        assertThat(command.content()).containsExactly(9, 8, 7);
    }

    @Test
    void reusesCachedDubbingAudioBeforeCallingTtsProvider() {
        properties.getTts().setEnabled(true);
        RecordingArtifactCacheService cacheService = new RecordingArtifactCacheService(
                new JobArtifactVo(
                        "cached-dubbing-artifact",
                        "dubbing-job-1",
                        JobArtifactType.DUBBING_AUDIO,
                        "dubbing-audio.mp3",
                        "audio/mpeg",
                        123L,
                        "cached-dubbing-hash",
                        true,
                        "source-dubbing-artifact",
                        Instant.parse("2026-06-27T09:20:00Z")
                )
        );
        DubbingAudioGenerationPipelineStage stage = new DubbingAudioGenerationPipelineStage(
                properties,
                artifactService,
                subtitleService,
                ttsProvider,
                new NoopCostBudgetGuardService(),
                cacheService
        );
        LocalizationJobExecutionContextBo context = context();

        stage.execute(context);

        assertThat(cacheService.requestedTypes).containsExactly(JobArtifactType.DUBBING_AUDIO);
        assertThat(context.consumeCacheHits()).containsExactly(cacheService.artifact);
        assertThat(ttsProvider.request).isNull();
        assertThat(artifactService.commands).isEmpty();
    }

    @Test
    void reusesCachedTtsProviderResultBeforeCallingTtsProvider() {
        properties.getTts().setEnabled(true);
        properties.getTts().setProvider("openai");
        properties.getTts().getOpenai().setModel("gpt-4o-mini-tts");
        properties.getTts().getOpenai().setVoice("alloy");
        RecordingTtsCacheKeyService cacheKeyService = new RecordingTtsCacheKeyService();
        RecordingTtsCacheService cacheService = new RecordingTtsCacheService(new TtsCacheHitVo(
                "tts-cache-key-hit",
                "source-job-tts",
                new TtsResultBo(new byte[] {4, 5, 6}, "cached-dubbing.mp3", "audio/mpeg")
        ));
        RecordingCostBudgetGuardService budgetGuardService = new RecordingCostBudgetGuardService();
        DubbingAudioGenerationPipelineStage stage = new DubbingAudioGenerationPipelineStage(
                properties,
                artifactService,
                subtitleService,
                ttsProvider,
                budgetGuardService,
                new EmptyArtifactCacheService(),
                cacheKeyService,
                cacheService
        );
        LocalizationJobExecutionContextBo context = context();

        stage.execute(context);

        assertThat(cacheKeyService.text).isEqualTo("第一句翻译\n第二句翻译");
        assertThat(cacheKeyService.provider).isEqualTo("OPENAI");
        assertThat(cacheKeyService.model).isEqualTo("gpt-4o-mini-tts");
        assertThat(cacheKeyService.voice).isEqualTo("alloy");
        assertThat(cacheService.lookup.cacheKey()).isEqualTo("tts-cache-key-hit");
        assertThat(ttsProvider.request).isNull();
        assertThat(budgetGuardService.calls).isZero();
        assertThat(artifactService.commands).hasSize(1);
        CreateJobArtifactCommand command = artifactService.commands.getFirst();
        assertThat(command.type()).isEqualTo(JobArtifactType.DUBBING_AUDIO);
        assertThat(command.filename()).isEqualTo("cached-dubbing.mp3");
        assertThat(command.content()).containsExactly(4, 5, 6);
        assertThat(context.consumeProviderCacheHits())
                .singleElement()
                .satisfies(hit -> {
                    assertThat(hit.operation()).isEqualTo(ModelCallOperation.TTS);
                    assertThat(hit.cacheKey()).isEqualTo("tts-cache-key-hit");
                    assertThat(hit.sourceJobId()).isEqualTo("source-job-tts");
                });
    }

    @Test
    void sendsJobTtsVoiceToProviderAndProviderCacheKey() {
        properties.getTts().setEnabled(true);
        properties.getTts().setProvider("openai");
        properties.getTts().getOpenai().setModel("gpt-4o-mini-tts");
        properties.getTts().getOpenai().setVoice("alloy");
        RecordingTtsCacheKeyService cacheKeyService = new RecordingTtsCacheKeyService();
        RecordingTtsCacheService cacheService = new RecordingTtsCacheService(null);
        DubbingAudioGenerationPipelineStage stage = new DubbingAudioGenerationPipelineStage(
                properties,
                artifactService,
                subtitleService,
                ttsProvider,
                new NoopCostBudgetGuardService(),
                new EmptyArtifactCacheService(),
                cacheKeyService,
                cacheService
        );

        stage.execute(contextWithTtsVoice("verse"));

        assertThat(ttsProvider.request.voice()).isEqualTo("verse");
        assertThat(cacheKeyService.voice).isEqualTo("verse");
        assertThat(cacheService.storedLookup.voice()).isEqualTo("verse");
    }

    @Test
    void fallsBackToConfiguredVoiceWhenJobHasNoTtsVoice() {
        properties.getTts().setEnabled(true);
        properties.getTts().setProvider("openai");
        properties.getTts().getOpenai().setModel("gpt-4o-mini-tts");
        properties.getTts().getOpenai().setVoice("alloy");
        RecordingTtsCacheKeyService cacheKeyService = new RecordingTtsCacheKeyService();
        RecordingTtsCacheService cacheService = new RecordingTtsCacheService(null);
        DubbingAudioGenerationPipelineStage stage = new DubbingAudioGenerationPipelineStage(
                properties,
                artifactService,
                subtitleService,
                ttsProvider,
                new NoopCostBudgetGuardService(),
                new EmptyArtifactCacheService(),
                cacheKeyService,
                cacheService
        );

        stage.execute(context());

        assertThat(ttsProvider.request.voice()).isEqualTo("alloy");
        assertThat(cacheKeyService.voice).isEqualTo("alloy");
    }

    @Test
    void artifactCacheHitSkipsTtsProviderCacheLookup() {
        properties.getTts().setEnabled(true);
        RecordingArtifactCacheService artifactCacheService = new RecordingArtifactCacheService(
                new JobArtifactVo(
                        "cached-dubbing-artifact",
                        "dubbing-job-1",
                        JobArtifactType.DUBBING_AUDIO,
                        "dubbing-audio.mp3",
                        "audio/mpeg",
                        123L,
                        "cached-dubbing-hash",
                        true,
                        "source-dubbing-artifact",
                        Instant.parse("2026-06-27T09:20:00Z")
                )
        );
        RecordingTtsCacheKeyService cacheKeyService = new RecordingTtsCacheKeyService();
        RecordingTtsCacheService cacheService = new RecordingTtsCacheService(null);
        DubbingAudioGenerationPipelineStage stage = new DubbingAudioGenerationPipelineStage(
                properties,
                artifactService,
                subtitleService,
                ttsProvider,
                new NoopCostBudgetGuardService(),
                artifactCacheService,
                cacheKeyService,
                cacheService
        );

        stage.execute(context());

        assertThat(cacheKeyService.text).isNull();
        assertThat(cacheService.lookup).isNull();
        assertThat(ttsProvider.request).isNull();
        assertThat(artifactService.commands).isEmpty();
    }

    @Test
    void storesTtsProviderResultAfterCacheMiss() {
        properties.getTts().setEnabled(true);
        RecordingTtsCacheKeyService cacheKeyService = new RecordingTtsCacheKeyService();
        RecordingTtsCacheService cacheService = new RecordingTtsCacheService(null);
        DubbingAudioGenerationPipelineStage stage = new DubbingAudioGenerationPipelineStage(
                properties,
                artifactService,
                subtitleService,
                ttsProvider,
                new NoopCostBudgetGuardService(),
                new EmptyArtifactCacheService(),
                cacheKeyService,
                cacheService
        );

        stage.execute(context());

        assertThat(ttsProvider.request).isNotNull();
        assertThat(cacheService.storedJobId).isEqualTo("dubbing-job-1");
        assertThat(cacheService.storedLookup.cacheKey()).isEqualTo("tts-cache-key-hit");
        assertThat(cacheService.storedResult.audioContent()).containsExactly(9, 8, 7);
    }

    @Test
    void failsWhenEnabledAndTargetSubtitlesAreMissing() {
        properties.getTts().setEnabled(true);
        DubbingAudioGenerationPipelineStage stage = new DubbingAudioGenerationPipelineStage(
                properties,
                artifactService,
                new RecordingSubtitleService(List.of()),
                ttsProvider,
                new NoopCostBudgetGuardService()
        );

        assertThatThrownBy(() -> stage.execute(context()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Target subtitles not found for dubbing audio generation.");
    }

    @Test
    void budgetGuardStopsBeforeTtsProviderCall() {
        properties.getTts().setEnabled(true);
        DubbingAudioGenerationPipelineStage stage = new DubbingAudioGenerationPipelineStage(
                properties,
                artifactService,
                subtitleService,
                ttsProvider,
                new FailingCostBudgetGuardService()
        );

        assertThatThrownBy(() -> stage.execute(context()))
                .isInstanceOf(CostBudgetExceededException.class)
                .hasMessageContaining("Job cost budget exceeded before DUBBING_AUDIO_GENERATION");
        assertThat(ttsProvider.request).isNull();
        assertThat(artifactService.commands).isEmpty();
    }

    private DubbingAudioGenerationPipelineStage stage() {
        return new DubbingAudioGenerationPipelineStage(
                properties,
                artifactService,
                subtitleService,
                ttsProvider,
                new NoopCostBudgetGuardService(),
                new EmptyArtifactCacheService()
        );
    }

    private LocalizationJobExecutionContextBo context() {
        return contextWithTtsVoice(null);
    }

    private LocalizationJobExecutionContextBo contextWithTtsVoice(String ttsVoice) {
        Instant now = Instant.parse("2026-06-26T23:00:00Z");
        return new LocalizationJobExecutionContextBo(
                new LocalizationJobRecord("dubbing-job-1", "dubbing-video-1", "zh-CN", ttsVoice, LocalizationJobStatus.PROCESSING, now),
                new QueuedLocalizationJobMessage(
                        "dubbing-job-1",
                        "dubbing-video-1",
                        "source-videos/dubbing-video-1/sample.mp4",
                        "zh-CN",
                        ttsVoice,
                        now,
                        LocalizationJobStage.WORKER_SMOKE
                ),
                now
        );
    }

    private static class RecordingTtsProvider implements TtsProvider {

        private TtsRequestBo request;

        @Override
        public TtsResultBo synthesize(TtsRequestBo request) {
            this.request = request;
            return new TtsResultBo(new byte[] {9, 8, 7}, "dubbing-audio.mp3", "audio/mpeg");
        }
    }

    private static class RecordingTtsCacheKeyService implements TtsCacheKeyService {

        private String language;
        private String provider;
        private String model;
        private String voice;
        private String text;

        @Override
        public TtsCacheLookupBo build(String language, String provider, String model, String voice, String text) {
            this.language = language;
            this.provider = provider;
            this.model = model;
            this.voice = voice;
            this.text = text;
            return new TtsCacheLookupBo(
                    "tts-cache-key-hit",
                    "tts-text-hash",
                    language,
                    provider,
                    model,
                    voice
            );
        }
    }

    private static class RecordingTtsCacheService implements TtsCacheService {

        private final TtsCacheHitVo hit;
        private TtsCacheLookupBo lookup;
        private TtsCacheLookupBo storedLookup;
        private String storedJobId;
        private TtsResultBo storedResult;

        private RecordingTtsCacheService(TtsCacheHitVo hit) {
            this.hit = hit;
        }

        @Override
        public java.util.Optional<TtsCacheHitVo> findCachedTts(TtsCacheLookupBo lookup) {
            this.lookup = lookup;
            return java.util.Optional.ofNullable(hit);
        }

        @Override
        public void storeTts(TtsCacheLookupBo lookup, String jobId, TtsResultBo result) {
            this.storedLookup = lookup;
            this.storedJobId = jobId;
            this.storedResult = result;
        }
    }

    private static class RecordingSubtitleService implements SubtitleService {

        private final List<SubtitleSegmentVo> subtitles;

        private RecordingSubtitleService(List<SubtitleSegmentVo> subtitles) {
            this.subtitles = subtitles;
        }

        @Override
        public List<SubtitleSegmentVo> replaceSubtitles(String jobId, String language, TranslationResultBo result) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SubtitleSegmentVo> listSubtitles(String jobId, String language) {
            return subtitles;
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
                    "artifact-hash-" + commands.size(),
                    false,
                    null,
                    Instant.parse("2026-06-26T23:00:00Z")
            );
        }

        @Override
        public JobArtifactVo createReusedArtifact(
                String jobId,
                com.linguaframe.job.domain.entity.JobArtifactRecord source
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<JobArtifactVo> listArtifacts(String jobId) {
            return List.of();
        }

        @Override
        public StoredObjectResourceBo openArtifact(String jobId, String artifactId) {
            return new StoredObjectResourceBo(
                    "dubbing-audio.mp3",
                    "audio/mpeg",
                    0L,
                    new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8))
            );
        }
    }

    private static class EmptyArtifactCacheService implements ArtifactCacheService {

        @Override
        public java.util.Optional<JobArtifactVo> tryReuseArtifact(
                LocalizationJobExecutionContextBo context,
                JobArtifactType type
        ) {
            return java.util.Optional.empty();
        }
    }

    private static class RecordingArtifactCacheService implements ArtifactCacheService {

        private final JobArtifactVo artifact;
        private final List<JobArtifactType> requestedTypes = new ArrayList<>();

        private RecordingArtifactCacheService(JobArtifactVo artifact) {
            this.artifact = artifact;
        }

        @Override
        public java.util.Optional<JobArtifactVo> tryReuseArtifact(
                LocalizationJobExecutionContextBo context,
                JobArtifactType type
        ) {
            requestedTypes.add(type);
            return java.util.Optional.of(artifact);
        }
    }

    private static class NoopCostBudgetGuardService implements CostBudgetGuardService {

        @Override
        public void assertWithinBudget(String jobId, LocalizationJobStage stage) {
        }
    }

    private static class RecordingCostBudgetGuardService implements CostBudgetGuardService {

        private int calls;

        @Override
        public void assertWithinBudget(String jobId, LocalizationJobStage stage) {
            calls++;
        }
    }

    private static class FailingCostBudgetGuardService implements CostBudgetGuardService {

        @Override
        public void assertWithinBudget(String jobId, LocalizationJobStage stage) {
            throw new CostBudgetExceededException(
                    "Job cost budget exceeded before " + stage
                            + ": current estimated cost 0.01 USD, limit 0.01 USD."
            );
        }
    }
}
