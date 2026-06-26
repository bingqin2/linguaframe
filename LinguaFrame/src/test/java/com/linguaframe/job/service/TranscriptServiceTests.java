package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.TranscriptionResultBo;
import com.linguaframe.job.domain.bo.TranscriptionSegmentBo;
import com.linguaframe.job.domain.entity.TranscriptSegmentRecord;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.repository.TranscriptSegmentRepository;
import com.linguaframe.job.service.impl.TranscriptServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranscriptServiceTests {

    @Test
    void replacesExistingTranscriptAndReturnsOrderedSegments() {
        InMemoryTranscriptSegmentRepository repository = new InMemoryTranscriptSegmentRepository();
        Clock clock = Clock.fixed(Instant.parse("2026-06-26T10:00:00Z"), ZoneOffset.UTC);
        TranscriptService service = new TranscriptServiceImpl(repository, clock);
        repository.saveAll(List.of(new TranscriptSegmentRecord(
                "old-segment",
                "transcript-job-1",
                0,
                0L,
                400L,
                "Old text",
                Instant.parse("2026-06-26T09:00:00Z")
        )));

        List<TranscriptSegmentVo> segments = service.replaceTranscript("transcript-job-1", new TranscriptionResultBo(List.of(
                new TranscriptionSegmentBo(1, 1_200L, 2_500L, "Second segment"),
                new TranscriptionSegmentBo(0, 0L, 1_200L, "First segment")
        )));

        assertThat(segments)
                .containsExactly(
                        new TranscriptSegmentVo(0, 0L, 1_200L, "First segment"),
                        new TranscriptSegmentVo(1, 1_200L, 2_500L, "Second segment")
                );
        assertThat(repository.deletedJobIds).containsExactly("transcript-job-1");
    }

    @Test
    void rejectsInvalidSegmentRanges() {
        TranscriptService service = new TranscriptServiceImpl(
                new InMemoryTranscriptSegmentRepository(),
                Clock.fixed(Instant.parse("2026-06-26T10:00:00Z"), ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.replaceTranscript("transcript-job-1", new TranscriptionResultBo(List.of(
                new TranscriptionSegmentBo(0, 1_000L, 1_000L, "Invalid segment")
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endMs");
    }

    private static class InMemoryTranscriptSegmentRepository extends TranscriptSegmentRepository {

        private final List<TranscriptSegmentRecord> records = new ArrayList<>();
        private final List<String> deletedJobIds = new ArrayList<>();

        private InMemoryTranscriptSegmentRepository() {
            super(null);
        }

        @Override
        public void saveAll(List<TranscriptSegmentRecord> records) {
            this.records.addAll(records);
        }

        @Override
        public List<TranscriptSegmentRecord> findByJobId(String jobId) {
            return records.stream()
                    .filter(record -> record.jobId().equals(jobId))
                    .sorted((left, right) -> Integer.compare(left.segmentIndex(), right.segmentIndex()))
                    .toList();
        }

        @Override
        public void deleteByJobId(String jobId) {
            deletedJobIds.add(jobId);
            records.removeIf(record -> record.jobId().equals(jobId));
        }
    }
}
