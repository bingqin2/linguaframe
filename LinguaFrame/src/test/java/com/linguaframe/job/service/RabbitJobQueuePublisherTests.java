package com.linguaframe.job.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.service.impl.RabbitJobQueuePublisher;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitJobQueuePublisherTests {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final LinguaFrameProperties properties = new LinguaFrameProperties();
    private final RabbitJobQueuePublisher publisher = new RabbitJobQueuePublisher(rabbitTemplate, properties);

    @Test
    void routesFfmpegStagesToFfmpegRoutingKey() {
        assertRoutesTo(LocalizationJobStage.WORKER_SMOKE, "localization.queued");
        assertRoutesTo(LocalizationJobStage.AUDIO_EXTRACTION, "localization.queued");
        assertRoutesTo(LocalizationJobStage.SUBTITLE_BURN_IN, "localization.queued");
        assertRoutesTo(LocalizationJobStage.ARTIFACT_SUMMARY, "localization.queued");
    }

    @Test
    void routesOpenAiStagesToOpenAiRoutingKey() {
        assertRoutesTo(LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT, "localization.openai");
        assertRoutesTo(LocalizationJobStage.TARGET_SUBTITLE_EXPORT, "localization.openai");
        assertRoutesTo(LocalizationJobStage.TRANSLATION_QUALITY_EVALUATION, "localization.openai");
        assertRoutesTo(LocalizationJobStage.DUBBING_AUDIO_GENERATION, "localization.openai");
    }

    private void assertRoutesTo(LocalizationJobStage startStage, String routingKey) {
        QueuedLocalizationJobMessage message = new QueuedLocalizationJobMessage(
                "publisher-job-" + startStage.name(),
                "publisher-video",
                "source.mp4",
                "zh-CN",
                Instant.parse("2026-06-26T10:00:00Z"),
                startStage
        );

        publisher.publish(message);

        verify(rabbitTemplate).convertAndSend("linguaframe.jobs", routingKey, message);
    }
}
