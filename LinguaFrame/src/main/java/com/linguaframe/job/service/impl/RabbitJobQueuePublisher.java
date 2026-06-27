package com.linguaframe.job.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.service.JobQueuePublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class RabbitJobQueuePublisher implements JobQueuePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final LinguaFrameProperties properties;

    public RabbitJobQueuePublisher(RabbitTemplate rabbitTemplate, LinguaFrameProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Override
    public void publish(QueuedLocalizationJobMessage message) {
        rabbitTemplate.convertAndSend(
                properties.getRabbitmq().getJobExchange(),
                routingKeyFor(message.startStage()),
                message
        );
    }

    private String routingKeyFor(LocalizationJobStage startStage) {
        return switch (startStage) {
            case TRANSCRIPT_SUBTITLE_EXPORT,
                 TARGET_SUBTITLE_EXPORT,
                 TRANSLATION_QUALITY_EVALUATION,
                 DUBBING_AUDIO_GENERATION -> properties.getRabbitmq().getOpenaiJobRoutingKey();
            default -> properties.getRabbitmq().getFfmpegJobRoutingKey();
        };
    }
}
