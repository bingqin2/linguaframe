package com.linguaframe.job.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.bo.CreateQualityEvaluationCacheEntryCommand;
import com.linguaframe.job.domain.bo.QualityEvaluationCacheLookupBo;
import com.linguaframe.job.domain.bo.QualityEvaluationResultBo;
import com.linguaframe.job.domain.entity.QualityEvaluationCacheEntryRecord;
import com.linguaframe.job.repository.QualityEvaluationCacheRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class QualityEvaluationCacheServiceTests {

    private final RecordingQualityEvaluationCacheRepository repository = new RecordingQualityEvaluationCacheRepository();
    private final QualityEvaluationCacheService service =
            new com.linguaframe.job.service.impl.QualityEvaluationCacheServiceImpl(repository, new ObjectMapper());

    @Test
    void storesAndReadsQualityEvaluationResult() {
        QualityEvaluationCacheLookupBo lookup = lookup("quality-evaluation-cache-key-service-1");
        QualityEvaluationResultBo result = new QualityEvaluationResultBo(
                92,
                "GOOD",
                90,
                91,
                93,
                94,
                List.of("Issue"),
                List.of("Fix")
        );

        service.storeEvaluation(lookup, "source-job-1", result);

        assertThat(repository.saved).hasSize(1);
        assertThat(repository.saved.getFirst().cacheKey()).isEqualTo("quality-evaluation-cache-key-service-1");
        assertThat(repository.saved.getFirst().responseJson()).contains("\"score\":92");
        assertThat(service.findCachedEvaluation(lookup))
                .get()
                .satisfies(hit -> {
                    assertThat(hit.cacheKey()).isEqualTo("quality-evaluation-cache-key-service-1");
                    assertThat(hit.sourceJobId()).isEqualTo("source-job-1");
                    assertThat(hit.result()).isEqualTo(result);
                });
    }

    @Test
    void ignoresMalformedCachedJsonAsMiss() {
        QualityEvaluationCacheLookupBo lookup = lookup("quality-evaluation-cache-key-malformed");
        repository.entries.add(new QualityEvaluationCacheEntryRecord(
                "entry-malformed",
                lookup.cacheKey(),
                lookup.sourceHash(),
                lookup.targetHash(),
                lookup.language(),
                lookup.provider(),
                lookup.model(),
                lookup.promptVersion(),
                "{not-json",
                "source-job-malformed",
                Instant.parse("2026-06-27T06:00:00Z")
        ));

        assertThat(service.findCachedEvaluation(lookup)).isEmpty();
    }

    @Test
    void duplicateStoresKeepFirstSourceJob() {
        QualityEvaluationCacheLookupBo lookup = lookup("quality-evaluation-cache-key-duplicate-service");
        QualityEvaluationResultBo first = new QualityEvaluationResultBo(90, "FIRST", 90, 90, 90, 90, List.of(), List.of());
        QualityEvaluationResultBo second = new QualityEvaluationResultBo(10, "SECOND", 10, 10, 10, 10, List.of(), List.of());

        service.storeEvaluation(lookup, "source-job-first", first);
        service.storeEvaluation(lookup, "source-job-second", second);

        assertThat(service.findCachedEvaluation(lookup))
                .get()
                .satisfies(hit -> {
                    assertThat(hit.sourceJobId()).isEqualTo("source-job-first");
                    assertThat(hit.result()).isEqualTo(first);
                });
    }

    private QualityEvaluationCacheLookupBo lookup(String cacheKey) {
        return new QualityEvaluationCacheLookupBo(
                cacheKey,
                "source-hash-service",
                "target-hash-service",
                "zh-CN",
                "OPENAI",
                "gpt-4o-mini",
                "quality-evaluation-v1"
        );
    }

    private static class RecordingQualityEvaluationCacheRepository extends QualityEvaluationCacheRepository {

        private final List<QualityEvaluationCacheEntryRecord> entries = new ArrayList<>();
        private final List<CreateQualityEvaluationCacheEntryCommand> saved = new ArrayList<>();

        private RecordingQualityEvaluationCacheRepository() {
            super(null);
        }

        @Override
        public Optional<QualityEvaluationCacheEntryRecord> findByCacheKey(String cacheKey) {
            return entries.stream()
                    .filter(entry -> entry.cacheKey().equals(cacheKey))
                    .findFirst();
        }

        @Override
        public QualityEvaluationCacheEntryRecord saveIfAbsent(CreateQualityEvaluationCacheEntryCommand command) {
            saved.add(command);
            Optional<QualityEvaluationCacheEntryRecord> existing = findByCacheKey(command.cacheKey());
            if (existing.isPresent()) {
                return existing.get();
            }
            QualityEvaluationCacheEntryRecord record = new QualityEvaluationCacheEntryRecord(
                    "entry-" + entries.size(),
                    command.cacheKey(),
                    command.sourceHash(),
                    command.targetHash(),
                    command.language(),
                    command.provider(),
                    command.model(),
                    command.promptVersion(),
                    command.responseJson(),
                    command.sourceJobId(),
                    Instant.parse("2026-06-27T06:00:00Z").plusSeconds(entries.size())
            );
            entries.add(record);
            return record;
        }
    }
}
