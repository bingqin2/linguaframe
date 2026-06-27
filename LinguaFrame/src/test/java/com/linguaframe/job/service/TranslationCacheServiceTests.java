package com.linguaframe.job.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.bo.CreateTranslationCacheEntryCommand;
import com.linguaframe.job.domain.bo.TranslationCacheLookupBo;
import com.linguaframe.job.domain.bo.TranslationResultBo;
import com.linguaframe.job.domain.bo.TranslationSegmentBo;
import com.linguaframe.job.domain.entity.TranslationCacheEntryRecord;
import com.linguaframe.job.repository.TranslationCacheRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranslationCacheServiceTests {

    private final RecordingTranslationCacheRepository repository = new RecordingTranslationCacheRepository();
    private final TranslationCacheService service =
            new com.linguaframe.job.service.impl.TranslationCacheServiceImpl(repository, new ObjectMapper());

    @Test
    void storesAndReadsTranslationResult() {
        TranslationCacheLookupBo lookup = lookup("translation-cache-key-service-1");
        TranslationResultBo result = new TranslationResultBo(List.of(
                new TranslationSegmentBo(0, 0L, 1_000L, "你好"),
                new TranslationSegmentBo(1, 1_000L, 2_000L, "世界")
        ));

        service.storeTranslation(lookup, "source-job-1", result);

        assertThat(repository.saved).hasSize(1);
        assertThat(repository.saved.getFirst().cacheKey()).isEqualTo("translation-cache-key-service-1");
        assertThat(repository.saved.getFirst().responseJson()).contains("\"你好\"");
        assertThat(service.findCachedTranslation(lookup))
                .get()
                .satisfies(hit -> {
                    assertThat(hit.cacheKey()).isEqualTo("translation-cache-key-service-1");
                    assertThat(hit.sourceJobId()).isEqualTo("source-job-1");
                    assertThat(hit.result()).isEqualTo(result);
                });
    }

    @Test
    void ignoresMalformedCachedJsonAsMiss() {
        TranslationCacheLookupBo lookup = lookup("translation-cache-key-malformed");
        repository.entries.add(new TranslationCacheEntryRecord(
                "entry-malformed",
                lookup.cacheKey(),
                lookup.sourceHash(),
                lookup.targetLanguage(),
                lookup.provider(),
                lookup.model(),
                lookup.promptVersion(),
                "{not-json",
                "source-job-malformed",
                Instant.parse("2026-06-27T01:00:00Z")
        ));

        assertThat(service.findCachedTranslation(lookup)).isEmpty();
    }

    @Test
    void duplicateStoresKeepFirstSourceJob() {
        TranslationCacheLookupBo lookup = lookup("translation-cache-key-duplicate-service");
        TranslationResultBo first = new TranslationResultBo(List.of(new TranslationSegmentBo(0, 0L, 1_000L, "第一")));
        TranslationResultBo second = new TranslationResultBo(List.of(new TranslationSegmentBo(0, 0L, 1_000L, "第二")));

        service.storeTranslation(lookup, "source-job-first", first);
        service.storeTranslation(lookup, "source-job-second", second);

        assertThat(service.findCachedTranslation(lookup))
                .get()
                .satisfies(hit -> {
                    assertThat(hit.sourceJobId()).isEqualTo("source-job-first");
                    assertThat(hit.result()).isEqualTo(first);
                });
    }

    @Test
    void rejectsEmptyTranslationResults() {
        assertThatThrownBy(() -> service.storeTranslation(
                lookup("translation-cache-key-empty"),
                "source-job-empty",
                new TranslationResultBo(List.of())
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("segments");
    }

    private TranslationCacheLookupBo lookup(String cacheKey) {
        return new TranslationCacheLookupBo(
                cacheKey,
                "source-hash-service",
                "zh-CN",
                "OPENAI",
                "gpt-test",
                "openai-subtitle-translation-v1"
        );
    }

    private static class RecordingTranslationCacheRepository extends TranslationCacheRepository {

        private final List<TranslationCacheEntryRecord> entries = new ArrayList<>();
        private final List<CreateTranslationCacheEntryCommand> saved = new ArrayList<>();

        private RecordingTranslationCacheRepository() {
            super(null);
        }

        @Override
        public Optional<TranslationCacheEntryRecord> findByCacheKey(String cacheKey) {
            return entries.stream()
                    .filter(entry -> entry.cacheKey().equals(cacheKey))
                    .findFirst();
        }

        @Override
        public TranslationCacheEntryRecord saveIfAbsent(CreateTranslationCacheEntryCommand command) {
            saved.add(command);
            Optional<TranslationCacheEntryRecord> existing = findByCacheKey(command.cacheKey());
            if (existing.isPresent()) {
                return existing.get();
            }
            TranslationCacheEntryRecord record = new TranslationCacheEntryRecord(
                    "entry-" + entries.size(),
                    command.cacheKey(),
                    command.sourceHash(),
                    command.targetLanguage(),
                    command.provider(),
                    command.model(),
                    command.promptVersion(),
                    command.responseJson(),
                    command.sourceJobId(),
                    Instant.parse("2026-06-27T01:00:00Z").plusSeconds(entries.size())
            );
            entries.add(record);
            return record;
        }
    }
}
