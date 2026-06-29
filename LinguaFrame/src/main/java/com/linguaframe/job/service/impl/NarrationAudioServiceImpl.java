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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class NarrationAudioServiceImpl implements NarrationAudioService {

    private static final BigDecimal ZERO = new BigDecimal("0.000");
    private static final String MIXED_OR_DEFAULT = "MIXED_OR_DEFAULT";

    private final NarrationSegmentRepository narrationSegmentRepository;
    private final LocalizationJobQueryService queryService;
    private final TtsProvider ttsProvider;
    private final JobArtifactService artifactService;
    private final CostBudgetGuardService costBudgetGuardService;

    public NarrationAudioServiceImpl(
            NarrationSegmentRepository narrationSegmentRepository,
            LocalizationJobQueryService queryService,
            TtsProvider ttsProvider,
            JobArtifactService artifactService,
            CostBudgetGuardService costBudgetGuardService
    ) {
        this.narrationSegmentRepository = narrationSegmentRepository;
        this.queryService = queryService;
        this.ttsProvider = ttsProvider;
        this.artifactService = artifactService;
        this.costBudgetGuardService = costBudgetGuardService;
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
        String voice = MIXED_OR_DEFAULT.equals(voiceSummary) ? job.ttsVoice() : voiceSummary;

        costBudgetGuardService.assertWithinBudget(jobId, LocalizationJobStage.DUBBING_AUDIO_GENERATION);
        TtsResultBo ttsResult = ttsProvider.synthesize(new TtsRequestBo(
                jobId,
                job.targetLanguage(),
                voice,
                composeProviderText(segments)
        ));

        JobArtifactVo artifact = artifactService.createArtifact(new CreateJobArtifactCommand(
                jobId,
                JobArtifactType.NARRATION_AUDIO,
                "narration-audio.mp3",
                ttsResult.contentType(),
                ttsResult.audioContent()
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
                "READY"
        );
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

    private String composeProviderText(List<NarrationSegmentRecord> segments) {
        return segments.stream()
                .map(segment -> "[" + formatTimestamp(segment.startSeconds()) + "-" + formatTimestamp(segment.endSeconds())
                        + "]\n" + segment.text().trim())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    private String formatTimestamp(BigDecimal seconds) {
        BigDecimal normalized = seconds.setScale(3, RoundingMode.HALF_UP);
        int totalSeconds = normalized.intValue();
        int minutes = totalSeconds / 60;
        int wholeSeconds = totalSeconds % 60;
        int millis = normalized.remainder(BigDecimal.ONE).movePointRight(3).intValue();
        return String.format("%02d:%02d.%03d", minutes, wholeSeconds, millis);
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
}
