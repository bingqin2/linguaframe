package com.linguaframe.operator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.operator.domain.vo.DemoSampleMediaCatalogVo;
import com.linguaframe.operator.service.impl.DemoSampleMediaCatalogServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DemoSampleMediaCatalogServiceTests {

    @TempDir
    private Path tempDir;

    @Test
    void reportsReadyWhenConfiguredTearsSampleExistsWithoutLeakingFullPath() throws Exception {
        Path sample = tempDir.resolve("tos_casting-720p.mp4");
        Files.writeString(sample, "demo video bytes");
        LinguaFrameProperties properties = new LinguaFrameProperties();

        DemoSampleMediaCatalogVo catalog = service(properties, Map.of(
                "LINGUAFRAME_TEARS_SAMPLE_PATH", sample.toString()
        )).catalog();

        assertThat(catalog.overallStatus()).isEqualTo("READY");
        assertThat(catalog.recommendedSampleId()).isEqualTo("tears-of-steel-casting");
        assertThat(catalog.uploadDurationLimitSeconds()).isEqualTo(300);
        assertThat(catalog.configuredPaths())
                .filteredOn(path -> path.envVar().equals("LINGUAFRAME_TEARS_SAMPLE_PATH"))
                .singleElement()
                .satisfies(path -> {
                    assertThat(path.status()).isEqualTo("CONFIGURED");
                    assertThat(path.filename()).isEqualTo("tos_casting-720p.mp4");
                    assertThat(path.extension()).isEqualTo("mp4");
                    assertThat(path.sizeBytes()).isEqualTo(Files.size(sample));
                    assertThat(path.fullPathExposed()).isFalse();
                });
        assertThat(catalog.items())
                .extracting("id")
                .contains(
                        "tears-of-steel-casting",
                        "big-buck-bunny-w3schools",
                        "sintel",
                        "nasa-library",
                        "internet-archive-movies"
                );
        assertThat(toJson(catalog))
                .contains("Blender Studio")
                .contains("scripts/demo/docker-e2e-tears-of-steel-full.sh")
                .doesNotContain(sample.toString())
                .doesNotContain(tempDir.toString());
    }

    @Test
    void reportsAttentionWhenOnlyRemoteReferencesAreAvailable() throws Exception {
        DemoSampleMediaCatalogVo catalog = service(new LinguaFrameProperties(), Map.of()).catalog();

        assertThat(catalog.overallStatus()).isEqualTo("ATTENTION");
        assertThat(catalog.configuredPaths())
                .extracting("status")
                .containsOnly("UNCONFIGURED");
        assertThat(catalog.notesMarkdown())
                .contains("remote public references")
                .contains("does not download media");
    }

    @Test
    void reportsMissingConfiguredPathWithoutLeakingDirectory() throws Exception {
        Path missing = tempDir.resolve("missing-sample.mp4");

        DemoSampleMediaCatalogVo catalog = service(new LinguaFrameProperties(), Map.of(
                "LINGUAFRAME_DEMO_SAMPLE_PATH", missing.toString()
        )).catalog();

        assertThat(catalog.overallStatus()).isEqualTo("ATTENTION");
        assertThat(catalog.configuredPaths())
                .filteredOn(path -> path.envVar().equals("LINGUAFRAME_DEMO_SAMPLE_PATH"))
                .singleElement()
                .satisfies(path -> {
                    assertThat(path.status()).isEqualTo("MISSING");
                    assertThat(path.filename()).isEqualTo("missing-sample.mp4");
                    assertThat(path.fullPathExposed()).isFalse();
                });
        assertThat(toJson(catalog))
                .doesNotContain(missing.toString())
                .doesNotContain(tempDir.toString());
    }

    @Test
    void includesDurationLimitGuidanceForLongerSamples() {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getMedia().setMaxDurationSeconds(120);

        DemoSampleMediaCatalogVo catalog = service(properties, Map.of()).catalog();

        assertThat(catalog.uploadDurationLimitSeconds()).isEqualTo(120);
        assertThat(catalog.items())
                .filteredOn(item -> item.id().equals("sintel"))
                .singleElement()
                .satisfies(item -> assertThat(item.durationGuidance()).contains("120 seconds"));
    }

    @Test
    void keepsCatalogMetadataOnly() throws Exception {
        DemoSampleMediaCatalogVo catalog = service(new LinguaFrameProperties(), Map.of(
                "LINGUAFRAME_TEARS_SAMPLE_PATH", "/Users/wangbingqin/Downloads/tos_casting-720p.mp4"
        )).catalog();

        assertThat(toJson(catalog))
                .doesNotContain("/Users/")
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("private-demo-token")
                .doesNotContain("source-videos/")
                .doesNotContain("job-artifacts/")
                .doesNotContain("provider payload")
                .doesNotContain("raw transcript text")
                .doesNotContain("raw subtitle text");
    }

    private DemoSampleMediaCatalogService service(LinguaFrameProperties properties, Map<String, String> environment) {
        return new DemoSampleMediaCatalogServiceImpl(properties, environment);
    }

    private String toJson(DemoSampleMediaCatalogVo catalog) throws Exception {
        return new ObjectMapper().findAndRegisterModules().writeValueAsString(catalog);
    }
}
