package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.QualityEvaluationRequestBo;
import com.linguaframe.job.domain.bo.QualityEvaluationResultBo;
import com.linguaframe.job.domain.entity.QualityEvaluationRecord;
import com.linguaframe.job.domain.enums.QualityEvaluationStatus;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.repository.QualityEvaluationRepository;
import com.linguaframe.job.service.impl.QualityEvaluationServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QualityEvaluationServiceTests {

    private final Instant now = Instant.parse("2026-06-27T11:00:00Z");

    @Test
    void evaluatesStoresAndReturnsSucceededEvaluation() {
        InMemoryQualityEvaluationRepository repository = new InMemoryQualityEvaluationRepository();
        RecordingQualityEvaluationProvider provider = new RecordingQualityEvaluationProvider(new QualityEvaluationResultBo(
                92,
                "GOOD",
                95,
                92,
                94,
                88,
                List.of("No blocking issue."),
                List.of("Review terminology.")
        ));
        QualityEvaluationService service = new QualityEvaluationServiceImpl(
                repository,
                provider,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        var result = service.evaluateAndStore("quality-service-job", "zh-CN", sourceSegments(), targetSegments());

        assertThat(provider.request.jobId()).isEqualTo("quality-service-job");
        assertThat(provider.request.language()).isEqualTo("zh-CN");
        assertThat(result.score()).isEqualTo(92);
        assertThat(result.status()).isEqualTo(QualityEvaluationStatus.SUCCEEDED);
        assertThat(result.safeErrorSummary()).isNull();
        assertThat(result.createdAt()).isEqualTo(now);
        assertThat(repository.records).hasSize(1);
        assertThat(service.latestForJob("quality-service-job")).contains(result);
    }

    @Test
    void storesFailedEvaluationWhenProviderThrows() {
        InMemoryQualityEvaluationRepository repository = new InMemoryQualityEvaluationRepository();
        QualityEvaluationProvider provider = request -> {
            throw new IllegalStateException("OpenAI quality evaluation request failed with status 500");
        };
        QualityEvaluationService service = new QualityEvaluationServiceImpl(
                repository,
                provider,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        var result = service.evaluateAndStore("quality-service-failed-job", "zh-CN", sourceSegments(), targetSegments());

        assertThat(result.score()).isZero();
        assertThat(result.verdict()).isEqualTo("FAILED");
        assertThat(result.status()).isEqualTo(QualityEvaluationStatus.FAILED);
        assertThat(result.safeErrorSummary()).isEqualTo("OpenAI quality evaluation request failed with status 500");
        assertThat(repository.records).hasSize(1);
    }

    @Test
    void storesCachedEvaluationWithoutCallingProvider() {
        InMemoryQualityEvaluationRepository repository = new InMemoryQualityEvaluationRepository();
        RecordingQualityEvaluationProvider provider = new RecordingQualityEvaluationProvider(new QualityEvaluationResultBo(
                1,
                "SHOULD_NOT_BE_USED",
                1,
                1,
                1,
                1,
                List.of(),
                List.of()
        ));
        QualityEvaluationService service = new QualityEvaluationServiceImpl(
                repository,
                provider,
                Clock.fixed(now, ZoneOffset.UTC)
        );
        QualityEvaluationResultBo cachedResult = new QualityEvaluationResultBo(
                88,
                "CACHED_GOOD",
                89,
                87,
                90,
                86,
                List.of("Cached issue"),
                List.of("Cached fix")
        );

        var result = service.storeCachedEvaluation("quality-service-cache-job", "zh-CN", cachedResult);

        assertThat(provider.request).isNull();
        assertThat(result.jobId()).isEqualTo("quality-service-cache-job");
        assertThat(result.score()).isEqualTo(88);
        assertThat(result.verdict()).isEqualTo("CACHED_GOOD");
        assertThat(result.status()).isEqualTo(QualityEvaluationStatus.SUCCEEDED);
        assertThat(result.safeErrorSummary()).isNull();
        assertThat(result.createdAt()).isEqualTo(now);
        assertThat(repository.records)
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.jobId()).isEqualTo("quality-service-cache-job");
                    assertThat(record.language()).isEqualTo("zh-CN");
                    assertThat(record.score()).isEqualTo(88);
                    assertThat(record.status()).isEqualTo(QualityEvaluationStatus.SUCCEEDED);
                });
    }

    private List<TranscriptSegmentVo> sourceSegments() {
        return List.of(new TranscriptSegmentVo(0, 0L, 1_000L, "Hello from LinguaFrame."));
    }

    private List<SubtitleSegmentVo> targetSegments() {
        return List.of(new SubtitleSegmentVo("zh-CN", 0, 0L, 1_000L, "LinguaFrame 向你问好。"));
    }

    private static class RecordingQualityEvaluationProvider implements QualityEvaluationProvider {

        private final QualityEvaluationResultBo result;
        private QualityEvaluationRequestBo request;

        private RecordingQualityEvaluationProvider(QualityEvaluationResultBo result) {
            this.result = result;
        }

        @Override
        public QualityEvaluationResultBo evaluate(QualityEvaluationRequestBo request) {
            this.request = request;
            return result;
        }
    }

    private static class InMemoryQualityEvaluationRepository extends QualityEvaluationRepository {

        private final List<QualityEvaluationRecord> records = new ArrayList<>();

        private InMemoryQualityEvaluationRepository() {
            super(null, null);
        }

        @Override
        public void save(QualityEvaluationRecord record) {
            records.add(record);
        }

        @Override
        public java.util.Optional<QualityEvaluationRecord> findLatestByJobIdAndLanguage(String jobId, String language) {
            return records.stream()
                    .filter(record -> record.jobId().equals(jobId) && record.language().equals(language))
                    .reduce((first, second) -> second);
        }

        @Override
        public List<QualityEvaluationRecord> findByJobId(String jobId) {
            return records.stream()
                    .filter(record -> record.jobId().equals(jobId))
                    .toList();
        }
    }
}
