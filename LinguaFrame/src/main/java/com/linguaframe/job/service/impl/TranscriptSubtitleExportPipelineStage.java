package com.linguaframe.job.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.bo.TranscriptionCacheLookupBo;
import com.linguaframe.job.domain.bo.TranscriptionResultBo;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.ProviderCacheHitVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptionCacheHitVo;
import com.linguaframe.job.service.CostBudgetGuardService;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.LocalizationPipelineStage;
import com.linguaframe.job.service.SubtitleExportService;
import com.linguaframe.job.service.TranscriptService;
import com.linguaframe.job.service.TranscriptionCacheKeyService;
import com.linguaframe.job.service.TranscriptionCacheService;
import com.linguaframe.job.service.TranscriptionProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class TranscriptSubtitleExportPipelineStage implements LocalizationPipelineStage {

    private final LinguaFrameProperties properties;
    private final JobArtifactService artifactService;
    private final TranscriptionProvider transcriptionProvider;
    private final TranscriptService transcriptService;
    private final SubtitleExportService subtitleExportService;
    private final CostBudgetGuardService costBudgetGuardService;
    private final TranscriptionCacheKeyService transcriptionCacheKeyService;
    private final TranscriptionCacheService transcriptionCacheService;

    public TranscriptSubtitleExportPipelineStage(
            LinguaFrameProperties properties,
            JobArtifactService artifactService,
            TranscriptionProvider transcriptionProvider,
            TranscriptService transcriptService,
            SubtitleExportService subtitleExportService,
            CostBudgetGuardService costBudgetGuardService
    ) {
        this(
                properties,
                artifactService,
                transcriptionProvider,
                transcriptService,
                subtitleExportService,
                costBudgetGuardService,
                null,
                null
        );
    }

    @Autowired
    public TranscriptSubtitleExportPipelineStage(
            LinguaFrameProperties properties,
            JobArtifactService artifactService,
            TranscriptionProvider transcriptionProvider,
            TranscriptService transcriptService,
            SubtitleExportService subtitleExportService,
            CostBudgetGuardService costBudgetGuardService,
            TranscriptionCacheKeyService transcriptionCacheKeyService,
            TranscriptionCacheService transcriptionCacheService
    ) {
        this.properties = properties;
        this.artifactService = artifactService;
        this.transcriptionProvider = transcriptionProvider;
        this.transcriptService = transcriptService;
        this.subtitleExportService = subtitleExportService;
        this.costBudgetGuardService = costBudgetGuardService;
        this.transcriptionCacheKeyService = transcriptionCacheKeyService;
        this.transcriptionCacheService = transcriptionCacheService;
    }

    @Override
    public LocalizationJobStage stage() {
        return LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT;
    }

    @Override
    public void execute(LocalizationJobExecutionContextBo context) {
        if (!properties.getTranscription().isEnabled()) {
            return;
        }

        byte[] audioContent = readExtractedAudio(context.job().id());
        TranscriptionCacheLookupBo lookup = buildCacheLookup(audioContent);
        Optional<TranscriptionCacheHitVo> cacheHit = lookup == null || transcriptionCacheService == null
                ? Optional.empty()
                : transcriptionCacheService.findCachedTranscription(lookup);
        TranscriptionResultBo result;
        if (cacheHit.isPresent()) {
            result = cacheHit.get().result();
            context.recordProviderCacheHit(new ProviderCacheHitVo(
                    ModelCallOperation.TRANSCRIPTION,
                    cacheHit.get().cacheKey(),
                    cacheHit.get().sourceJobId()
            ));
        } else {
            costBudgetGuardService.assertWithinBudget(context.job().id(), stage());
            result = transcriptionProvider.transcribe(context.job().id(), audioContent);
        }
        List<TranscriptSegmentVo> segments = transcriptService.replaceTranscript(context.job().id(), result);
        if (cacheHit.isEmpty() && lookup != null && transcriptionCacheService != null) {
            transcriptionCacheService.storeTranscription(lookup, context.job().id(), result);
        }

        artifactService.createArtifact(new CreateJobArtifactCommand(
                context.job().id(),
                JobArtifactType.TRANSCRIPT_JSON,
                "transcript.json",
                "application/json",
                subtitleExportService.exportTranscriptJson(segments)
        ));
        artifactService.createArtifact(new CreateJobArtifactCommand(
                context.job().id(),
                JobArtifactType.SUBTITLE_SRT,
                "subtitles.srt",
                "application/x-subrip",
                subtitleExportService.exportSrt(segments)
        ));
        artifactService.createArtifact(new CreateJobArtifactCommand(
                context.job().id(),
                JobArtifactType.SUBTITLE_VTT,
                "subtitles.vtt",
                "text/vtt",
                subtitleExportService.exportVtt(segments)
        ));
    }

    private TranscriptionCacheLookupBo buildCacheLookup(byte[] audioContent) {
        if (transcriptionCacheKeyService == null) {
            return null;
        }
        return transcriptionCacheKeyService.build(
                cacheProvider(),
                cacheModel(),
                cachePromptVersion(),
                audioContent
        );
    }

    private String cacheProvider() {
        if ("openai".equalsIgnoreCase(properties.getTranscription().getProvider())) {
            return "OPENAI";
        }
        return "DEMO";
    }

    private String cacheModel() {
        if ("openai".equalsIgnoreCase(properties.getTranscription().getProvider())) {
            return properties.getTranscription().getOpenai().getModel();
        }
        return "demo-transcription";
    }

    private String cachePromptVersion() {
        if ("openai".equalsIgnoreCase(properties.getTranscription().getProvider())) {
            return "openai-audio-transcriptions-v1";
        }
        return "demo-transcription-v1";
    }

    private byte[] readExtractedAudio(String jobId) {
        JobArtifactVo audioArtifact = artifactService.listArtifacts(jobId).stream()
                .filter(artifact -> artifact.type() == JobArtifactType.EXTRACTED_AUDIO)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Extracted audio artifact not found."));
        StoredObjectResourceBo resource = artifactService.openArtifact(jobId, audioArtifact.artifactId());
        try (var inputStream = resource.inputStream()) {
            return inputStream.readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read extracted audio artifact.", ex);
        }
    }
}
