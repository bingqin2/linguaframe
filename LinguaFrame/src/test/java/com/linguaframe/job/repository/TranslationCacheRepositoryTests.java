package com.linguaframe.job.repository;

import com.linguaframe.job.domain.bo.CreateTranslationCacheEntryCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TranslationCacheRepositoryTests {

    @Autowired
    private TranslationCacheRepository repository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM translation_cache_entries").update();
    }

    @Test
    void savesAndFindsTranslationCacheEntryByKey() {
        var command = new CreateTranslationCacheEntryCommand(
                "translation-cache-key-1",
                "source-hash-1",
                "zh-CN",
                "OPENAI",
                "gpt-test",
                "openai-subtitle-translation-v1",
                "{\"segments\":[{\"index\":0,\"startMs\":0,\"endMs\":1000,\"text\":\"你好\"}]}",
                "source-job-1"
        );

        var saved = repository.saveIfAbsent(command);

        assertThat(saved.id()).isNotBlank();
        assertThat(saved.cacheKey()).isEqualTo("translation-cache-key-1");
        assertThat(saved.sourceHash()).isEqualTo("source-hash-1");
        assertThat(saved.targetLanguage()).isEqualTo("zh-CN");
        assertThat(saved.provider()).isEqualTo("OPENAI");
        assertThat(saved.model()).isEqualTo("gpt-test");
        assertThat(saved.promptVersion()).isEqualTo("openai-subtitle-translation-v1");
        assertThat(saved.responseJson()).contains("\"你好\"");
        assertThat(saved.sourceJobId()).isEqualTo("source-job-1");
        assertThat(saved.createdAt()).isNotNull();
        assertThat(repository.findByCacheKey("translation-cache-key-1")).contains(saved);
        assertThat(repository.findByCacheKey("missing-cache-key")).isEmpty();
    }

    @Test
    void saveIfAbsentKeepsOriginalEntryForDuplicateCacheKey() {
        var original = new CreateTranslationCacheEntryCommand(
                "translation-cache-key-duplicate",
                "source-hash-original",
                "zh-CN",
                "OPENAI",
                "gpt-test",
                "openai-subtitle-translation-v1",
                "{\"segments\":[{\"index\":0,\"startMs\":0,\"endMs\":1000,\"text\":\"原始\"}]}",
                "source-job-original"
        );
        var duplicate = new CreateTranslationCacheEntryCommand(
                "translation-cache-key-duplicate",
                "source-hash-duplicate",
                "zh-CN",
                "OPENAI",
                "gpt-test",
                "openai-subtitle-translation-v1",
                "{\"segments\":[{\"index\":0,\"startMs\":0,\"endMs\":1000,\"text\":\"重复\"}]}",
                "source-job-duplicate"
        );

        var first = repository.saveIfAbsent(original);
        var second = repository.saveIfAbsent(duplicate);

        assertThat(second).isEqualTo(first);
        assertThat(repository.findByCacheKey("translation-cache-key-duplicate"))
                .get()
                .satisfies(entry -> {
                    assertThat(entry.sourceHash()).isEqualTo("source-hash-original");
                    assertThat(entry.responseJson()).contains("\"原始\"");
                    assertThat(entry.sourceJobId()).isEqualTo("source-job-original");
                });
    }
}
