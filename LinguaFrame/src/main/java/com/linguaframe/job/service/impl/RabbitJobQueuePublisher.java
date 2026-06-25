package com.linguaframe.job.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
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
                properties.getRabbitmq().getJobRoutingKey(),
                message
        );
    }
}
