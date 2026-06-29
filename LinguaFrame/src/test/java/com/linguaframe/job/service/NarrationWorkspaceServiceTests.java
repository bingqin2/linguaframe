package com.linguaframe.job.service;

import com.linguaframe.job.domain.dto.SaveNarrationSegmentsRequest;
import com.linguaframe.job.domain.entity.NarrationSegmentRecord;
import com.linguaframe.job.domain.vo.NarrationWorkspaceVo;
import com.linguaframe.job.repository.NarrationSegmentRepository;
import com.linguaframe.job.service.impl.NarrationWorkspaceServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NarrationWorkspaceServiceTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-29T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void savesValidTimeCodedNarrationSegments() {
        FakeNarrationSegmentRepository repository = new FakeNarrationSegmentRepository();
        NarrationWorkspaceService service = new NarrationWorkspaceServiceImpl(repository, CLOCK);

        NarrationWorkspaceVo workspace = service.saveWorkspace("job-narration", new SaveNarrationSegmentsRequest(List.of(
                new SaveNarrationSegmentsRequest.Segment(0, new BigDecimal("15.000"), new BigDecimal("28.000"), "Explain the first scene.", "alloy"),
                new SaveNarrationSegmentsRequest.Segment(1, new BigDecimal("55.000"), new BigDecimal("70.500"), "Explain the second scene.", "alloy")
        )));

        assertThat(workspace.status()).isEqualTo("DRAFT_READY");
        assertThat(workspace.segmentCount()).isEqualTo(2);
        assertThat(workspace.totalDurationSeconds()).isEqualByComparingTo("28.500");
        assertThat(workspace.totalCharacterCount()).isEqualTo(49);
        assertThat(workspace.generationReady()).isTrue();
        assertThat(workspace.segments())
                .extracting(segment -> segment.index() + ":" + segment.startSeconds() + ":" + segment.endSeconds() + ":" + segment.text() + ":" + segment.voice())
                .containsExactly(
                        "0:15.000:28.000:Explain the first scene.:alloy",
                        "1:55.000:70.500:Explain the second scene.:alloy"
                );
        assertThat(repository.records)
                .extracting(record -> record.segmentIndex() + ":" + record.text() + ":" + record.voice())
                .containsExactly("0:Explain the first scene.:alloy", "1:Explain the second scene.:alloy");
    }

    @Test
    void rejectsOverlappingSegmentsAndInvalidRanges() {
        NarrationWorkspaceService service = new NarrationWorkspaceServiceImpl(new FakeNarrationSegmentRepository(), CLOCK);

        assertThatThrownBy(() -> service.saveWorkspace("job-narration", new SaveNarrationSegmentsRequest(List.of(
                new SaveNarrationSegmentsRequest.Segment(0, new BigDecimal("10.000"), new BigDecimal("20.000"), "First", null),
                new SaveNarrationSegmentsRequest.Segment(1, new BigDecimal("19.500"), new BigDecimal("25.000"), "Overlap", null)
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Narration segments must not overlap");

        assertThatThrownBy(() -> service.saveWorkspace("job-narration", new SaveNarrationSegmentsRequest(List.of(
                new SaveNarrationSegmentsRequest.Segment(0, new BigDecimal("12.000"), new BigDecimal("12.000"), "Invalid", null)
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endSeconds must be greater than startSeconds");
    }

    @Test
    void rejectsTooLongTextAndNonContiguousIndexes() {
        NarrationWorkspaceService service = new NarrationWorkspaceServiceImpl(new FakeNarrationSegmentRepository(), CLOCK);

        assertThatThrownBy(() -> service.saveWorkspace("job-narration", new SaveNarrationSegmentsRequest(List.of(
                new SaveNarrationSegmentsRequest.Segment(1, new BigDecimal("1.000"), new BigDecimal("2.000"), "Skipped index", null)
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Narration segment indexes must start at 0 and be contiguous");

        assertThatThrownBy(() -> service.saveWorkspace("job-narration", new SaveNarrationSegmentsRequest(List.of(
                new SaveNarrationSegmentsRequest.Segment(0, new BigDecimal("1.000"), new BigDecimal("2.000"), "x".repeat(1001), null)
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Narration text must be at most 1000 characters");
    }

    @Test
    void clearsNarrationWorkspace() {
        FakeNarrationSegmentRepository repository = new FakeNarrationSegmentRepository();
        repository.records.add(new NarrationSegmentRecord(
                "narration-1",
                "job-narration",
                0,
                new BigDecimal("1.000"),
                new BigDecimal("2.000"),
                "Existing",
                "verse",
                Instant.parse("2026-06-29T09:00:00Z"),
                Instant.parse("2026-06-29T09:00:00Z")
        ));
        NarrationWorkspaceService service = new NarrationWorkspaceServiceImpl(repository, CLOCK);

        NarrationWorkspaceVo workspace = service.clearWorkspace("job-narration");

        assertThat(workspace.status()).isEqualTo("EMPTY");
        assertThat(workspace.segmentCount()).isZero();
        assertThat(workspace.generationReady()).isFalse();
        assertThat(repository.records).isEmpty();
    }

    private static final class FakeNarrationSegmentRepository implements NarrationSegmentRepository {

        private final List<NarrationSegmentRecord> records = new ArrayList<>();

        @Override
        public void replaceSegments(String jobId, List<NarrationSegmentRecord> segments) {
            records.removeIf(record -> record.jobId().equals(jobId));
            records.addAll(segments);
        }

        @Override
        public List<NarrationSegmentRecord> findByJobId(String jobId) {
            return records.stream()
                    .filter(record -> record.jobId().equals(jobId))
                    .toList();
        }

        @Override
        public void deleteByJobId(String jobId) {
            records.removeIf(record -> record.jobId().equals(jobId));
        }
    }
}
