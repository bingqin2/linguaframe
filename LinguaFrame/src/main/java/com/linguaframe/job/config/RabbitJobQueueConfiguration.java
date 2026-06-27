package com.linguaframe.job.config;

import com.linguaframe.common.config.LinguaFrameProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
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
        return new Queue(properties.getRabbitmq().getFfmpegJobQueue(), true);
    }

    @Bean
    public Queue openaiLocalizationJobQueue(LinguaFrameProperties properties) {
        return new Queue(properties.getRabbitmq().getOpenaiJobQueue(), true);
    }

    @Bean
    public Binding localizationJobBinding(
            Queue localizationJobQueue,
            DirectExchange localizationJobExchange,
            LinguaFrameProperties properties
    ) {
        return BindingBuilder.bind(localizationJobQueue)
                .to(localizationJobExchange)
                .with(properties.getRabbitmq().getFfmpegJobRoutingKey());
    }

    @Bean
    public Binding openaiLocalizationJobBinding(
            Queue openaiLocalizationJobQueue,
            DirectExchange localizationJobExchange,
            LinguaFrameProperties properties
    ) {
        return BindingBuilder.bind(openaiLocalizationJobQueue)
                .to(localizationJobExchange)
                .with(properties.getRabbitmq().getOpenaiJobRoutingKey());
    }

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
