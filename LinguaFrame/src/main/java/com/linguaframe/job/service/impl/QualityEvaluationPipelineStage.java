package com.linguaframe.job.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.bo.QualityEvaluationCacheLookupBo;
import com.linguaframe.job.domain.bo.QualityEvaluationResultBo;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.PromptTemplatePurpose;
import com.linguaframe.job.domain.vo.ProviderCacheHitVo;
import com.linguaframe.job.domain.vo.QualityEvaluationCacheHitVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.service.CostBudgetGuardService;
import com.linguaframe.job.service.LocalizationPipelineStage;
import com.linguaframe.job.service.PromptTemplateRegistry;
import com.linguaframe.job.service.QualityEvaluationCacheKeyService;
import com.linguaframe.job.service.QualityEvaluationCacheService;
import com.linguaframe.job.service.QualityEvaluationService;
import com.linguaframe.job.service.SubtitleService;
import com.linguaframe.job.service.TranscriptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class QualityEvaluationPipelineStage implements LocalizationPipelineStage {

    private final LinguaFrameProperties properties;
    private final TranscriptService transcriptService;
    private final SubtitleService subtitleService;
    private final QualityEvaluationService qualityEvaluationService;
    private final CostBudgetGuardService costBudgetGuardService;
    private final QualityEvaluationCacheKeyService qualityEvaluationCacheKeyService;
    private final QualityEvaluationCacheService qualityEvaluationCacheService;
    private final PromptTemplateRegistry promptTemplateRegistry;

    public QualityEvaluationPipelineStage(
            LinguaFrameProperties properties,
            TranscriptService transcriptService,
            SubtitleService subtitleService,
            QualityEvaluationService qualityEvaluationService,
            CostBudgetGuardService costBudgetGuardService
    ) {
        this(
                properties,
                transcriptService,
                subtitleService,
                qualityEvaluationService,
                costBudgetGuardService,
                null,
                null,
                null
        );
    }

    @Autowired
    public QualityEvaluationPipelineStage(
            LinguaFrameProperties properties,
            TranscriptService transcriptService,
            SubtitleService subtitleService,
            QualityEvaluationService qualityEvaluationService,
            CostBudgetGuardService costBudgetGuardService,
            QualityEvaluationCacheKeyService qualityEvaluationCacheKeyService,
            QualityEvaluationCacheService qualityEvaluationCacheService,
            PromptTemplateRegistry promptTemplateRegistry
    ) {
        this.properties = properties;
        this.transcriptService = transcriptService;
        this.subtitleService = subtitleService;
        this.qualityEvaluationService = qualityEvaluationService;
        this.costBudgetGuardService = costBudgetGuardService;
        this.qualityEvaluationCacheKeyService = qualityEvaluationCacheKeyService;
        this.qualityEvaluationCacheService = qualityEvaluationCacheService;
        this.promptTemplateRegistry = promptTemplateRegistry;
    }

    @Override
    public LocalizationJobStage stage() {
        return LocalizationJobStage.TRANSLATION_QUALITY_EVALUATION;
    }

    @Override
    public void execute(LocalizationJobExecutionContextBo context) {
        if (!properties.getEvaluation().isEnabled()) {
            return;
        }

        String jobId = context.job().id();
        String targetLanguage = context.job().targetLanguage();
        List<TranscriptSegmentVo> sourceSegments = transcriptService.listTranscript(jobId);
        List<SubtitleSegmentVo> targetSegments = subtitleService.listSubtitles(jobId, targetLanguage);
        if (sourceSegments.isEmpty() || targetSegments.isEmpty()) {
            return;
        }

        QualityEvaluationCacheLookupBo lookup = buildCacheLookup(targetLanguage, sourceSegments, targetSegments);
        Optional<QualityEvaluationCacheHitVo> cacheHit = lookup == null || qualityEvaluationCacheService == null
                ? Optional.empty()
                : qualityEvaluationCacheService.findCachedEvaluation(lookup);
        QualityEvaluationVo evaluation;
        if (cacheHit.isPresent()) {
            QualityEvaluationResultBo result = cacheHit.get().result();
            evaluation = qualityEvaluationService.storeCachedEvaluation(jobId, targetLanguage, result);
            context.recordProviderCacheHit(new ProviderCacheHitVo(
                    ModelCallOperation.EVALUATION,
                    cacheHit.get().cacheKey(),
                    cacheHit.get().sourceJobId()
            ));
        } else {
            costBudgetGuardService.assertWithinBudget(jobId, stage());
            evaluation = qualityEvaluationService.evaluateAndStore(jobId, targetLanguage, sourceSegments, targetSegments);
        }
        if (cacheHit.isEmpty() && lookup != null && qualityEvaluationCacheService != null
                && evaluation.status() == com.linguaframe.job.domain.enums.QualityEvaluationStatus.SUCCEEDED) {
            qualityEvaluationCacheService.storeEvaluation(lookup, jobId, new QualityEvaluationResultBo(
                    evaluation.score(),
                    evaluation.verdict(),
                    evaluation.completeness(),
                    evaluation.readability(),
                    evaluation.timingPreservation(),
                    evaluation.naturalness(),
                    evaluation.issues(),
                    evaluation.suggestedFixes()
            ));
        }
    }

    private QualityEvaluationCacheLookupBo buildCacheLookup(
            String targetLanguage,
            List<TranscriptSegmentVo> sourceSegments,
            List<SubtitleSegmentVo> targetSegments
    ) {
        if (qualityEvaluationCacheKeyService == null) {
            return null;
        }
        return qualityEvaluationCacheKeyService.build(
                targetLanguage,
                cacheProvider(),
                cacheModel(),
                cachePromptVersion(),
                sourceSegments,
                targetSegments
        );
    }

    private String cacheProvider() {
        if ("openai".equalsIgnoreCase(properties.getEvaluation().getProvider())) {
            return "OPENAI";
        }
        return "DEMO";
    }

    private String cacheModel() {
        if ("openai".equalsIgnoreCase(properties.getEvaluation().getProvider())) {
            return properties.getEvaluation().getOpenai().getModel();
        }
        return "demo-quality-evaluation";
    }

    private String cachePromptVersion() {
        if ("openai".equalsIgnoreCase(properties.getEvaluation().getProvider()) && promptTemplateRegistry != null) {
            return promptTemplateRegistry.activeTemplate(PromptTemplatePurpose.TRANSLATION_QUALITY_EVALUATION).version();
        }
        return "demo-quality-evaluation-v1";
    }
}
