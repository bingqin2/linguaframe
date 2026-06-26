package com.linguaframe.job.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.bo.TranslationResultBo;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.service.CostBudgetGuardService;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.LocalizationPipelineStage;
import com.linguaframe.job.service.SubtitleExportService;
import com.linguaframe.job.service.SubtitleService;
import com.linguaframe.job.service.TranscriptService;
import com.linguaframe.job.service.TranslationProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TargetSubtitleExportPipelineStage implements LocalizationPipelineStage {

    private final LinguaFrameProperties properties;
    private final JobArtifactService artifactService;
    private final TranscriptService transcriptService;
    private final TranslationProvider translationProvider;
    private final SubtitleService subtitleService;
    private final SubtitleExportService subtitleExportService;
    private final CostBudgetGuardService costBudgetGuardService;

    public TargetSubtitleExportPipelineStage(
            LinguaFrameProperties properties,
            JobArtifactService artifactService,
            TranscriptService transcriptService,
            TranslationProvider translationProvider,
            SubtitleService subtitleService,
            SubtitleExportService subtitleExportService,
            CostBudgetGuardService costBudgetGuardService
    ) {
        this.properties = properties;
        this.artifactService = artifactService;
        this.transcriptService = transcriptService;
        this.translationProvider = translationProvider;
        this.subtitleService = subtitleService;
        this.subtitleExportService = subtitleExportService;
        this.costBudgetGuardService = costBudgetGuardService;
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
        costBudgetGuardService.assertWithinBudget(jobId, stage());

        TranslationResultBo result = translationProvider.translate(jobId, targetLanguage, transcriptSegments);
        List<SubtitleSegmentVo> subtitles = subtitleService.replaceSubtitles(jobId, targetLanguage, result);

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
}
