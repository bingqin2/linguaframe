package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.bo.QualityEvaluationRequestBo;
import com.linguaframe.job.domain.bo.QualityEvaluationResultBo;
import com.linguaframe.job.domain.entity.QualityEvaluationRecord;
import com.linguaframe.job.domain.enums.QualityEvaluationStatus;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.repository.QualityEvaluationRepository;
import com.linguaframe.job.service.QualityEvaluationProvider;
import com.linguaframe.job.service.QualityEvaluationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class QualityEvaluationServiceImpl implements QualityEvaluationService {

    private static final int MAX_SAFE_ERROR_LENGTH = 512;

    private final QualityEvaluationRepository repository;
    private final QualityEvaluationProvider provider;
    private final Clock clock;

    @Autowired
    public QualityEvaluationServiceImpl(
            QualityEvaluationRepository repository,
            QualityEvaluationProvider provider
    ) {
        this(repository, provider, Clock.systemUTC());
    }

    public QualityEvaluationServiceImpl(
            QualityEvaluationRepository repository,
            QualityEvaluationProvider provider,
            Clock clock
    ) {
        this.repository = repository;
        this.provider = provider;
        this.clock = clock;
    }

    @Override
    public QualityEvaluationVo evaluateAndStore(
            String jobId,
            String language,
            List<TranscriptSegmentVo> sourceSegments,
            List<SubtitleSegmentVo> targetSegments
    ) {
        QualityEvaluationRecord record;
        try {
            QualityEvaluationResultBo result = provider.evaluate(new QualityEvaluationRequestBo(
                    jobId,
                    language,
                    sourceSegments,
                    targetSegments
            ));
            record = succeededRecord(jobId, language, result);
        } catch (RuntimeException ex) {
            record = failedRecord(jobId, language, safeError(ex));
        }
        repository.save(record);
        return toVo(record);
    }

    @Override
    public Optional<QualityEvaluationVo> latestForJob(String jobId) {
        return repository.findByJobId(jobId).stream()
                .reduce((first, second) -> second)
                .map(this::toVo);
    }

    private QualityEvaluationRecord succeededRecord(
            String jobId,
            String language,
            QualityEvaluationResultBo result
    ) {
        return new QualityEvaluationRecord(
                UUID.randomUUID().toString(),
                jobId,
                language,
                result.score(),
                result.verdict(),
                result.completeness(),
                result.readability(),
                result.timingPreservation(),
                result.naturalness(),
                result.issues() == null ? List.of() : result.issues(),
                result.suggestedFixes() == null ? List.of() : result.suggestedFixes(),
                QualityEvaluationStatus.SUCCEEDED,
                null,
                Instant.now(clock)
        );
    }

    private QualityEvaluationRecord failedRecord(String jobId, String language, String safeErrorSummary) {
        return new QualityEvaluationRecord(
                UUID.randomUUID().toString(),
                jobId,
                language,
                0,
                "FAILED",
                0,
                0,
                0,
                0,
                List.of(),
                List.of(),
                QualityEvaluationStatus.FAILED,
                safeErrorSummary,
                Instant.now(clock)
        );
    }

    private QualityEvaluationVo toVo(QualityEvaluationRecord record) {
        return new QualityEvaluationVo(
                record.id(),
                record.jobId(),
                record.language(),
                record.score(),
                record.verdict(),
                record.completeness(),
                record.readability(),
                record.timingPreservation(),
                record.naturalness(),
                record.issues(),
                record.suggestedFixes(),
                record.status(),
                record.safeErrorSummary(),
                record.createdAt()
        );
    }

    private String safeError(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }
        return message.length() <= MAX_SAFE_ERROR_LENGTH ? message : message.substring(0, MAX_SAFE_ERROR_LENGTH);
    }
}
