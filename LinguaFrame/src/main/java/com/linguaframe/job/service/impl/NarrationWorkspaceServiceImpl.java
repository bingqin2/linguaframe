package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.dto.SaveNarrationSegmentsRequest;
import com.linguaframe.job.domain.entity.NarrationSegmentRecord;
import com.linguaframe.job.domain.vo.NarrationSegmentVo;
import com.linguaframe.job.domain.vo.NarrationWorkspaceVo;
import com.linguaframe.job.repository.NarrationSegmentRepository;
import com.linguaframe.job.service.NarrationWorkspaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    private final NarrationSegmentRepository repository;
    private final Clock clock;

    public NarrationWorkspaceServiceImpl(NarrationSegmentRepository repository) {
        this(repository, Clock.systemUTC());
    }

    @Autowired
    public NarrationWorkspaceServiceImpl(NarrationSegmentRepository repository, Clock clock) {
        this.repository = repository;
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
                segments,
                List.of(
                        "Narration text is available only in the editing workspace.",
                        "Narration evidence exports use counts and timing metadata instead of script bodies.",
                        "Narration outputs do not replace generated subtitles, reviewed subtitles, or dubbed video artifacts."
                )
        );
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
}
