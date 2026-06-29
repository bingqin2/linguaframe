package com.linguaframe.job.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.dto.SaveNarrationSegmentsRequest;
import com.linguaframe.job.domain.dto.UpdateNarrationMixSettingsDto;
import com.linguaframe.job.domain.entity.NarrationMixSettingsRecord;
import com.linguaframe.job.domain.entity.NarrationSegmentRecord;
import com.linguaframe.job.domain.vo.NarrationMixSettingsVo;
import com.linguaframe.job.domain.vo.NarrationSegmentVo;
import com.linguaframe.job.domain.vo.NarrationTimelineSegmentVo;
import com.linguaframe.job.domain.vo.NarrationTimelineSummaryVo;
import com.linguaframe.job.domain.vo.NarrationWorkspaceVo;
import com.linguaframe.job.repository.NarrationMixSettingsRepository;
import com.linguaframe.job.repository.NarrationSegmentRepository;
import com.linguaframe.job.service.NarrationVoiceCatalogService;
import com.linguaframe.job.service.NarrationWorkspaceService;
import com.linguaframe.job.service.impl.NarrationVoiceCatalogServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class NarrationWorkspaceServiceImpl implements NarrationWorkspaceService {

    private static final int MAX_SEGMENTS = 20;
    private static final int MAX_TEXT_LENGTH = 1000;
    private static final int MAX_VOICE_LENGTH = 64;
    private static final BigDecimal ZERO = new BigDecimal("0.000");
    private static final BigDecimal ZERO_PERCENT = new BigDecimal("0.00");
    private static final BigDecimal ONE_HUNDRED_PERCENT = new BigDecimal("100.00");
    private static final BigDecimal DEFAULT_DUCKING_VOLUME = new BigDecimal("0.350");
    private static final BigDecimal DEFAULT_NARRATION_VOLUME = new BigDecimal("1.000");
    private static final int DEFAULT_FADE_DURATION_MS = 250;
    private static final BigDecimal MAX_DUCKING_VOLUME = new BigDecimal("1.000");
    private static final BigDecimal MAX_NARRATION_VOLUME = new BigDecimal("2.000");
    private static final int MAX_FADE_DURATION_MS = 5000;

    private final NarrationSegmentRepository repository;
    private final NarrationMixSettingsRepository mixSettingsRepository;
    private final NarrationVoiceCatalogService voiceCatalogService;
    private final Clock clock;

    public NarrationWorkspaceServiceImpl(
            NarrationSegmentRepository repository,
            NarrationMixSettingsRepository mixSettingsRepository
    ) {
        this(repository, mixSettingsRepository, new NarrationVoiceCatalogServiceImpl(new LinguaFrameProperties()), Clock.systemUTC());
    }

    public NarrationWorkspaceServiceImpl(
            NarrationSegmentRepository repository,
            NarrationMixSettingsRepository mixSettingsRepository,
            Clock clock
    ) {
        this(repository, mixSettingsRepository, new NarrationVoiceCatalogServiceImpl(new LinguaFrameProperties()), clock);
    }

    @Autowired
    public NarrationWorkspaceServiceImpl(
            NarrationSegmentRepository repository,
            NarrationMixSettingsRepository mixSettingsRepository,
            NarrationVoiceCatalogService voiceCatalogService,
            Clock clock
    ) {
        this.repository = repository;
        this.mixSettingsRepository = mixSettingsRepository;
        this.voiceCatalogService = voiceCatalogService;
        this.clock = clock;
    }

    @Override
    public NarrationWorkspaceVo getWorkspace(String jobId) {
        return toWorkspace(jobId, repository.findByJobId(jobId));
    }

    @Override
    public NarrationWorkspaceVo saveWorkspace(String jobId, SaveNarrationSegmentsRequest request) {
        List<SaveNarrationSegmentsRequest.Segment> segments = request == null || request.segments() == null
                ? List.of()
                : request.segments();
        validate(segments);

        Instant now = Instant.now(clock);
        List<NarrationSegmentRecord> records = segments.stream()
                .sorted(Comparator.comparingInt(SaveNarrationSegmentsRequest.Segment::index))
                .map(segment -> new NarrationSegmentRecord(
                        "narration-" + UUID.randomUUID(),
                        jobId,
                        segment.index(),
                        normalizeSeconds(segment.startSeconds(), "startSeconds"),
                        normalizeSeconds(segment.endSeconds(), "endSeconds"),
                        segment.text().trim(),
                        normalizeVoice(segment.voice()),
                        now,
                        now
                ))
                .toList();
        repository.replaceSegments(jobId, records);
        return toWorkspace(jobId, records);
    }

    @Override
    public NarrationWorkspaceVo updateMixSettings(String jobId, UpdateNarrationMixSettingsDto request) {
        BigDecimal duckingVolume = normalizeMixDecimal(
                request == null ? null : request.duckingVolume(),
                "duckingVolume",
                MAX_DUCKING_VOLUME
        );
        BigDecimal narrationVolume = normalizeMixDecimal(
                request == null ? null : request.narrationVolume(),
                "narrationVolume",
                MAX_NARRATION_VOLUME
        );
        int fadeDurationMs = normalizeFadeDuration(request == null ? null : request.fadeDurationMs());
        mixSettingsRepository.upsert(new NarrationMixSettingsRecord(
                jobId,
                duckingVolume,
                narrationVolume,
                fadeDurationMs,
                Instant.now(clock)
        ));
        return toWorkspace(jobId, repository.findByJobId(jobId));
    }

    @Override
    public NarrationWorkspaceVo clearWorkspace(String jobId) {
        repository.deleteByJobId(jobId);
        return toWorkspace(jobId, List.of());
    }

    private void validate(List<SaveNarrationSegmentsRequest.Segment> segments) {
        if (segments.size() > MAX_SEGMENTS) {
            throw new IllegalArgumentException("Narration workspace supports at most 20 segments.");
        }
        List<SaveNarrationSegmentsRequest.Segment> byIndex = segments.stream()
                .sorted(Comparator.comparingInt(SaveNarrationSegmentsRequest.Segment::index))
                .toList();
        for (int i = 0; i < byIndex.size(); i++) {
            SaveNarrationSegmentsRequest.Segment segment = byIndex.get(i);
            if (segment.index() != i) {
                throw new IllegalArgumentException("Narration segment indexes must start at 0 and be contiguous.");
            }
            validateSegment(segment);
        }

        List<SaveNarrationSegmentsRequest.Segment> byTime = segments.stream()
                .sorted(Comparator.comparing(segment -> normalizeSeconds(segment.startSeconds(), "startSeconds")))
                .toList();
        for (int i = 1; i < byTime.size(); i++) {
            BigDecimal previousEnd = normalizeSeconds(byTime.get(i - 1).endSeconds(), "endSeconds");
            BigDecimal currentStart = normalizeSeconds(byTime.get(i).startSeconds(), "startSeconds");
            if (currentStart.compareTo(previousEnd) < 0) {
                throw new IllegalArgumentException("Narration segments must not overlap.");
            }
        }
    }

    private void validateSegment(SaveNarrationSegmentsRequest.Segment segment) {
        BigDecimal start = normalizeSeconds(segment.startSeconds(), "startSeconds");
        BigDecimal end = normalizeSeconds(segment.endSeconds(), "endSeconds");
        if (start.compareTo(ZERO) < 0) {
            throw new IllegalArgumentException("Narration startSeconds must be greater than or equal to 0.");
        }
        if (end.compareTo(start) <= 0) {
            throw new IllegalArgumentException("Narration endSeconds must be greater than startSeconds.");
        }
        if (segment.text() == null || segment.text().trim().isEmpty()) {
            throw new IllegalArgumentException("Narration text must not be blank.");
        }
        if (segment.text().trim().length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Narration text must be at most 1000 characters.");
        }
        String voice = normalizeVoice(segment.voice());
        if (voice != null && voice.length() > MAX_VOICE_LENGTH) {
            throw new IllegalArgumentException("Narration voice must be at most 64 characters.");
        }
        if (!voiceCatalogService.containsVoice(voice)) {
            throw new IllegalArgumentException("Narration voice must be one of the configured presets.");
        }
    }

    private NarrationWorkspaceVo toWorkspace(String jobId, List<NarrationSegmentRecord> records) {
        List<NarrationSegmentVo> segments = records.stream()
                .sorted(Comparator.comparingInt(NarrationSegmentRecord::segmentIndex))
                .map(this::toSegment)
                .toList();
        BigDecimal totalDuration = segments.stream()
                .map(NarrationSegmentVo::durationSeconds)
                .reduce(ZERO, BigDecimal::add);
        int totalCharacters = segments.stream()
                .mapToInt(NarrationSegmentVo::characterCount)
                .sum();
        boolean generationReady = !segments.isEmpty();
        return new NarrationWorkspaceVo(
                jobId,
                generationReady ? "DRAFT_READY" : "EMPTY",
                segments.size(),
                totalDuration,
                totalCharacters,
                generationReady,
                toMixSettings(jobId),
                voiceCatalogService.catalog(),
                toTimeline(segments, generationReady),
                segments,
                List.of(
                        "Narration text is available only in the editing workspace.",
                        "Narration evidence exports use counts and timing metadata instead of script bodies.",
                        "Narration outputs do not replace generated subtitles, reviewed subtitles, or dubbed video artifacts."
                )
        );
    }

    private NarrationTimelineSummaryVo toTimeline(List<NarrationSegmentVo> segments, boolean generationReady) {
        if (segments.isEmpty()) {
            return new NarrationTimelineSummaryVo(
                    ZERO,
                    ZERO,
                    ZERO,
                    ZERO,
                    ZERO,
                    0,
                    false,
                    false,
                    List.of()
            );
        }

        BigDecimal start = segments.stream()
                .map(NarrationSegmentVo::startSeconds)
                .min(BigDecimal::compareTo)
                .orElse(ZERO);
        BigDecimal end = segments.stream()
                .map(NarrationSegmentVo::endSeconds)
                .max(BigDecimal::compareTo)
                .orElse(ZERO);
        BigDecimal totalSpan = end.subtract(start).setScale(3, RoundingMode.HALF_UP);
        BigDecimal covered = segments.stream()
                .map(NarrationSegmentVo::durationSeconds)
                .reduce(ZERO, BigDecimal::add)
                .setScale(3, RoundingMode.HALF_UP);

        GapSummary gapSummary = gapSummary(segments);
        List<NarrationTimelineSegmentVo> timelineSegments = segments.stream()
                .map(segment -> toTimelineSegment(segment, start, totalSpan))
                .toList();

        return new NarrationTimelineSummaryVo(
                start,
                end,
                totalSpan,
                covered,
                gapSummary.gapSeconds().setScale(3, RoundingMode.HALF_UP),
                gapSummary.gapCount(),
                gapSummary.hasOverlap(),
                generationReady && !gapSummary.hasOverlap(),
                timelineSegments
        );
    }

    private NarrationTimelineSegmentVo toTimelineSegment(NarrationSegmentVo segment, BigDecimal timelineStart, BigDecimal totalSpan) {
        BigDecimal leftPercent = ZERO_PERCENT;
        BigDecimal widthPercent = ONE_HUNDRED_PERCENT;
        if (totalSpan.compareTo(ZERO) > 0) {
            leftPercent = segment.startSeconds()
                    .subtract(timelineStart)
                    .multiply(new BigDecimal("100"))
                    .divide(totalSpan, 2, RoundingMode.HALF_UP);
            widthPercent = segment.durationSeconds()
                    .multiply(new BigDecimal("100"))
                    .divide(totalSpan, 2, RoundingMode.HALF_UP);
        }
        return new NarrationTimelineSegmentVo(
                segment.index(),
                segment.startSeconds(),
                segment.endSeconds(),
                segment.durationSeconds(),
                leftPercent,
                widthPercent,
                segmentStatus(segment),
                segment.characterCount(),
                segment.voice() == null ? "" : segment.voice()
        );
    }

    private String segmentStatus(NarrationSegmentVo segment) {
        if (segment.endSeconds().compareTo(segment.startSeconds()) <= 0) {
            return "INVALID_RANGE";
        }
        if (segment.text() == null || segment.text().isBlank()) {
            return "EMPTY_TEXT";
        }
        return "READY";
    }

    private GapSummary gapSummary(List<NarrationSegmentVo> segments) {
        BigDecimal gapSeconds = ZERO;
        int gapCount = 0;
        boolean hasOverlap = false;
        List<NarrationSegmentVo> byTime = segments.stream()
                .sorted(Comparator.comparing(NarrationSegmentVo::startSeconds))
                .toList();
        for (int index = 1; index < byTime.size(); index += 1) {
            BigDecimal previousEnd = byTime.get(index - 1).endSeconds();
            BigDecimal currentStart = byTime.get(index).startSeconds();
            int comparison = currentStart.compareTo(previousEnd);
            if (comparison > 0) {
                gapCount += 1;
                gapSeconds = gapSeconds.add(currentStart.subtract(previousEnd));
            } else if (comparison < 0) {
                hasOverlap = true;
            }
        }
        return new GapSummary(gapSeconds, gapCount, hasOverlap);
    }

    private record GapSummary(BigDecimal gapSeconds, int gapCount, boolean hasOverlap) {
    }

    private NarrationMixSettingsVo toMixSettings(String jobId) {
        return mixSettingsRepository.findByJobId(jobId)
                .map(record -> new NarrationMixSettingsVo(
                        record.duckingVolume().setScale(3, java.math.RoundingMode.HALF_UP),
                        record.narrationVolume().setScale(3, java.math.RoundingMode.HALF_UP),
                        record.fadeDurationMs(),
                        record.updatedAt()
                ))
                .orElse(new NarrationMixSettingsVo(
                        DEFAULT_DUCKING_VOLUME,
                        DEFAULT_NARRATION_VOLUME,
                        DEFAULT_FADE_DURATION_MS,
                        null
                ));
    }

    private NarrationSegmentVo toSegment(NarrationSegmentRecord record) {
        BigDecimal start = normalizeSeconds(record.startSeconds(), "startSeconds");
        BigDecimal end = normalizeSeconds(record.endSeconds(), "endSeconds");
        String text = record.text() == null ? "" : record.text();
        return new NarrationSegmentVo(
                record.segmentIndex(),
                start,
                end,
                end.subtract(start),
                text,
                normalizeVoice(record.voice()),
                text.length(),
                record.updatedAt()
        );
    }

    private BigDecimal normalizeSeconds(BigDecimal value, String label) {
        if (value == null) {
            throw new IllegalArgumentException("Narration " + label + " is required.");
        }
        return value.setScale(3, java.math.RoundingMode.HALF_UP);
    }

    private String normalizeVoice(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private BigDecimal normalizeMixDecimal(BigDecimal value, String label, BigDecimal max) {
        if (value == null) {
            throw new IllegalArgumentException("Narration " + label + " is required.");
        }
        BigDecimal normalized = value.setScale(3, java.math.RoundingMode.HALF_UP);
        if (normalized.compareTo(ZERO) < 0 || normalized.compareTo(max) > 0) {
            throw new IllegalArgumentException(label + " must be between 0.00 and " + max.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + ".");
        }
        return normalized;
    }

    private int normalizeFadeDuration(Integer value) {
        if (value == null) {
            throw new IllegalArgumentException("Narration fadeDurationMs is required.");
        }
        if (value < 0 || value > MAX_FADE_DURATION_MS) {
            throw new IllegalArgumentException("fadeDurationMs must be between 0 and 5000.");
        }
        return value;
    }
}
