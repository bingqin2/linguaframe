package com.linguaframe.job.config;

import com.linguaframe.common.config.LinguaFrameProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitJobQueueConfiguration {

    @Bean
    public DirectExchange localizationJobExchange(LinguaFrameProperties properties) {
        return new DirectExchange(properties.getRabbitmq().getJobExchange(), true, false);
    }

    @Bean
    public Queue localizationJobQueue(LinguaFrameProperties properties) {
        return new Queue(properties.getRabbitmq().getJobQueue(), true);
    }

    @Bean
    public Binding localizationJobBinding(
            Queue localizationJobQueue,
            DirectExchange localizationJobExchange,
            LinguaFrameProperties properties
    ) {
        return BindingBuilder.bind(localizationJobQueue)
                .to(localizationJobExchange)
                .with(properties.getRabbitmq().getJobRoutingKey());
    }
}
