package com.linguaframe.job.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.service.LocalizationPipelineStage;
import com.linguaframe.job.service.QualityEvaluationService;
import com.linguaframe.job.service.SubtitleService;
import com.linguaframe.job.service.TranscriptService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QualityEvaluationPipelineStage implements LocalizationPipelineStage {

    private final LinguaFrameProperties properties;
    private final TranscriptService transcriptService;
    private final SubtitleService subtitleService;
    private final QualityEvaluationService qualityEvaluationService;

    public QualityEvaluationPipelineStage(
            LinguaFrameProperties properties,
            TranscriptService transcriptService,
            SubtitleService subtitleService,
            QualityEvaluationService qualityEvaluationService
    ) {
        this.properties = properties;
        this.transcriptService = transcriptService;
        this.subtitleService = subtitleService;
        this.qualityEvaluationService = qualityEvaluationService;
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

        qualityEvaluationService.evaluateAndStore(jobId, targetLanguage, sourceSegments, targetSegments);
    }
}
