package com.linguaframe.job.repository;

import com.linguaframe.job.domain.bo.CreateTtsCacheEntryCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TtsCacheRepositoryTests {

    @Autowired
    private TtsCacheRepository repository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM tts_cache_entries").update();
    }

    @Test
    void savesAndFindsTtsCacheEntryByKey() {
        var command = new CreateTtsCacheEntryCommand(
                "tts-cache-key-1",
                "text-hash-1",
                "zh-CN",
                "OPENAI",
                "gpt-4o-mini-tts",
                "alloy",
                "{\"audioBase64\":\"CQgH\",\"filename\":\"dubbing-audio.mp3\",\"contentType\":\"audio/mpeg\"}",
                "source-job-1"
        );

        var saved = repository.saveIfAbsent(command);

        assertThat(saved.id()).isNotBlank();
        assertThat(saved.cacheKey()).isEqualTo("tts-cache-key-1");
        assertThat(saved.textHash()).isEqualTo("text-hash-1");
        assertThat(saved.language()).isEqualTo("zh-CN");
        assertThat(saved.provider()).isEqualTo("OPENAI");
        assertThat(saved.model()).isEqualTo("gpt-4o-mini-tts");
        assertThat(saved.voice()).isEqualTo("alloy");
        assertThat(saved.responseJson()).contains("dubbing-audio.mp3");
        assertThat(saved.sourceJobId()).isEqualTo("source-job-1");
        assertThat(saved.createdAt()).isNotNull();
        assertThat(repository.findByCacheKey("tts-cache-key-1")).contains(saved);
        assertThat(repository.findByCacheKey("missing-cache-key")).isEmpty();
    }

    @Test
    void saveIfAbsentKeepsOriginalEntryForDuplicateCacheKey() {
        var original = new CreateTtsCacheEntryCommand(
                "tts-cache-key-duplicate",
                "text-hash-original",
                "zh-CN",
                "OPENAI",
                "gpt-4o-mini-tts",
                "alloy",
                "{\"audioBase64\":\"AQID\",\"filename\":\"first.mp3\",\"contentType\":\"audio/mpeg\"}",
                "source-job-original"
        );
        var duplicate = new CreateTtsCacheEntryCommand(
                "tts-cache-key-duplicate",
                "text-hash-duplicate",
                "zh-CN",
                "OPENAI",
                "gpt-4o-mini-tts",
                "alloy",
                "{\"audioBase64\":\"BAUG\",\"filename\":\"second.mp3\",\"contentType\":\"audio/mpeg\"}",
                "source-job-duplicate"
        );

        var first = repository.saveIfAbsent(original);
        var second = repository.saveIfAbsent(duplicate);

        assertThat(second).isEqualTo(first);
        assertThat(repository.findByCacheKey("tts-cache-key-duplicate"))
                .get()
                .satisfies(entry -> {
                    assertThat(entry.textHash()).isEqualTo("text-hash-original");
                    assertThat(entry.responseJson()).contains("first.mp3");
                    assertThat(entry.sourceJobId()).isEqualTo("source-job-original");
                });
    }
}
