package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.bo.TranscriptionResultBo;
import com.linguaframe.job.domain.bo.TranscriptionSegmentBo;
import com.linguaframe.job.domain.entity.TranscriptSegmentRecord;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.repository.TranscriptSegmentRepository;
import com.linguaframe.job.service.TranscriptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TranscriptServiceImpl implements TranscriptService {

    private final TranscriptSegmentRepository transcriptSegmentRepository;
    private final Clock clock;

    @Autowired
    public TranscriptServiceImpl(TranscriptSegmentRepository transcriptSegmentRepository) {
        this(transcriptSegmentRepository, Clock.systemUTC());
    }

    public TranscriptServiceImpl(TranscriptSegmentRepository transcriptSegmentRepository, Clock clock) {
        this.transcriptSegmentRepository = transcriptSegmentRepository;
        this.clock = clock;
    }

    @Override
    public List<TranscriptSegmentVo> replaceTranscript(String jobId, TranscriptionResultBo result) {
        validate(result);
        Instant createdAt = Instant.now(clock);
        List<TranscriptSegmentRecord> records = result.segments().stream()
                .map(segment -> toRecord(jobId, segment, createdAt))
                .toList();

        transcriptSegmentRepository.deleteByJobId(jobId);
        transcriptSegmentRepository.saveAll(records);
        return listTranscript(jobId);
    }

    @Override
    public List<TranscriptSegmentVo> listTranscript(String jobId) {
        return transcriptSegmentRepository.findByJobId(jobId).stream()
                .map(this::toVo)
                .toList();
    }

    private void validate(TranscriptionResultBo result) {
        if (result == null || result.segments() == null || result.segments().isEmpty()) {
            throw new IllegalArgumentException("Transcript result must contain at least one segment.");
        }
        for (TranscriptionSegmentBo segment : result.segments()) {
            if (segment.index() < 0) {
                throw new IllegalArgumentException("Transcript segment index must be non-negative.");
            }
            if (segment.startMs() < 0) {
                throw new IllegalArgumentException("Transcript segment startMs must be non-negative.");
            }
            if (segment.endMs() <= segment.startMs()) {
                throw new IllegalArgumentException("Transcript segment endMs must be greater than startMs.");
            }
            if (segment.text() == null || segment.text().isBlank()) {
                throw new IllegalArgumentException("Transcript segment text must not be blank.");
            }
        }
    }

    private TranscriptSegmentRecord toRecord(String jobId, TranscriptionSegmentBo segment, Instant createdAt) {
        return new TranscriptSegmentRecord(
                UUID.randomUUID().toString(),
                jobId,
                segment.index(),
                segment.startMs(),
                segment.endMs(),
                segment.text().trim(),
                createdAt
        );
    }

    private TranscriptSegmentVo toVo(TranscriptSegmentRecord record) {
        return new TranscriptSegmentVo(
                record.segmentIndex(),
                record.startMs(),
                record.endMs(),
                record.text()
        );
    }
}
