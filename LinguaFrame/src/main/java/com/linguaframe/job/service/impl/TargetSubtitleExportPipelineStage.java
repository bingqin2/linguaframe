package com.linguaframe.job.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.bo.TranslationCacheLookupBo;
import com.linguaframe.job.domain.bo.TranslationResultBo;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.PromptTemplatePurpose;
import com.linguaframe.job.domain.vo.ProviderCacheHitVo;
import com.linguaframe.job.domain.vo.PromptTemplateVo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.service.CostBudgetGuardService;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.LocalizationPipelineStage;
import com.linguaframe.job.service.PromptTemplateRegistry;
import com.linguaframe.job.service.SubtitleExportService;
import com.linguaframe.job.service.SubtitleService;
import com.linguaframe.job.service.TranscriptService;
import com.linguaframe.job.service.TranslationCacheKeyService;
import com.linguaframe.job.service.TranslationCacheService;
import com.linguaframe.job.service.TranslationProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class TargetSubtitleExportPipelineStage implements LocalizationPipelineStage {

    private final LinguaFrameProperties properties;
    private final JobArtifactService artifactService;
    private final TranscriptService transcriptService;
    private final TranslationProvider translationProvider;
    private final SubtitleService subtitleService;
    private final SubtitleExportService subtitleExportService;
    private final CostBudgetGuardService costBudgetGuardService;
    private final TranslationCacheKeyService translationCacheKeyService;
    private final TranslationCacheService translationCacheService;
    private final PromptTemplateRegistry promptTemplateRegistry;

    public TargetSubtitleExportPipelineStage(
            LinguaFrameProperties properties,
            JobArtifactService artifactService,
            TranscriptService transcriptService,
            TranslationProvider translationProvider,
            SubtitleService subtitleService,
            SubtitleExportService subtitleExportService,
            CostBudgetGuardService costBudgetGuardService
    ) {
        this(
                properties,
                artifactService,
                transcriptService,
                translationProvider,
                subtitleService,
                subtitleExportService,
                costBudgetGuardService,
                null,
                null,
                null
        );
    }

    @Autowired
    public TargetSubtitleExportPipelineStage(
            LinguaFrameProperties properties,
            JobArtifactService artifactService,
            TranscriptService transcriptService,
            TranslationProvider translationProvider,
            SubtitleService subtitleService,
            SubtitleExportService subtitleExportService,
            CostBudgetGuardService costBudgetGuardService,
            TranslationCacheKeyService translationCacheKeyService,
            TranslationCacheService translationCacheService
    ) {
        this(
                properties,
                artifactService,
                transcriptService,
                translationProvider,
                subtitleService,
                subtitleExportService,
                costBudgetGuardService,
                translationCacheKeyService,
                translationCacheService,
                null
        );
    }

    public TargetSubtitleExportPipelineStage(
            LinguaFrameProperties properties,
            JobArtifactService artifactService,
            TranscriptService transcriptService,
            TranslationProvider translationProvider,
            SubtitleService subtitleService,
            SubtitleExportService subtitleExportService,
            CostBudgetGuardService costBudgetGuardService,
            TranslationCacheKeyService translationCacheKeyService,
            TranslationCacheService translationCacheService,
            PromptTemplateRegistry promptTemplateRegistry
    ) {
        this.properties = properties;
        this.artifactService = artifactService;
        this.transcriptService = transcriptService;
        this.translationProvider = translationProvider;
        this.subtitleService = subtitleService;
        this.subtitleExportService = subtitleExportService;
        this.costBudgetGuardService = costBudgetGuardService;
        this.translationCacheKeyService = translationCacheKeyService;
        this.translationCacheService = translationCacheService;
        this.promptTemplateRegistry = promptTemplateRegistry;
    }

    @Override
    public LocalizationJobStage stage() {
        return LocalizationJobStage.TARGET_SUBTITLE_EXPORT;
    }

    @Override
    public void execute(LocalizationJobExecutionContextBo context) {
        if (!properties.getTranslation().isEnabled()) {
            return;
        }

        String jobId = context.job().id();
        String targetLanguage = context.job().targetLanguage();
        List<TranscriptSegmentVo> transcriptSegments = transcriptService.listTranscript(jobId);
        if (transcriptSegments.isEmpty()) {
            throw new IllegalStateException("Transcript segments not found.");
        }

        TranslationCacheLookupBo lookup = buildCacheLookup(targetLanguage, transcriptSegments);
        Optional<com.linguaframe.job.domain.vo.TranslationCacheHitVo> cacheHit = lookup == null || translationCacheService == null
                ? Optional.empty()
                : translationCacheService.findCachedTranslation(lookup);
        TranslationResultBo result;
        if (cacheHit.isPresent()) {
            result = cacheHit.get().result();
            context.recordProviderCacheHit(new ProviderCacheHitVo(
                    ModelCallOperation.TRANSLATION,
                    cacheHit.get().cacheKey(),
                    cacheHit.get().sourceJobId()
            ));
        } else {
            costBudgetGuardService.assertWithinBudget(jobId, stage());
            result = translationProvider.translate(jobId, targetLanguage, transcriptSegments);
        }
        List<SubtitleSegmentVo> subtitles = subtitleService.replaceSubtitles(jobId, targetLanguage, result);
        if (cacheHit.isEmpty() && lookup != null && translationCacheService != null) {
            translationCacheService.storeTranslation(lookup, jobId, result);
        }

        artifactService.createArtifact(new CreateJobArtifactCommand(
                jobId,
                JobArtifactType.TARGET_SUBTITLE_JSON,
                "target-subtitles.json",
                "application/json",
                subtitleExportService.exportSubtitleJson(subtitles)
        ));
        artifactService.createArtifact(new CreateJobArtifactCommand(
                jobId,
                JobArtifactType.TARGET_SUBTITLE_SRT,
                "target-subtitles.srt",
                "application/x-subrip",
                subtitleExportService.exportSubtitleSrt(subtitles)
        ));
        artifactService.createArtifact(new CreateJobArtifactCommand(
                jobId,
                JobArtifactType.TARGET_SUBTITLE_VTT,
                "target-subtitles.vtt",
                "text/vtt",
                subtitleExportService.exportSubtitleVtt(subtitles)
        ));
    }

    private TranslationCacheLookupBo buildCacheLookup(
            String targetLanguage,
            List<TranscriptSegmentVo> transcriptSegments
    ) {
        if (translationCacheKeyService == null) {
            return null;
        }
        return translationCacheKeyService.build(
                targetLanguage,
                cacheProvider(),
                cacheModel(),
                cachePromptVersion(),
                transcriptSegments
        );
    }

    private String cacheProvider() {
        if ("openai".equalsIgnoreCase(properties.getTranslation().getProvider())) {
            return "OPENAI";
        }
        return "DEMO";
    }

    private String cacheModel() {
        if ("openai".equalsIgnoreCase(properties.getTranslation().getProvider())) {
            return properties.getTranslation().getOpenai().getModel();
        }
        return "demo-translation";
    }

    private String cachePromptVersion() {
        if ("openai".equalsIgnoreCase(properties.getTranslation().getProvider()) && promptTemplateRegistry != null) {
            PromptTemplateVo template = promptTemplateRegistry.activeTemplate(PromptTemplatePurpose.SUBTITLE_TRANSLATION);
            return template.version();
        }
        return "demo-translation-v1";
    }
}
