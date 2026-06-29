package com.linguaframe.job.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.bo.TranscriptionResultBo;
import com.linguaframe.job.domain.bo.TranslationResultBo;
import com.linguaframe.job.domain.dto.UpdateSubtitleDraftRequest;
import com.linguaframe.job.domain.entity.SubtitleDraftSegmentRecord;
import com.linguaframe.job.domain.enums.SubtitleDraftExportFormat;
import com.linguaframe.job.domain.enums.SubtitleReviewDecision;
import com.linguaframe.job.domain.enums.SubtitleReviewIssueCategory;
import com.linguaframe.job.domain.vo.SubtitleDraftSummaryVo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.repository.SubtitleDraftSegmentRepository;
import com.linguaframe.job.service.impl.SubtitleDraftServiceImpl;
import com.linguaframe.job.service.impl.SubtitleExportServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubtitleDraftServiceTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-28T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void overlaysDraftTextOnGeneratedSubtitles() {
        FakeDraftRepository repository = new FakeDraftRepository(List.of(new SubtitleDraftSegmentRecord(
                "draft-0",
                "job-draft",
                "zh-CN",
                0,
                "修正后的第一行",
                SubtitleReviewDecision.EDITED,
                List.of(SubtitleReviewIssueCategory.TERM, SubtitleReviewIssueCategory.READABILITY),
                "Use the established term.",
                Instant.parse("2026-06-28T09:00:00Z"),
                Instant.parse("2026-06-28T09:30:00Z")
        )));
        SubtitleDraftService service = service(repository);

        SubtitleDraftSummaryVo result = service.getDraft("job-draft", "zh-CN");

        assertThat(result.jobId()).isEqualTo("job-draft");
        assertThat(result.targetLanguage()).isEqualTo("zh-CN");
        assertThat(result.segmentCount()).isEqualTo(2);
        assertThat(result.editedSegmentCount()).isEqualTo(1);
        assertThat(result.reviewedSegmentCount()).isEqualTo(1);
        assertThat(result.editedDecisionCount()).isEqualTo(1);
        assertThat(result.annotationCount()).isEqualTo(2);
        assertThat(result.reviewerNoteCount()).isEqualTo(1);
        assertThat(result.lastUpdatedAt()).isEqualTo(Instant.parse("2026-06-28T09:30:00Z"));
        assertThat(result.segments())
                .extracting(segment -> segment.index() + ":" + segment.sourceText() + ":" + segment.generatedText()
                        + ":" + segment.draftText() + ":" + segment.edited() + ":" + segment.decision())
                .containsExactly(
                        "0:Hello.:第一行:修正后的第一行:true:EDITED",
                        "1:Welcome.:第二行:第二行:false:UNREVIEWED"
                );
        assertThat(result.segments().getFirst().issueCategories())
                .containsExactly(SubtitleReviewIssueCategory.TERM, SubtitleReviewIssueCategory.READABILITY);
        assertThat(result.segments().getFirst().reviewerNote()).isEqualTo("Use the established term.");
        assertThat(result.segments().getFirst().noteLength()).isEqualTo(25);
    }

    @Test
    void updatesDraftRowsBySegmentIndex() {
        FakeDraftRepository repository = new FakeDraftRepository(List.of());
        SubtitleDraftService service = service(repository);

        SubtitleDraftSummaryVo result = service.updateDraft(
                "job-draft",
                "zh-CN",
                new UpdateSubtitleDraftRequest(List.of(
                        new UpdateSubtitleDraftRequest.Segment(
                                0,
                                " 新第一行 ",
                                SubtitleReviewDecision.ACCEPTED,
                                List.of(),
                                "Reviewed unchanged."
                        ),
                        new UpdateSubtitleDraftRequest.Segment(
                                1,
                                "新第二行",
                                SubtitleReviewDecision.NEEDS_FOLLOWUP,
                                List.of(SubtitleReviewIssueCategory.TIMING),
                                "Timing needs one more pass."
                        )
                ))
        );

        assertThat(result.editedSegmentCount()).isEqualTo(2);
        assertThat(result.reviewedSegmentCount()).isEqualTo(2);
        assertThat(result.acceptedSegmentCount()).isEqualTo(1);
        assertThat(result.followupSegmentCount()).isEqualTo(1);
        assertThat(result.annotationCount()).isEqualTo(1);
        assertThat(result.reviewerNoteCount()).isEqualTo(2);
        assertThat(result.lastUpdatedAt()).isEqualTo(Instant.parse("2026-06-28T10:00:00Z"));
        assertThat(result.segments())
                .extracting(segment -> segment.index() + ":" + segment.draftText() + ":" + segment.decision())
                .containsExactly("0:新第一行:ACCEPTED", "1:新第二行:NEEDS_FOLLOWUP");
        assertThat(repository.records)
                .extracting(record -> record.segmentIndex() + ":" + record.text() + ":" + record.reviewDecision() + ":" + record.issueCategories())
                .containsExactly("0:新第一行:ACCEPTED:[]", "1:新第二行:NEEDS_FOLLOWUP:[TIMING]");
    }

    @Test
    void rejectsDraftIndexesThatDoNotExistInGeneratedSubtitles() {
        SubtitleDraftService service = service(new FakeDraftRepository(List.of()));

        assertThatThrownBy(() -> service.updateDraft(
                "job-draft",
                "zh-CN",
                new UpdateSubtitleDraftRequest(List.of(new UpdateSubtitleDraftRequest.Segment(
                        7,
                        "无效行",
                        SubtitleReviewDecision.EDITED,
                        List.of(),
                        null
                )))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist: 7");
    }

    @Test
    void rejectsDuplicateIssueCategoriesAndOversizedReviewerNotes() {
        SubtitleDraftService service = service(new FakeDraftRepository(List.of()));

        assertThatThrownBy(() -> service.updateDraft(
                "job-draft",
                "zh-CN",
                new UpdateSubtitleDraftRequest(List.of(new UpdateSubtitleDraftRequest.Segment(
                        0,
                        "第一行",
                        SubtitleReviewDecision.EDITED,
                        List.of(SubtitleReviewIssueCategory.TONE, SubtitleReviewIssueCategory.TONE),
                        null
                )))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate subtitle review issue category");

        assertThatThrownBy(() -> service.updateDraft(
                "job-draft",
                "zh-CN",
                new UpdateSubtitleDraftRequest(List.of(new UpdateSubtitleDraftRequest.Segment(
                        0,
                        "第一行",
                        SubtitleReviewDecision.EDITED,
                        List.of(),
                        "x".repeat(501)
                )))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reviewer note must be at most 500 characters");
    }

    @Test
    void clearsDraftRowsWithoutChangingGeneratedSubtitles() {
        FakeSubtitleService subtitleService = new FakeSubtitleService(generatedSubtitles());
        FakeDraftRepository repository = new FakeDraftRepository(List.of(new SubtitleDraftSegmentRecord(
                "draft-0",
                "job-draft",
                "zh-CN",
                0,
                "修正后的第一行",
                SubtitleReviewDecision.EDITED,
                List.of(SubtitleReviewIssueCategory.TERM),
                "Use known title.",
                Instant.parse("2026-06-28T09:00:00Z"),
                Instant.parse("2026-06-28T09:30:00Z")
        )));
        SubtitleDraftService service = service(subtitleService, repository);

        SubtitleDraftSummaryVo result = service.clearDraft("job-draft", "zh-CN");

        assertThat(result.editedSegmentCount()).isZero();
        assertThat(result.reviewedSegmentCount()).isZero();
        assertThat(result.annotationCount()).isZero();
        assertThat(result.reviewerNoteCount()).isZero();
        assertThat(result.segments())
                .extracting(segment -> segment.index() + ":" + segment.draftText() + ":" + segment.edited() + ":" + segment.decision())
                .containsExactly("0:第一行:false:UNREVIEWED", "1:第二行:false:UNREVIEWED");
        assertThat(subtitleService.segments)
                .extracting(SubtitleSegmentVo::text)
                .containsExactly("第一行", "第二行");
    }

    @Test
    void exportsCorrectedSubtitleFormatsFromDraftOverlay() {
        FakeDraftRepository repository = new FakeDraftRepository(List.of(new SubtitleDraftSegmentRecord(
                "draft-1",
                "job-draft",
                "zh-CN",
                1,
                "修正后的第二行",
                SubtitleReviewDecision.EDITED,
                List.of(SubtitleReviewIssueCategory.TERM),
                "raw reviewer note must not be exported",
                Instant.parse("2026-06-28T09:00:00Z"),
                Instant.parse("2026-06-28T09:30:00Z")
        )));
        SubtitleDraftService service = service(repository);

        String json = new String(service.exportDraft("job-draft", "zh-CN", SubtitleDraftExportFormat.JSON));
        String srt = new String(service.exportDraft("job-draft", "zh-CN", SubtitleDraftExportFormat.SRT));
        String vtt = new String(service.exportDraft("job-draft", "zh-CN", SubtitleDraftExportFormat.VTT));

        assertThat(json).contains("\"language\":\"zh-CN\"");
        assertThat(json).contains("修正后的第二行");
        assertThat(json).doesNotContain("raw reviewer note must not be exported");
        assertThat(json).doesNotContain("TERM");
        assertThat(srt).contains("00:00:01,200 --> 00:00:02,800");
        assertThat(srt).contains("修正后的第二行");
        assertThat(srt).doesNotContain("raw reviewer note must not be exported");
        assertThat(vtt).startsWith("WEBVTT");
        assertThat(vtt).contains("00:00:01.200 --> 00:00:02.800");
        assertThat(vtt).contains("修正后的第二行");
        assertThat(vtt).doesNotContain("raw reviewer note must not be exported");
    }

    @Test
    void throwsNotFoundWhenGeneratedTargetSubtitlesAreMissing() {
        SubtitleDraftService service = service(new FakeSubtitleService(List.of()), new FakeDraftRepository(List.of()));

        assertThatThrownBy(() -> service.getDraft("missing-job", "zh-CN"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Target subtitles not found");
    }

    private SubtitleDraftService service(FakeDraftRepository repository) {
        return service(new FakeSubtitleService(generatedSubtitles()), repository);
    }

    private SubtitleDraftService service(FakeSubtitleService subtitleService, FakeDraftRepository repository) {
        return new SubtitleDraftServiceImpl(
                subtitleService,
                new FakeTranscriptService(),
                new SubtitleExportServiceImpl(new ObjectMapper()),
                repository,
                CLOCK
        );
    }

    private List<SubtitleSegmentVo> generatedSubtitles() {
        return List.of(
                new SubtitleSegmentVo("zh-CN", 0, 0L, 1_000L, "第一行"),
                new SubtitleSegmentVo("zh-CN", 1, 1_200L, 2_800L, "第二行")
        );
    }

    private static final class FakeSubtitleService implements SubtitleService {
        private final List<SubtitleSegmentVo> segments;

        private FakeSubtitleService(List<SubtitleSegmentVo> segments) {
            this.segments = segments;
        }

        @Override
        public List<SubtitleSegmentVo> replaceSubtitles(String jobId, String language, TranslationResultBo result) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SubtitleSegmentVo> listSubtitles(String jobId, String language) {
            return segments;
        }
    }

    private static final class FakeTranscriptService implements TranscriptService {

        @Override
        public List<TranscriptSegmentVo> replaceTranscript(String jobId, TranscriptionResultBo result) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TranscriptSegmentVo> listTranscript(String jobId) {
            return List.of(
                    new TranscriptSegmentVo(0, 0L, 1_000L, "Hello."),
                    new TranscriptSegmentVo(1, 1_200L, 2_800L, "Welcome.")
            );
        }
    }

    private static final class FakeDraftRepository extends SubtitleDraftSegmentRepository {
        private final List<SubtitleDraftSegmentRecord> records = new ArrayList<>();

        private FakeDraftRepository(List<SubtitleDraftSegmentRecord> records) {
            super(null);
            this.records.addAll(records);
        }

        @Override
        public List<SubtitleDraftSegmentRecord> findByJobIdAndLanguage(String jobId, String language) {
            return records.stream()
                    .filter(record -> record.jobId().equals(jobId) && record.language().equals(language))
                    .toList();
        }

        @Override
        public void upsert(SubtitleDraftSegmentRecord record) {
            records.removeIf(existing -> existing.jobId().equals(record.jobId())
                    && existing.language().equals(record.language())
                    && existing.segmentIndex() == record.segmentIndex());
            records.add(record);
            records.sort(java.util.Comparator.comparingInt(SubtitleDraftSegmentRecord::segmentIndex));
        }

        @Override
        public void deleteByJobIdAndLanguage(String jobId, String language) {
            records.removeIf(record -> record.jobId().equals(jobId) && record.language().equals(language));
        }
    }
}
