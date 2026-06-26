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
        assertThat(properties.getWorker().isDispatchEnabled()).isFalse();
        assertThat(properties.getWorker().getDispatchBatchSize()).isEqualTo(10);
        assertThat(properties.getWorker().getDispatchIntervalMs()).isEqualTo(5000L);
        assertThat(properties.getWorker().isExecutionEnabled()).isFalse();
        assertThat(properties.getWorker().getSmokeStageDurationMs()).isEqualTo(0L);
        assertThat(properties.getWorker().isSmokeStageFailureEnabled()).isFalse();
        assertThat(properties.getCost().isEnabled()).isTrue();
        assertThat(properties.getDatabase().getHost()).isEqualTo("localhost");
        assertThat(properties.getDatabase().getPort()).isEqualTo(3306);
        assertThat(properties.getDatabase().getName()).isEqualTo("linguaframe");
        assertThat(properties.getDatabase().getUsername()).isEqualTo("linguaframe");
        assertThat(properties.getDatabase().getPassword()).isEqualTo("linguaframe_dev_password");
        assertThat(properties.getRedis().getHost()).isEqualTo("localhost");
        assertThat(properties.getRedis().getPort()).isEqualTo(6379);
        assertThat(properties.getRabbitmq().getHost()).isEqualTo("localhost");
        assertThat(properties.getRabbitmq().getPort()).isEqualTo(5672);
        assertThat(properties.getRabbitmq().getUsername()).isEqualTo("linguaframe");
        assertThat(properties.getRabbitmq().getPassword()).isEqualTo("linguaframe_dev_password");
        assertThat(properties.getRabbitmq().getJobExchange()).isEqualTo("linguaframe.jobs");
        assertThat(properties.getRabbitmq().getJobQueue()).isEqualTo("linguaframe.localization.jobs");
        assertThat(properties.getRabbitmq().getJobRoutingKey()).isEqualTo("localization.queued");
        assertThat(properties.getStorage().getEndpoint()).isEqualTo("http://localhost:9000");
        assertThat(properties.getStorage().getBucket()).isEqualTo("linguaframe-artifacts");
        assertThat(properties.getStorage().getAccessKey()).isEqualTo("linguaframe");
        assertThat(properties.getStorage().getSecretKey()).isEqualTo("linguaframe_minio_password");
        assertThat(properties.getFfmpeg().getBinaryPath()).isEqualTo("ffmpeg");
        assertThat(properties.getFfmpeg().isAudioEnabled()).isFalse();
        assertThat(properties.getFfmpeg().getAudioTimeoutSeconds()).isEqualTo(120);
        assertThat(properties.getFfmpeg().getWorkDir()).isEqualTo("/tmp/linguaframe-media");
    }

    @Test
    void bindsDemoWorkerSmokeFailureToggle() {
        contextRunner
                .withPropertyValues("linguaframe.worker.smoke-stage-failure-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LinguaFrameProperties boundProperties = context.getBean(LinguaFrameProperties.class);
                    assertThat(boundProperties.getWorker().isSmokeStageFailureEnabled()).isTrue();
                });
    }

    @Test
    void bindsFfmpegRuntimeProperties() {
        contextRunner
                .withPropertyValues(
                        "linguaframe.ffmpeg.binary-path=/usr/bin/ffmpeg",
                        "linguaframe.ffmpeg.audio-enabled=true",
                        "linguaframe.ffmpeg.audio-timeout-seconds=30",
                        "linguaframe.ffmpeg.work-dir=/tmp/custom-media"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LinguaFrameProperties boundProperties = context.getBean(LinguaFrameProperties.class);
                    assertThat(boundProperties.getFfmpeg().getBinaryPath()).isEqualTo("/usr/bin/ffmpeg");
                    assertThat(boundProperties.getFfmpeg().isAudioEnabled()).isTrue();
                    assertThat(boundProperties.getFfmpeg().getAudioTimeoutSeconds()).isEqualTo(30);
                    assertThat(boundProperties.getFfmpeg().getWorkDir()).isEqualTo("/tmp/custom-media");
                });
    }

    @Test
    void rejectsInvalidRuntimeProperties() {
        contextRunner
                .withPropertyValues(
                        "linguaframe.media.max-file-size-mb=0",
                        "linguaframe.worker.max-retries=-1",
                        "linguaframe.worker.stage-timeout-seconds=0",
                        "linguaframe.worker.dispatch-batch-size=0",
                        "linguaframe.worker.dispatch-interval-ms=0",
                        "linguaframe.worker.smoke-stage-duration-ms=-1",
                        "linguaframe.ffmpeg.audio-timeout-seconds=0"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("linguaframe");
                });
    }

    @Test
    void rejectsInvalidRuntimeDependencyProperties() {
        contextRunner
                .withPropertyValues(
                        "linguaframe.database.host=",
                        "linguaframe.database.port=0",
                        "linguaframe.database.name=",
                        "linguaframe.database.username=",
                        "linguaframe.database.password=",
                        "linguaframe.redis.host=",
                        "linguaframe.redis.port=70000",
                        "linguaframe.rabbitmq.host=",
                        "linguaframe.rabbitmq.port=0",
                        "linguaframe.rabbitmq.username=",
                        "linguaframe.rabbitmq.password=",
                        "linguaframe.rabbitmq.job-exchange=",
                        "linguaframe.rabbitmq.job-queue=",
                        "linguaframe.rabbitmq.job-routing-key=",
                        "linguaframe.storage.endpoint=",
                        "linguaframe.storage.bucket=",
                        "linguaframe.storage.access-key=",
                        "linguaframe.storage.secret-key=",
                        "linguaframe.ffmpeg.binary-path=",
                        "linguaframe.ffmpeg.work-dir="
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
