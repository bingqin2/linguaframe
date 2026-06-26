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
        assertThat(properties.getCost().getTranscriptionUsdPerMinute()).isEqualByComparingTo("0");
        assertThat(properties.getCost().getTranslationInputUsdPerMillionTokens()).isEqualByComparingTo("0");
        assertThat(properties.getCost().getTranslationOutputUsdPerMillionTokens()).isEqualByComparingTo("0");
        assertThat(properties.getCost().getTtsUsdPerMillionCharacters()).isEqualByComparingTo("0");
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
        assertThat(properties.getFfmpeg().isBurnInEnabled()).isFalse();
        assertThat(properties.getFfmpeg().getBurnInTimeoutSeconds()).isEqualTo(180);
        assertThat(properties.getFfmpeg().getWorkDir()).isEqualTo("/tmp/linguaframe-media");
        assertThat(properties.getTranscription().isEnabled()).isFalse();
        assertThat(properties.getTranscription().getProvider()).isEqualTo("demo");
        assertThat(properties.getTranscription().getOpenai().getApiKey()).isEmpty();
        assertThat(properties.getTranscription().getOpenai().getModel()).isEmpty();
        assertThat(properties.getTranscription().getOpenai().getBaseUrl()).isEqualTo("https://api.openai.com");
        assertThat(properties.getTranscription().getOpenai().getTimeoutSeconds()).isEqualTo(120);
        assertThat(properties.getTranslation().isEnabled()).isFalse();
        assertThat(properties.getTranslation().getProvider()).isEqualTo("demo");
        assertThat(properties.getTranslation().getOpenai().getApiKey()).isEmpty();
        assertThat(properties.getTranslation().getOpenai().getModel()).isEmpty();
        assertThat(properties.getTranslation().getOpenai().getBaseUrl()).isEqualTo("https://api.openai.com");
        assertThat(properties.getTranslation().getOpenai().getTimeoutSeconds()).isEqualTo(60);
        assertThat(properties.getTts().isEnabled()).isFalse();
        assertThat(properties.getTts().getProvider()).isEqualTo("demo");
        assertThat(properties.getTts().getOpenai().getApiKey()).isEmpty();
        assertThat(properties.getTts().getOpenai().getModel()).isEmpty();
        assertThat(properties.getTts().getOpenai().getVoice()).isEmpty();
        assertThat(properties.getTts().getOpenai().getBaseUrl()).isEqualTo("https://api.openai.com");
        assertThat(properties.getTts().getOpenai().getTimeoutSeconds()).isEqualTo(120);
        assertThat(properties.getEvaluation().isEnabled()).isFalse();
        assertThat(properties.getEvaluation().getProvider()).isEqualTo("demo");
        assertThat(properties.getEvaluation().getOpenai().getApiKey()).isEmpty();
        assertThat(properties.getEvaluation().getOpenai().getModel()).isEmpty();
        assertThat(properties.getEvaluation().getOpenai().getBaseUrl()).isEqualTo("https://api.openai.com");
        assertThat(properties.getEvaluation().getOpenai().getTimeoutSeconds()).isEqualTo(60);
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
    void bindsCostRuntimeProperties() {
        contextRunner
                .withPropertyValues(
                        "linguaframe.cost.enabled=false",
                        "linguaframe.cost.transcription-usd-per-minute=0.006",
                        "linguaframe.cost.translation-input-usd-per-million-tokens=0.15",
                        "linguaframe.cost.translation-output-usd-per-million-tokens=0.60",
                        "linguaframe.cost.tts-usd-per-million-characters=15.00"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LinguaFrameProperties boundProperties = context.getBean(LinguaFrameProperties.class);
                    assertThat(boundProperties.getCost().isEnabled()).isFalse();
                    assertThat(boundProperties.getCost().getTranscriptionUsdPerMinute()).isEqualByComparingTo("0.006");
                    assertThat(boundProperties.getCost().getTranslationInputUsdPerMillionTokens()).isEqualByComparingTo("0.15");
                    assertThat(boundProperties.getCost().getTranslationOutputUsdPerMillionTokens()).isEqualByComparingTo("0.60");
                    assertThat(boundProperties.getCost().getTtsUsdPerMillionCharacters()).isEqualByComparingTo("15.00");
                });
    }

    @Test
    void bindsFfmpegRuntimeProperties() {
        contextRunner
                .withPropertyValues(
                        "linguaframe.ffmpeg.binary-path=/usr/bin/ffmpeg",
                        "linguaframe.ffmpeg.audio-enabled=true",
                        "linguaframe.ffmpeg.audio-timeout-seconds=30",
                        "linguaframe.ffmpeg.burn-in-enabled=true",
                        "linguaframe.ffmpeg.burn-in-timeout-seconds=45",
                        "linguaframe.ffmpeg.work-dir=/tmp/custom-media"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LinguaFrameProperties boundProperties = context.getBean(LinguaFrameProperties.class);
                    assertThat(boundProperties.getFfmpeg().getBinaryPath()).isEqualTo("/usr/bin/ffmpeg");
                    assertThat(boundProperties.getFfmpeg().isAudioEnabled()).isTrue();
                    assertThat(boundProperties.getFfmpeg().getAudioTimeoutSeconds()).isEqualTo(30);
                    assertThat(boundProperties.getFfmpeg().isBurnInEnabled()).isTrue();
                    assertThat(boundProperties.getFfmpeg().getBurnInTimeoutSeconds()).isEqualTo(45);
                    assertThat(boundProperties.getFfmpeg().getWorkDir()).isEqualTo("/tmp/custom-media");
                });
    }

    @Test
    void bindsTranscriptionRuntimeProperties() {
        contextRunner
                .withPropertyValues(
                        "linguaframe.transcription.enabled=true",
                        "linguaframe.transcription.provider=demo"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LinguaFrameProperties boundProperties = context.getBean(LinguaFrameProperties.class);
                    assertThat(boundProperties.getTranscription().isEnabled()).isTrue();
                    assertThat(boundProperties.getTranscription().getProvider()).isEqualTo("demo");
                });
    }

    @Test
    void bindsOpenAiTranscriptionRuntimeProperties() {
        contextRunner
                .withPropertyValues(
                        "linguaframe.transcription.enabled=true",
                        "linguaframe.transcription.provider=openai",
                        "linguaframe.transcription.openai.api-key=test-key",
                        "linguaframe.transcription.openai.model=whisper-1",
                        "linguaframe.transcription.openai.base-url=http://localhost:9999",
                        "linguaframe.transcription.openai.timeout-seconds=45"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LinguaFrameProperties boundProperties = context.getBean(LinguaFrameProperties.class);
                    assertThat(boundProperties.getTranscription().getProvider()).isEqualTo("openai");
                    assertThat(boundProperties.getTranscription().getOpenai().getApiKey()).isEqualTo("test-key");
                    assertThat(boundProperties.getTranscription().getOpenai().getModel()).isEqualTo("whisper-1");
                    assertThat(boundProperties.getTranscription().getOpenai().getBaseUrl()).isEqualTo("http://localhost:9999");
                    assertThat(boundProperties.getTranscription().getOpenai().getTimeoutSeconds()).isEqualTo(45);
                });
    }

    @Test
    void bindsTranslationRuntimeProperties() {
        contextRunner
                .withPropertyValues(
                        "linguaframe.translation.enabled=true",
                        "linguaframe.translation.provider=demo"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LinguaFrameProperties boundProperties = context.getBean(LinguaFrameProperties.class);
                    assertThat(boundProperties.getTranslation().isEnabled()).isTrue();
                    assertThat(boundProperties.getTranslation().getProvider()).isEqualTo("demo");
                });
    }

    @Test
    void bindsOpenAiTranslationRuntimeProperties() {
        contextRunner
                .withPropertyValues(
                        "linguaframe.translation.enabled=true",
                        "linguaframe.translation.provider=openai",
                        "linguaframe.translation.openai.api-key=test-key",
                        "linguaframe.translation.openai.model=test-model",
                        "linguaframe.translation.openai.base-url=http://localhost:9999",
                        "linguaframe.translation.openai.timeout-seconds=15"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LinguaFrameProperties boundProperties = context.getBean(LinguaFrameProperties.class);
                    assertThat(boundProperties.getTranslation().getProvider()).isEqualTo("openai");
                    assertThat(boundProperties.getTranslation().getOpenai().getApiKey()).isEqualTo("test-key");
                    assertThat(boundProperties.getTranslation().getOpenai().getModel()).isEqualTo("test-model");
                    assertThat(boundProperties.getTranslation().getOpenai().getBaseUrl()).isEqualTo("http://localhost:9999");
                    assertThat(boundProperties.getTranslation().getOpenai().getTimeoutSeconds()).isEqualTo(15);
                });
    }

    @Test
    void bindsOpenAiTtsRuntimeProperties() {
        contextRunner
                .withPropertyValues(
                        "linguaframe.tts.enabled=true",
                        "linguaframe.tts.provider=openai",
                        "linguaframe.tts.openai.api-key=test-key",
                        "linguaframe.tts.openai.model=gpt-4o-mini-tts",
                        "linguaframe.tts.openai.voice=alloy",
                        "linguaframe.tts.openai.base-url=http://localhost:9999",
                        "linguaframe.tts.openai.timeout-seconds=45"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LinguaFrameProperties boundProperties = context.getBean(LinguaFrameProperties.class);
                    assertThat(boundProperties.getTts().isEnabled()).isTrue();
                    assertThat(boundProperties.getTts().getProvider()).isEqualTo("openai");
                    assertThat(boundProperties.getTts().getOpenai().getApiKey()).isEqualTo("test-key");
                    assertThat(boundProperties.getTts().getOpenai().getModel()).isEqualTo("gpt-4o-mini-tts");
                    assertThat(boundProperties.getTts().getOpenai().getVoice()).isEqualTo("alloy");
                    assertThat(boundProperties.getTts().getOpenai().getBaseUrl()).isEqualTo("http://localhost:9999");
                    assertThat(boundProperties.getTts().getOpenai().getTimeoutSeconds()).isEqualTo(45);
                });
    }

    @Test
    void bindsOpenAiEvaluationRuntimeProperties() {
        contextRunner
                .withPropertyValues(
                        "linguaframe.evaluation.enabled=true",
                        "linguaframe.evaluation.provider=openai",
                        "linguaframe.evaluation.openai.api-key=test-key",
                        "linguaframe.evaluation.openai.model=test-evaluation-model",
                        "linguaframe.evaluation.openai.base-url=http://localhost:9999",
                        "linguaframe.evaluation.openai.timeout-seconds=20"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LinguaFrameProperties boundProperties = context.getBean(LinguaFrameProperties.class);
                    assertThat(boundProperties.getEvaluation().isEnabled()).isTrue();
                    assertThat(boundProperties.getEvaluation().getProvider()).isEqualTo("openai");
                    assertThat(boundProperties.getEvaluation().getOpenai().getApiKey()).isEqualTo("test-key");
                    assertThat(boundProperties.getEvaluation().getOpenai().getModel()).isEqualTo("test-evaluation-model");
                    assertThat(boundProperties.getEvaluation().getOpenai().getBaseUrl()).isEqualTo("http://localhost:9999");
                    assertThat(boundProperties.getEvaluation().getOpenai().getTimeoutSeconds()).isEqualTo(20);
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
                        "linguaframe.ffmpeg.audio-timeout-seconds=0",
                        "linguaframe.ffmpeg.burn-in-timeout-seconds=0",
                        "linguaframe.cost.tts-usd-per-million-characters=-1"
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
