package com.linguaframe.job.repository;

import com.linguaframe.job.domain.bo.CreateTranscriptionCacheEntryCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TranscriptionCacheRepositoryTests {

    @Autowired
    private TranscriptionCacheRepository repository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM transcription_cache_entries").update();
    }

    @Test
    void savesAndFindsTranscriptionCacheEntryByKey() {
        var command = new CreateTranscriptionCacheEntryCommand(
                "transcription-cache-key-1",
                "audio-hash-1",
                "OPENAI",
                "whisper-1",
                "openai-audio-transcriptions-v1",
                "{\"segments\":[{\"index\":0,\"startMs\":0,\"endMs\":1000,\"text\":\"Hello\"}]}",
                "source-job-1"
        );

        var saved = repository.saveIfAbsent(command);

        assertThat(saved.id()).isNotBlank();
        assertThat(saved.cacheKey()).isEqualTo("transcription-cache-key-1");
        assertThat(saved.audioHash()).isEqualTo("audio-hash-1");
        assertThat(saved.provider()).isEqualTo("OPENAI");
        assertThat(saved.model()).isEqualTo("whisper-1");
        assertThat(saved.promptVersion()).isEqualTo("openai-audio-transcriptions-v1");
        assertThat(saved.responseJson()).contains("\"Hello\"");
        assertThat(saved.sourceJobId()).isEqualTo("source-job-1");
        assertThat(saved.createdAt()).isNotNull();
        assertThat(repository.findByCacheKey("transcription-cache-key-1")).contains(saved);
        assertThat(repository.findByCacheKey("missing-cache-key")).isEmpty();
    }

    @Test
    void saveIfAbsentKeepsOriginalEntryForDuplicateCacheKey() {
        var original = new CreateTranscriptionCacheEntryCommand(
                "transcription-cache-key-duplicate",
                "audio-hash-original",
                "OPENAI",
                "whisper-1",
                "openai-audio-transcriptions-v1",
                "{\"segments\":[{\"index\":0,\"startMs\":0,\"endMs\":1000,\"text\":\"Original\"}]}",
                "source-job-original"
        );
        var duplicate = new CreateTranscriptionCacheEntryCommand(
                "transcription-cache-key-duplicate",
                "audio-hash-duplicate",
                "OPENAI",
                "whisper-1",
                "openai-audio-transcriptions-v1",
                "{\"segments\":[{\"index\":0,\"startMs\":0,\"endMs\":1000,\"text\":\"Duplicate\"}]}",
                "source-job-duplicate"
        );

        var first = repository.saveIfAbsent(original);
        var second = repository.saveIfAbsent(duplicate);

        assertThat(second).isEqualTo(first);
        assertThat(repository.findByCacheKey("transcription-cache-key-duplicate"))
                .get()
                .satisfies(entry -> {
                    assertThat(entry.audioHash()).isEqualTo("audio-hash-original");
                    assertThat(entry.responseJson()).contains("\"Original\"");
                    assertThat(entry.sourceJobId()).isEqualTo("source-job-original");
                });
    }
}
