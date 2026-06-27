package com.linguaframe.job.repository;

import com.linguaframe.job.domain.bo.CreateQualityEvaluationCacheEntryCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class QualityEvaluationCacheRepositoryTests {

    @Autowired
    private QualityEvaluationCacheRepository repository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM quality_evaluation_cache_entries").update();
    }

    @Test
    void savesAndFindsQualityEvaluationCacheEntryByKey() {
        var command = new CreateQualityEvaluationCacheEntryCommand(
                "quality-evaluation-cache-key-1",
                "source-hash-1",
                "target-hash-1",
                "zh-CN",
                "OPENAI",
                "gpt-4o-mini",
                "quality-evaluation-v1",
                "{\"score\":92,\"verdict\":\"GOOD\",\"issues\":[],\"suggestedFixes\":[]}",
                "source-job-1"
        );

        var saved = repository.saveIfAbsent(command);

        assertThat(saved.id()).isNotBlank();
        assertThat(saved.cacheKey()).isEqualTo("quality-evaluation-cache-key-1");
        assertThat(saved.sourceHash()).isEqualTo("source-hash-1");
        assertThat(saved.targetHash()).isEqualTo("target-hash-1");
        assertThat(saved.language()).isEqualTo("zh-CN");
        assertThat(saved.provider()).isEqualTo("OPENAI");
        assertThat(saved.model()).isEqualTo("gpt-4o-mini");
        assertThat(saved.promptVersion()).isEqualTo("quality-evaluation-v1");
        assertThat(saved.responseJson()).contains("\"score\":92");
        assertThat(saved.sourceJobId()).isEqualTo("source-job-1");
        assertThat(saved.createdAt()).isNotNull();
        assertThat(repository.findByCacheKey("quality-evaluation-cache-key-1")).contains(saved);
        assertThat(repository.findByCacheKey("missing-cache-key")).isEmpty();
    }

    @Test
    void saveIfAbsentKeepsOriginalEntryForDuplicateCacheKey() {
        var original = new CreateQualityEvaluationCacheEntryCommand(
                "quality-evaluation-cache-key-duplicate",
                "source-hash-original",
                "target-hash-original",
                "zh-CN",
                "OPENAI",
                "gpt-4o-mini",
                "quality-evaluation-v1",
                "{\"score\":90,\"verdict\":\"ORIGINAL\",\"issues\":[],\"suggestedFixes\":[]}",
                "source-job-original"
        );
        var duplicate = new CreateQualityEvaluationCacheEntryCommand(
                "quality-evaluation-cache-key-duplicate",
                "source-hash-duplicate",
                "target-hash-duplicate",
                "zh-CN",
                "OPENAI",
                "gpt-4o-mini",
                "quality-evaluation-v1",
                "{\"score\":10,\"verdict\":\"DUPLICATE\",\"issues\":[],\"suggestedFixes\":[]}",
                "source-job-duplicate"
        );

        var first = repository.saveIfAbsent(original);
        var second = repository.saveIfAbsent(duplicate);

        assertThat(second).isEqualTo(first);
        assertThat(repository.findByCacheKey("quality-evaluation-cache-key-duplicate"))
                .get()
                .satisfies(entry -> {
                    assertThat(entry.sourceHash()).isEqualTo("source-hash-original");
                    assertThat(entry.targetHash()).isEqualTo("target-hash-original");
                    assertThat(entry.responseJson()).contains("\"ORIGINAL\"");
                    assertThat(entry.sourceJobId()).isEqualTo("source-job-original");
                });
    }
}
