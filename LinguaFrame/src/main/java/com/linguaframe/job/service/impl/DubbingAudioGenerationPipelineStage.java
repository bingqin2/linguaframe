package com.linguaframe.job.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.bo.TtsRequestBo;
import com.linguaframe.job.domain.bo.TtsResultBo;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.service.CostBudgetGuardService;
import com.linguaframe.job.service.ArtifactCacheService;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.LocalizationPipelineStage;
import com.linguaframe.job.service.SubtitleService;
import com.linguaframe.job.service.TtsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class DubbingAudioGenerationPipelineStage implements LocalizationPipelineStage {

    private final LinguaFrameProperties properties;
    private final JobArtifactService artifactService;
    private final SubtitleService subtitleService;
    private final TtsProvider ttsProvider;
    private final CostBudgetGuardService costBudgetGuardService;
    private final ArtifactCacheService artifactCacheService;

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
                (context, type) -> java.util.Optional.empty()
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
        this.properties = properties;
        this.artifactService = artifactService;
        this.subtitleService = subtitleService;
        this.ttsProvider = ttsProvider;
        this.costBudgetGuardService = costBudgetGuardService;
        this.artifactCacheService = artifactCacheService;
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
        List<SubtitleSegmentVo> subtitles = subtitleService.listSubtitles(jobId, targetLanguage);
        if (subtitles.isEmpty()) {
            throw new IllegalStateException("Target subtitles not found for dubbing audio generation.");
        }
        costBudgetGuardService.assertWithinBudget(jobId, stage());

        String text = subtitles.stream()
                .sorted(Comparator.comparingInt(SubtitleSegmentVo::index))
                .map(SubtitleSegmentVo::text)
                .map(String::trim)
                .collect(Collectors.joining("\n"));
        TtsResultBo result = ttsProvider.synthesize(new TtsRequestBo(jobId, targetLanguage, text));
        artifactService.createArtifact(new CreateJobArtifactCommand(
                jobId,
                JobArtifactType.DUBBING_AUDIO,
                result.filename(),
                result.contentType(),
                result.audioContent()
        ));
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
