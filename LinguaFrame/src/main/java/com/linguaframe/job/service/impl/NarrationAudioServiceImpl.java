package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.TtsRequestBo;
import com.linguaframe.job.domain.bo.TtsResultBo;
import com.linguaframe.job.domain.entity.NarrationSegmentRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.NarrationGenerationVo;
import com.linguaframe.job.repository.NarrationSegmentRepository;
import com.linguaframe.job.service.CostBudgetGuardService;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.job.service.NarrationAudioService;
import com.linguaframe.job.service.TtsProvider;
import com.linguaframe.media.domain.bo.CreateTimedAudioBedCommand;
import com.linguaframe.media.domain.bo.TimedAudioSegmentBo;
import com.linguaframe.media.service.FfmpegTimedAudioBedService;
import com.linguaframe.media.service.MediaWorkDirectoryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class NarrationAudioServiceImpl implements NarrationAudioService {

    private static final BigDecimal ZERO = new BigDecimal("0.000");
    private static final String MIXED_OR_DEFAULT = "MIXED_OR_DEFAULT";
    private static final String TIMED_AUDIO_BED = "TIMED_AUDIO_BED";

    private final NarrationSegmentRepository narrationSegmentRepository;
    private final LocalizationJobQueryService queryService;
    private final TtsProvider ttsProvider;
    private final JobArtifactService artifactService;
    private final CostBudgetGuardService costBudgetGuardService;
    private final FfmpegTimedAudioBedService timedAudioBedService;
    private final MediaWorkDirectoryService workDirectoryService;

    public NarrationAudioServiceImpl(
            NarrationSegmentRepository narrationSegmentRepository,
            LocalizationJobQueryService queryService,
            TtsProvider ttsProvider,
            JobArtifactService artifactService,
            CostBudgetGuardService costBudgetGuardService,
            FfmpegTimedAudioBedService timedAudioBedService,
            MediaWorkDirectoryService workDirectoryService
    ) {
        this.narrationSegmentRepository = narrationSegmentRepository;
        this.queryService = queryService;
        this.ttsProvider = ttsProvider;
        this.artifactService = artifactService;
        this.costBudgetGuardService = costBudgetGuardService;
        this.timedAudioBedService = timedAudioBedService;
        this.workDirectoryService = workDirectoryService;
    }

    @Override
    public NarrationGenerationVo generateAudio(String jobId) {
        List<NarrationSegmentRecord> segments = narrationSegmentRepository.findByJobId(jobId).stream()
                .sorted(Comparator.comparingInt(NarrationSegmentRecord::segmentIndex))
                .toList();
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Narration workspace is empty.");
        }

        LocalizationJobVo job = queryService.getJob(jobId);
        String voiceSummary = voiceSummary(segments);

        costBudgetGuardService.assertWithinBudget(jobId, LocalizationJobStage.DUBBING_AUDIO_GENERATION);
        Path workDirectory = workDirectoryService.createJobWorkDirectory(jobId);
        try {
            List<TimedAudioSegmentBo> timedSegments = new ArrayList<>();
            for (int i = 0; i < segments.size(); i++) {
                NarrationSegmentRecord segment = segments.get(i);
                String voice = segment.voice() == null || segment.voice().isBlank() ? job.ttsVoice() : segment.voice().trim();
                TtsResultBo segmentAudio = ttsProvider.synthesize(new TtsRequestBo(
                        jobId,
                        job.targetLanguage(),
                        voice,
                        segment.text().trim()
                ));
                Path segmentAudioPath = workDirectory.resolve("narration-segment-%03d.mp3".formatted(i));
                writeAudio(segmentAudioPath, segmentAudio.audioContent());
                timedSegments.add(new TimedAudioSegmentBo(
                        segmentAudioPath,
                        segment.startSeconds(),
                        segment.endSeconds()
                ));
            }

            TtsResultBo audioBed = timedAudioBedService.createAudioBed(new CreateTimedAudioBedCommand(
                    jobId,
                    timedSegments,
                    workDirectory.resolve("narration-audio.mp3"),
                    "narration-audio.mp3"
            ));

            JobArtifactVo artifact = artifactService.createArtifact(new CreateJobArtifactCommand(
                    jobId,
                    JobArtifactType.NARRATION_AUDIO,
                    "narration-audio.mp3",
                    audioBed.contentType(),
                    audioBed.audioContent()
            ));

            return new NarrationGenerationVo(
                    jobId,
                    artifact.artifactId(),
                    artifact.filename(),
                    artifact.contentType(),
                    artifact.sizeBytes(),
                    segments.size(),
                    totalCharacters(segments),
                    totalTimelineDurationSeconds(segments),
                    voiceSummary,
                    TIMED_AUDIO_BED,
                    true,
                    segments.size(),
                    "READY"
            );
        } finally {
            workDirectoryService.deleteRecursively(workDirectory);
        }
    }

    private String voiceSummary(List<NarrationSegmentRecord> segments) {
        List<String> voices = segments.stream()
                .map(NarrationSegmentRecord::voice)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(voice -> !voice.isBlank())
                .distinct()
                .toList();
        boolean everySegmentHasVoice = segments.stream()
                .map(NarrationSegmentRecord::voice)
                .allMatch(voice -> voice != null && !voice.isBlank());
        return everySegmentHasVoice && voices.size() == 1 ? voices.getFirst() : MIXED_OR_DEFAULT;
    }

    private int totalCharacters(List<NarrationSegmentRecord> segments) {
        return segments.stream()
                .map(NarrationSegmentRecord::text)
                .map(String::trim)
                .mapToInt(String::length)
                .sum();
    }

    private BigDecimal totalTimelineDurationSeconds(List<NarrationSegmentRecord> segments) {
        return segments.stream()
                .map(segment -> segment.endSeconds().subtract(segment.startSeconds()).setScale(3, RoundingMode.HALF_UP))
                .reduce(ZERO, BigDecimal::add);
    }

    private void writeAudio(Path path, byte[] audioContent) {
        try {
            Files.write(path, audioContent);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Failed to prepare narration segment audio.", ex);
        }
    }
}
