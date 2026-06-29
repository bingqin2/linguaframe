package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.dto.UpdateSubtitleDraftRequest;
import com.linguaframe.job.domain.entity.SubtitleDraftSegmentRecord;
import com.linguaframe.job.domain.enums.SubtitleDraftExportFormat;
import com.linguaframe.job.domain.enums.SubtitleReviewDecision;
import com.linguaframe.job.domain.enums.SubtitleReviewIssueCategory;
import com.linguaframe.job.domain.vo.SubtitleDraftSegmentVo;
import com.linguaframe.job.domain.vo.SubtitleDraftSummaryVo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.repository.SubtitleDraftSegmentRepository;
import com.linguaframe.job.service.SubtitleDraftService;
import com.linguaframe.job.service.SubtitleExportService;
import com.linguaframe.job.service.SubtitleService;
import com.linguaframe.job.service.TranscriptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SubtitleDraftServiceImpl implements SubtitleDraftService {

    private final SubtitleService subtitleService;
    private final TranscriptService transcriptService;
    private final SubtitleExportService subtitleExportService;
    private final SubtitleDraftSegmentRepository draftRepository;
    private final Clock clock;

    @Autowired
    public SubtitleDraftServiceImpl(
            SubtitleService subtitleService,
            TranscriptService transcriptService,
            SubtitleExportService subtitleExportService,
            SubtitleDraftSegmentRepository draftRepository
    ) {
        this(subtitleService, transcriptService, subtitleExportService, draftRepository, Clock.systemUTC());
    }

    public SubtitleDraftServiceImpl(
            SubtitleService subtitleService,
            TranscriptService transcriptService,
            SubtitleExportService subtitleExportService,
            SubtitleDraftSegmentRepository draftRepository,
            Clock clock
    ) {
        this.subtitleService = subtitleService;
        this.transcriptService = transcriptService;
        this.subtitleExportService = subtitleExportService;
        this.draftRepository = draftRepository;
        this.clock = clock;
    }

    @Override
    public SubtitleDraftSummaryVo getDraft(String jobId, String language) {
        String normalizedLanguage = normalizeLanguage(language);
        List<SubtitleSegmentVo> generated = generatedSubtitlesOrThrow(jobId, normalizedLanguage);
        Map<Integer, TranscriptSegmentVo> sourceByIndex = transcriptService.listTranscript(jobId).stream()
                .collect(Collectors.toMap(TranscriptSegmentVo::index, Function.identity(), (left, right) -> left));
        Map<Integer, SubtitleDraftSegmentRecord> draftByIndex = draftRepository
                .findByJobIdAndLanguage(jobId, normalizedLanguage)
                .stream()
                .collect(Collectors.toMap(SubtitleDraftSegmentRecord::segmentIndex, Function.identity()));

        List<SubtitleDraftSegmentVo> segments = generated.stream()
                .map(segment -> toDraftVo(segment, sourceByIndex.get(segment.index()), draftByIndex.get(segment.index())))
                .toList();
        int editedSegmentCount = (int) segments.stream().filter(SubtitleDraftSegmentVo::edited).count();
        int reviewedSegmentCount = (int) segments.stream()
                .filter(segment -> segment.decision() != SubtitleReviewDecision.UNREVIEWED)
                .count();
        int acceptedSegmentCount = (int) segments.stream()
                .filter(segment -> segment.decision() == SubtitleReviewDecision.ACCEPTED)
                .count();
        int editedDecisionCount = (int) segments.stream()
                .filter(segment -> segment.decision() == SubtitleReviewDecision.EDITED)
                .count();
        int followupSegmentCount = (int) segments.stream()
                .filter(segment -> segment.decision() == SubtitleReviewDecision.NEEDS_FOLLOWUP)
                .count();
        int annotationCount = segments.stream()
                .mapToInt(segment -> segment.issueCategories().size())
                .sum();
        int reviewerNoteCount = (int) segments.stream()
                .filter(segment -> segment.reviewerNote() != null && !segment.reviewerNote().isBlank())
                .count();
        Instant lastUpdatedAt = segments.stream()
                .map(SubtitleDraftSegmentVo::updatedAt)
                .filter(value -> value != null)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new SubtitleDraftSummaryVo(
                jobId,
                normalizedLanguage,
                segments.size(),
                editedSegmentCount,
                reviewedSegmentCount,
                acceptedSegmentCount,
                editedDecisionCount,
                followupSegmentCount,
                annotationCount,
                reviewerNoteCount,
                lastUpdatedAt,
                segments
        );
    }

    @Override
    public SubtitleDraftSummaryVo updateDraft(String jobId, String language, UpdateSubtitleDraftRequest request) {
        String normalizedLanguage = normalizeLanguage(language);
        List<SubtitleSegmentVo> generated = generatedSubtitlesOrThrow(jobId, normalizedLanguage);
        validateRequest(request, generated);
        Instant now = Instant.now(clock);
        for (UpdateSubtitleDraftRequest.Segment segment : request.segments()) {
            draftRepository.upsert(new SubtitleDraftSegmentRecord(
                    UUID.randomUUID().toString(),
                    jobId,
                    normalizedLanguage,
                    segment.index(),
                    segment.text().trim(),
                    normalizeDecision(segment.decision()),
                    normalizeIssueCategories(segment.issueCategories()),
                    normalizeReviewerNote(segment.reviewerNote()),
                    now,
                    now
            ));
        }
        return getDraft(jobId, normalizedLanguage);
    }

    @Override
    public SubtitleDraftSummaryVo clearDraft(String jobId, String language) {
        String normalizedLanguage = normalizeLanguage(language);
        generatedSubtitlesOrThrow(jobId, normalizedLanguage);
        draftRepository.deleteByJobIdAndLanguage(jobId, normalizedLanguage);
        return getDraft(jobId, normalizedLanguage);
    }

    @Override
    public byte[] exportDraft(String jobId, String language, SubtitleDraftExportFormat format) {
        if (format == null) {
            throw new IllegalArgumentException("Subtitle draft export format must not be null.");
        }
        List<SubtitleSegmentVo> corrected = correctedSubtitleSegments(jobId, language);
        return switch (format) {
            case JSON -> subtitleExportService.exportSubtitleJson(corrected);
            case SRT -> subtitleExportService.exportSubtitleSrt(corrected);
            case VTT -> subtitleExportService.exportSubtitleVtt(corrected);
        };
    }

    private List<SubtitleSegmentVo> correctedSubtitleSegments(String jobId, String language) {
        SubtitleDraftSummaryVo draft = getDraft(jobId, language);
        return draft.segments().stream()
                .map(segment -> new SubtitleSegmentVo(
                        draft.targetLanguage(),
                        segment.index(),
                        segment.startMs(),
                        segment.endMs(),
                        segment.draftText()
                ))
                .toList();
    }

    private SubtitleDraftSegmentVo toDraftVo(
            SubtitleSegmentVo generated,
            TranscriptSegmentVo source,
            SubtitleDraftSegmentRecord draft
    ) {
        boolean edited = draft != null;
        SubtitleReviewDecision decision = edited ? normalizeDecision(draft.reviewDecision()) : SubtitleReviewDecision.UNREVIEWED;
        List<SubtitleReviewIssueCategory> issueCategories = edited ? normalizeIssueCategories(draft.issueCategories()) : List.of();
        String reviewerNote = edited ? normalizeReviewerNote(draft.reviewerNote()) : null;
        return new SubtitleDraftSegmentVo(
                generated.index(),
                generated.startMs(),
                generated.endMs(),
                source == null ? "" : source.text(),
                generated.text(),
                edited ? draft.text() : generated.text(),
                edited,
                edited ? draft.updatedAt() : null,
                decision,
                issueCategories,
                reviewerNote,
                reviewerNote == null ? 0 : reviewerNote.length()
        );
    }

    private List<SubtitleSegmentVo> generatedSubtitlesOrThrow(String jobId, String language) {
        List<SubtitleSegmentVo> generated = subtitleService.listSubtitles(jobId, language);
        if (generated.isEmpty()) {
            throw new NoSuchElementException("Target subtitles not found for job " + jobId + " and language " + language + ".");
        }
        return generated;
    }

    private void validateRequest(UpdateSubtitleDraftRequest request, List<SubtitleSegmentVo> generated) {
        if (request == null || request.segments() == null || request.segments().isEmpty()) {
            throw new IllegalArgumentException("Subtitle draft update must contain at least one segment.");
        }
        Set<Integer> validIndexes = generated.stream()
                .map(SubtitleSegmentVo::index)
                .collect(Collectors.toSet());
        for (UpdateSubtitleDraftRequest.Segment segment : request.segments()) {
            if (!validIndexes.contains(segment.index())) {
                throw new IllegalArgumentException("Subtitle draft segment index does not exist: " + segment.index());
            }
            if (segment.text() == null || segment.text().isBlank()) {
                throw new IllegalArgumentException("Subtitle draft text must not be blank.");
            }
            validateIssueCategories(segment.issueCategories());
            String reviewerNote = normalizeReviewerNote(segment.reviewerNote());
            if (reviewerNote != null && reviewerNote.length() > 500) {
                throw new IllegalArgumentException("Reviewer note must be at most 500 characters.");
            }
        }
    }

    private void validateIssueCategories(List<SubtitleReviewIssueCategory> categories) {
        if (categories == null || categories.isEmpty()) {
            return;
        }
        Set<SubtitleReviewIssueCategory> seen = new HashSet<>();
        for (SubtitleReviewIssueCategory category : categories) {
            if (category == null) {
                throw new IllegalArgumentException("Subtitle review issue category must not be null.");
            }
            if (!seen.add(category)) {
                throw new IllegalArgumentException("Duplicate subtitle review issue category: " + category + ".");
            }
        }
    }

    private SubtitleReviewDecision normalizeDecision(SubtitleReviewDecision decision) {
        return decision == null ? SubtitleReviewDecision.EDITED : decision;
    }

    private List<SubtitleReviewIssueCategory> normalizeIssueCategories(List<SubtitleReviewIssueCategory> categories) {
        if (categories == null || categories.isEmpty()) {
            return List.of();
        }
        return List.copyOf(categories);
    }

    private String normalizeReviewerNote(String reviewerNote) {
        if (reviewerNote == null || reviewerNote.isBlank()) {
            return null;
        }
        return reviewerNote.trim();
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            throw new IllegalArgumentException("Subtitle draft language must not be blank.");
        }
        return language.trim();
    }
}
