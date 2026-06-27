package com.linguaframe.job.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.bo.CreateTtsCacheEntryCommand;
import com.linguaframe.job.domain.bo.TtsCacheLookupBo;
import com.linguaframe.job.domain.bo.TtsResultBo;
import com.linguaframe.job.domain.entity.TtsCacheEntryRecord;
import com.linguaframe.job.repository.TtsCacheRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TtsCacheServiceTests {

    private final RecordingTtsCacheRepository repository = new RecordingTtsCacheRepository();
    private final TtsCacheService service =
            new com.linguaframe.job.service.impl.TtsCacheServiceImpl(repository, new ObjectMapper());

    @Test
    void storesAndReadsTtsResult() {
        TtsCacheLookupBo lookup = lookup("tts-cache-key-service-1");
        TtsResultBo result = new TtsResultBo(new byte[] {9, 8, 7}, "dubbing-audio.mp3", "audio/mpeg");

        service.storeTts(lookup, "source-job-1", result);

        assertThat(repository.saved).hasSize(1);
        assertThat(repository.saved.getFirst().cacheKey()).isEqualTo("tts-cache-key-service-1");
        assertThat(repository.saved.getFirst().responseJson()).contains("dubbing-audio.mp3");
        assertThat(service.findCachedTts(lookup))
                .get()
                .satisfies(hit -> {
                    assertThat(hit.cacheKey()).isEqualTo("tts-cache-key-service-1");
                    assertThat(hit.sourceJobId()).isEqualTo("source-job-1");
                    assertThat(hit.result().audioContent()).containsExactly(9, 8, 7);
                    assertThat(hit.result().filename()).isEqualTo("dubbing-audio.mp3");
                    assertThat(hit.result().contentType()).isEqualTo("audio/mpeg");
                });
    }

    @Test
    void ignoresMalformedCachedJsonAsMiss() {
        TtsCacheLookupBo lookup = lookup("tts-cache-key-malformed");
        repository.entries.add(new TtsCacheEntryRecord(
                "entry-malformed",
                lookup.cacheKey(),
                lookup.textHash(),
                lookup.language(),
                lookup.provider(),
                lookup.model(),
                lookup.voice(),
                "{not-json",
                "source-job-malformed",
                Instant.parse("2026-06-27T01:00:00Z")
        ));

        assertThat(service.findCachedTts(lookup)).isEmpty();
    }

    @Test
    void duplicateStoresKeepFirstSourceJob() {
        TtsCacheLookupBo lookup = lookup("tts-cache-key-duplicate-service");
        TtsResultBo first = new TtsResultBo(new byte[] {1, 2, 3}, "first.mp3", "audio/mpeg");
        TtsResultBo second = new TtsResultBo(new byte[] {4, 5, 6}, "second.mp3", "audio/mpeg");

        service.storeTts(lookup, "source-job-first", first);
        service.storeTts(lookup, "source-job-second", second);

        assertThat(service.findCachedTts(lookup))
                .get()
                .satisfies(hit -> {
                    assertThat(hit.sourceJobId()).isEqualTo("source-job-first");
                    assertThat(hit.result().audioContent()).containsExactly(1, 2, 3);
                    assertThat(hit.result().filename()).isEqualTo("first.mp3");
                });
    }

    @Test
    void rejectsEmptyTtsAudio() {
        assertThatThrownBy(() -> service.storeTts(
                lookup("tts-cache-key-empty"),
                "source-job-empty",
                new TtsResultBo(new byte[0], "empty.mp3", "audio/mpeg")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audio");
    }

    private TtsCacheLookupBo lookup(String cacheKey) {
        return new TtsCacheLookupBo(
                cacheKey,
                "text-hash-service",
                "zh-CN",
                "OPENAI",
                "gpt-4o-mini-tts",
                "alloy"
        );
    }

    private static class RecordingTtsCacheRepository extends TtsCacheRepository {

        private final List<TtsCacheEntryRecord> entries = new ArrayList<>();
        private final List<CreateTtsCacheEntryCommand> saved = new ArrayList<>();

        private RecordingTtsCacheRepository() {
            super(null);
        }

        @Override
        public Optional<TtsCacheEntryRecord> findByCacheKey(String cacheKey) {
            return entries.stream()
                    .filter(entry -> entry.cacheKey().equals(cacheKey))
                    .findFirst();
        }

        @Override
        public TtsCacheEntryRecord saveIfAbsent(CreateTtsCacheEntryCommand command) {
            saved.add(command);
            Optional<TtsCacheEntryRecord> existing = findByCacheKey(command.cacheKey());
            if (existing.isPresent()) {
                return existing.get();
            }
            TtsCacheEntryRecord record = new TtsCacheEntryRecord(
                    "entry-" + entries.size(),
                    command.cacheKey(),
                    command.textHash(),
                    command.language(),
                    command.provider(),
                    command.model(),
                    command.voice(),
                    command.responseJson(),
                    command.sourceJobId(),
                    Instant.parse("2026-06-27T01:00:00Z").plusSeconds(entries.size())
            );
            entries.add(record);
            return record;
        }
    }
}
