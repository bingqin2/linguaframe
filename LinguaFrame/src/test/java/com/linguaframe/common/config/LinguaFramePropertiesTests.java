package com.linguaframe.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LinguaFramePropertiesTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesTestConfiguration.class);

    @Autowired
    private LinguaFrameProperties properties;

    @Test
    void bindsDefaultRuntimeProperties() {
        assertThat(properties.getMedia().getMaxFileSizeMb()).isEqualTo(100);
        assertThat(properties.getMedia().getMaxDurationSeconds()).isEqualTo(120);
        assertThat(properties.getWorker().getMaxRetries()).isEqualTo(2);
        assertThat(properties.getWorker().getStageTimeoutSeconds()).isEqualTo(600);
        assertThat(properties.getCost().isEnabled()).isTrue();
    }

    @Test
    void rejectsInvalidRuntimeProperties() {
        contextRunner
                .withPropertyValues(
                        "linguaframe.media.max-file-size-mb=0",
                        "linguaframe.worker.max-retries=-1",
                        "linguaframe.worker.stage-timeout-seconds=0"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("linguaframe");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LinguaFrameProperties.class)
    static class PropertiesTestConfiguration {
    }
}
