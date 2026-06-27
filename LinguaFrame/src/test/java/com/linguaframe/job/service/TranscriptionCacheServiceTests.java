package com.linguaframe.job.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.bo.CreateTranscriptionCacheEntryCommand;
import com.linguaframe.job.domain.bo.TranscriptionCacheLookupBo;
import com.linguaframe.job.domain.bo.TranscriptionResultBo;
import com.linguaframe.job.domain.bo.TranscriptionSegmentBo;
import com.linguaframe.job.domain.entity.TranscriptionCacheEntryRecord;
import com.linguaframe.job.repository.TranscriptionCacheRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranscriptionCacheServiceTests {

    private final RecordingTranscriptionCacheRepository repository = new RecordingTranscriptionCacheRepository();
    private final TranscriptionCacheService service =
            new com.linguaframe.job.service.impl.TranscriptionCacheServiceImpl(repository, new ObjectMapper());

    @Test
    void storesAndReadsTranscriptionResult() {
        TranscriptionCacheLookupBo lookup = lookup("transcription-cache-key-service-1");
        TranscriptionResultBo result = new TranscriptionResultBo(List.of(
                new TranscriptionSegmentBo(0, 0L, 1_000L, "Hello"),
                new TranscriptionSegmentBo(1, 1_000L, 2_000L, "World")
        ));

        service.storeTranscription(lookup, "source-job-1", result);

        assertThat(repository.saved).hasSize(1);
        assertThat(repository.saved.getFirst().cacheKey()).isEqualTo("transcription-cache-key-service-1");
        assertThat(repository.saved.getFirst().responseJson()).contains("\"Hello\"");
        assertThat(service.findCachedTranscription(lookup))
                .get()
                .satisfies(hit -> {
                    assertThat(hit.cacheKey()).isEqualTo("transcription-cache-key-service-1");
                    assertThat(hit.sourceJobId()).isEqualTo("source-job-1");
                    assertThat(hit.result()).isEqualTo(result);
                });
    }

    @Test
    void ignoresMalformedCachedJsonAsMiss() {
        TranscriptionCacheLookupBo lookup = lookup("transcription-cache-key-malformed");
        repository.entries.add(new TranscriptionCacheEntryRecord(
                "entry-malformed",
                lookup.cacheKey(),
                lookup.audioHash(),
                lookup.provider(),
                lookup.model(),
                lookup.promptVersion(),
                "{not-json",
                "source-job-malformed",
                Instant.parse("2026-06-27T01:00:00Z")
        ));

        assertThat(service.findCachedTranscription(lookup)).isEmpty();
    }

    @Test
    void duplicateStoresKeepFirstSourceJob() {
        TranscriptionCacheLookupBo lookup = lookup("transcription-cache-key-duplicate-service");
        TranscriptionResultBo first = new TranscriptionResultBo(List.of(new TranscriptionSegmentBo(0, 0L, 1_000L, "First")));
        TranscriptionResultBo second = new TranscriptionResultBo(List.of(new TranscriptionSegmentBo(0, 0L, 1_000L, "Second")));

        service.storeTranscription(lookup, "source-job-first", first);
        service.storeTranscription(lookup, "source-job-second", second);

        assertThat(service.findCachedTranscription(lookup))
                .get()
                .satisfies(hit -> {
                    assertThat(hit.sourceJobId()).isEqualTo("source-job-first");
                    assertThat(hit.result()).isEqualTo(first);
                });
    }

    @Test
    void rejectsEmptyTranscriptionResults() {
        assertThatThrownBy(() -> service.storeTranscription(
                lookup("transcription-cache-key-empty"),
                "source-job-empty",
                new TranscriptionResultBo(List.of())
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("segments");
    }

    private TranscriptionCacheLookupBo lookup(String cacheKey) {
        return new TranscriptionCacheLookupBo(
                cacheKey,
                "audio-hash-service",
                "OPENAI",
                "whisper-1",
                "openai-audio-transcriptions-v1"
        );
    }

    private static class RecordingTranscriptionCacheRepository extends TranscriptionCacheRepository {

        private final List<TranscriptionCacheEntryRecord> entries = new ArrayList<>();
        private final List<CreateTranscriptionCacheEntryCommand> saved = new ArrayList<>();

        private RecordingTranscriptionCacheRepository() {
            super(null);
        }

        @Override
        public Optional<TranscriptionCacheEntryRecord> findByCacheKey(String cacheKey) {
            return entries.stream()
                    .filter(entry -> entry.cacheKey().equals(cacheKey))
                    .findFirst();
        }

        @Override
        public TranscriptionCacheEntryRecord saveIfAbsent(CreateTranscriptionCacheEntryCommand command) {
            saved.add(command);
            Optional<TranscriptionCacheEntryRecord> existing = findByCacheKey(command.cacheKey());
            if (existing.isPresent()) {
                return existing.get();
            }
            TranscriptionCacheEntryRecord record = new TranscriptionCacheEntryRecord(
                    "entry-" + entries.size(),
                    command.cacheKey(),
                    command.audioHash(),
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
