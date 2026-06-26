package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.bo.TranslationResultBo;
import com.linguaframe.job.domain.bo.TranslationSegmentBo;
import com.linguaframe.job.domain.entity.SubtitleSegmentRecord;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.repository.SubtitleSegmentRepository;
import com.linguaframe.job.service.SubtitleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SubtitleServiceImpl implements SubtitleService {

    private final SubtitleSegmentRepository subtitleSegmentRepository;
    private final Clock clock;

    @Autowired
    public SubtitleServiceImpl(SubtitleSegmentRepository subtitleSegmentRepository) {
        this(subtitleSegmentRepository, Clock.systemUTC());
    }

    public SubtitleServiceImpl(SubtitleSegmentRepository subtitleSegmentRepository, Clock clock) {
        this.subtitleSegmentRepository = subtitleSegmentRepository;
        this.clock = clock;
    }

    @Override
    public List<SubtitleSegmentVo> replaceSubtitles(String jobId, String language, TranslationResultBo result) {
        validateLanguage(language);
        validate(result);
        Instant createdAt = Instant.now(clock);
        String normalizedLanguage = language.trim();
        List<SubtitleSegmentRecord> records = result.segments().stream()
                .map(segment -> toRecord(jobId, normalizedLanguage, segment, createdAt))
                .toList();

        subtitleSegmentRepository.deleteByJobIdAndLanguage(jobId, normalizedLanguage);
        subtitleSegmentRepository.saveAll(records);
        return listSubtitles(jobId, normalizedLanguage);
    }

    @Override
    public List<SubtitleSegmentVo> listSubtitles(String jobId, String language) {
        validateLanguage(language);
        return subtitleSegmentRepository.findByJobIdAndLanguage(jobId, language.trim()).stream()
                .map(this::toVo)
                .toList();
    }

    private void validateLanguage(String language) {
        if (language == null || language.isBlank()) {
            throw new IllegalArgumentException("Subtitle language must not be blank.");
        }
    }

    private void validate(TranslationResultBo result) {
        if (result == null || result.segments() == null || result.segments().isEmpty()) {
            throw new IllegalArgumentException("Translation result must contain at least one segment.");
        }
        for (TranslationSegmentBo segment : result.segments()) {
            if (segment.index() < 0) {
                throw new IllegalArgumentException("Subtitle segment index must be non-negative.");
            }
            if (segment.startMs() < 0) {
                throw new IllegalArgumentException("Subtitle segment startMs must be non-negative.");
            }
            if (segment.endMs() <= segment.startMs()) {
                throw new IllegalArgumentException("Subtitle segment endMs must be greater than startMs.");
            }
            if (segment.text() == null || segment.text().isBlank()) {
                throw new IllegalArgumentException("Subtitle segment text must not be blank.");
            }
        }
    }

    private SubtitleSegmentRecord toRecord(
            String jobId,
            String language,
            TranslationSegmentBo segment,
            Instant createdAt
    ) {
        return new SubtitleSegmentRecord(
                UUID.randomUUID().toString(),
                jobId,
                language,
                segment.index(),
                segment.startMs(),
                segment.endMs(),
                segment.text().trim(),
                createdAt
        );
    }

    private SubtitleSegmentVo toVo(SubtitleSegmentRecord record) {
        return new SubtitleSegmentVo(
                record.language(),
                record.segmentIndex(),
                record.startMs(),
                record.endMs(),
                record.text()
        );
    }
}
