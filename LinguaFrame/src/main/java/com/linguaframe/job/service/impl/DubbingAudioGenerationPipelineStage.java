package com.linguaframe.job.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.bo.TtsRequestBo;
import com.linguaframe.job.domain.bo.TtsResultBo;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.vo.ProviderCacheHitVo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.service.CostBudgetGuardService;
import com.linguaframe.job.service.ArtifactCacheService;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.LocalizationPipelineStage;
import com.linguaframe.job.service.SubtitleService;
import com.linguaframe.job.service.TtsCacheKeyService;
import com.linguaframe.job.service.TtsCacheService;
import com.linguaframe.job.service.TtsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class DubbingAudioGenerationPipelineStage implements LocalizationPipelineStage {

    private final LinguaFrameProperties properties;
    private final JobArtifactService artifactService;
    private final SubtitleService subtitleService;
    private final TtsProvider ttsProvider;
    private final CostBudgetGuardService costBudgetGuardService;
    private final ArtifactCacheService artifactCacheService;
    private final TtsCacheKeyService ttsCacheKeyService;
    private final TtsCacheService ttsCacheService;

    public DubbingAudioGenerationPipelineStage(
            LinguaFrameProperties properties,
            JobArtifactService artifactService,
            SubtitleService subtitleService,
            TtsProvider ttsProvider,
            CostBudgetGuardService costBudgetGuardService
    ) {
        this(
                properties,
                artifactService,
                subtitleService,
                ttsProvider,
                costBudgetGuardService,
                (context, type) -> java.util.Optional.empty(),
                null,
                null
        );
    }

    @Autowired
    public DubbingAudioGenerationPipelineStage(
            LinguaFrameProperties properties,
            JobArtifactService artifactService,
            SubtitleService subtitleService,
            TtsProvider ttsProvider,
            CostBudgetGuardService costBudgetGuardService,
            ArtifactCacheService artifactCacheService
    ) {
        this(
                properties,
                artifactService,
                subtitleService,
                ttsProvider,
                costBudgetGuardService,
                artifactCacheService,
                null,
                null
        );
    }

    public DubbingAudioGenerationPipelineStage(
            LinguaFrameProperties properties,
            JobArtifactService artifactService,
            SubtitleService subtitleService,
            TtsProvider ttsProvider,
            CostBudgetGuardService costBudgetGuardService,
            ArtifactCacheService artifactCacheService,
            TtsCacheKeyService ttsCacheKeyService,
            TtsCacheService ttsCacheService
    ) {
        this.properties = properties;
        this.artifactService = artifactService;
        this.subtitleService = subtitleService;
        this.ttsProvider = ttsProvider;
        this.costBudgetGuardService = costBudgetGuardService;
        this.artifactCacheService = artifactCacheService;
        this.ttsCacheKeyService = ttsCacheKeyService;
        this.ttsCacheService = ttsCacheService;
    }

    @Override
    public LocalizationJobStage stage() {
        return LocalizationJobStage.DUBBING_AUDIO_GENERATION;
    }

    @Override
    public void execute(LocalizationJobExecutionContextBo context) {
        if (!properties.getTts().isEnabled()) {
            return;
        }
        if (reuseCachedArtifact(context, JobArtifactType.DUBBING_AUDIO)) {
            return;
        }

        String jobId = context.job().id();
        String targetLanguage = context.job().targetLanguage();
        String effectiveVoice = effectiveVoice(context.job().ttsVoice());
        List<SubtitleSegmentVo> subtitles = subtitleService.listSubtitles(jobId, targetLanguage);
        if (subtitles.isEmpty()) {
            throw new IllegalStateException("Target subtitles not found for dubbing audio generation.");
        }
        String text = subtitles.stream()
                .sorted(Comparator.comparingInt(SubtitleSegmentVo::index))
                .map(SubtitleSegmentVo::text)
                .map(String::trim)
                .collect(Collectors.joining("\n"));
        Optional<com.linguaframe.job.domain.vo.TtsCacheHitVo> providerCacheHit = findCachedTts(targetLanguage, effectiveVoice, text);
        if (providerCacheHit.isPresent()) {
            com.linguaframe.job.domain.vo.TtsCacheHitVo hit = providerCacheHit.get();
            createDubbingArtifact(jobId, hit.result());
            context.recordProviderCacheHit(new ProviderCacheHitVo(
                    ModelCallOperation.TTS,
                    hit.cacheKey(),
                    hit.sourceJobId()
            ));
            return;
        }

        costBudgetGuardService.assertWithinBudget(jobId, stage());
        TtsResultBo result = ttsProvider.synthesize(new TtsRequestBo(jobId, targetLanguage, effectiveVoice, text));
        storeCachedTts(jobId, targetLanguage, effectiveVoice, text, result);
        createDubbingArtifact(jobId, result);
    }

    private Optional<com.linguaframe.job.domain.vo.TtsCacheHitVo> findCachedTts(String targetLanguage, String voice, String text) {
        if (ttsCacheKeyService == null || ttsCacheService == null) {
            return Optional.empty();
        }
        return ttsCacheService.findCachedTts(ttsCacheKeyService.build(
                targetLanguage,
                cacheProvider(),
                cacheModel(),
                voice,
                text
        ));
    }

    private void storeCachedTts(String jobId, String targetLanguage, String voice, String text, TtsResultBo result) {
        if (ttsCacheKeyService == null || ttsCacheService == null) {
            return;
        }
        ttsCacheService.storeTts(
                ttsCacheKeyService.build(targetLanguage, cacheProvider(), cacheModel(), voice, text),
                jobId,
                result
        );
    }

    private void createDubbingArtifact(String jobId, TtsResultBo result) {
        artifactService.createArtifact(new CreateJobArtifactCommand(
                jobId,
                JobArtifactType.DUBBING_AUDIO,
                result.filename(),
                result.contentType(),
                result.audioContent()
        ));
    }

    private String cacheProvider() {
        if ("openai".equalsIgnoreCase(properties.getTts().getProvider())) {
            return "OPENAI";
        }
        return "DEMO";
    }

    private String cacheModel() {
        if ("openai".equalsIgnoreCase(properties.getTts().getProvider())) {
            return properties.getTts().getOpenai().getModel();
        }
        return "demo-tts";
    }

    private String cacheVoice() {
        if ("openai".equalsIgnoreCase(properties.getTts().getProvider())) {
            return properties.getTts().getOpenai().getVoice();
        }
        return "demo-voice";
    }

    private String effectiveVoice(String jobTtsVoice) {
        if (jobTtsVoice != null && !jobTtsVoice.isBlank()) {
            return jobTtsVoice.trim();
        }
        return cacheVoice();
    }

    private boolean reuseCachedArtifact(LocalizationJobExecutionContextBo context, JobArtifactType type) {
        return artifactCacheService.tryReuseArtifact(context, type)
                .map(artifact -> {
                    context.recordCacheHit(artifact);
                    return true;
                })
                .orElse(false);
    }
}
