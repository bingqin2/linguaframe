package com.linguaframe.job.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.bo.SubtitlePolishingCacheLookupBo;
import com.linguaframe.job.domain.bo.SubtitlePolishingResultBo;
import com.linguaframe.job.domain.bo.TranslationResultBo;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.SubtitlePolishingMode;
import com.linguaframe.job.domain.vo.ProviderCacheHitVo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.service.CostBudgetGuardService;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.LocalizationPipelineStage;
import com.linguaframe.job.service.SubtitleExportService;
import com.linguaframe.job.service.SubtitlePolishingCacheKeyService;
import com.linguaframe.job.service.SubtitlePolishingCacheService;
import com.linguaframe.job.service.SubtitlePolishingProvider;
import com.linguaframe.job.service.SubtitleService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class SubtitlePolishingPipelineStage implements LocalizationPipelineStage {

    private final LinguaFrameProperties properties;
    private final SubtitleService subtitleService;
    private final SubtitleExportService subtitleExportService;
    private final JobArtifactService artifactService;
    private final SubtitlePolishingProvider polishingProvider;
    private final SubtitlePolishingCacheKeyService cacheKeyService;
    private final SubtitlePolishingCacheService cacheService;
    private final CostBudgetGuardService costBudgetGuardService;

    public SubtitlePolishingPipelineStage(
            LinguaFrameProperties properties,
            SubtitleService subtitleService,
            SubtitleExportService subtitleExportService,
            JobArtifactService artifactService,
            SubtitlePolishingProvider polishingProvider,
            SubtitlePolishingCacheKeyService cacheKeyService,
            SubtitlePolishingCacheService cacheService,
            CostBudgetGuardService costBudgetGuardService
    ) {
        this.properties = properties;
        this.subtitleService = subtitleService;
        this.subtitleExportService = subtitleExportService;
        this.artifactService = artifactService;
        this.polishingProvider = polishingProvider;
        this.cacheKeyService = cacheKeyService;
        this.cacheService = cacheService;
        this.costBudgetGuardService = costBudgetGuardService;
    }

    @Override
    public LocalizationJobStage stage() {
        return LocalizationJobStage.SUBTITLE_POLISHING;
    }

    @Override
    public void execute(LocalizationJobExecutionContextBo context) {
        String mode = SubtitlePolishingMode.parse(context.job().subtitlePolishingMode()).name();
        if (mode.equals(SubtitlePolishingMode.OFF.name())) {
            return;
        }

        String jobId = context.job().id();
        String targetLanguage = context.job().targetLanguage();
        List<SubtitleSegmentVo> subtitles = subtitleService.listSubtitles(jobId, targetLanguage);
        if (subtitles.isEmpty()) {
            throw new IllegalStateException("Target subtitles not found for subtitle polishing.");
        }

        SubtitlePolishingCacheLookupBo lookup = cacheKeyService.build(
                targetLanguage,
                cacheProvider(),
                cacheModel(),
                cachePromptVersion(),
                mode,
                subtitles
        );
        Optional<com.linguaframe.job.domain.vo.SubtitlePolishingCacheHitVo> cacheHit = cacheService.findCachedPolishing(lookup);
        SubtitlePolishingResultBo result;
        if (cacheHit.isPresent()) {
            result = cacheHit.get().result();
            context.recordProviderCacheHit(new ProviderCacheHitVo(
                    ModelCallOperation.SUBTITLE_POLISHING,
                    cacheHit.get().cacheKey(),
                    cacheHit.get().sourceJobId()
            ));
        } else {
            costBudgetGuardService.assertWithinBudget(jobId, stage());
            result = polishingProvider.polish(jobId, targetLanguage, mode, subtitles);
            cacheService.storePolishing(lookup, jobId, result);
        }

        List<SubtitleSegmentVo> polishedSubtitles = subtitleService.replaceSubtitles(
                jobId,
                targetLanguage,
                new TranslationResultBo(result.segments())
        );
        rewriteTargetArtifacts(jobId, polishedSubtitles);
    }

    private void rewriteTargetArtifacts(String jobId, List<SubtitleSegmentVo> subtitles) {
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
        return "demo-subtitle-polishing";
    }

    private String cachePromptVersion() {
        if ("openai".equalsIgnoreCase(properties.getTranslation().getProvider())) {
            return "openai-subtitle-polishing-v1";
        }
        return "demo-subtitle-polishing-v1";
    }
}
