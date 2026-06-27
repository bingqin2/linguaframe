package com.linguaframe.job.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RabbitJobQueueConfigurationTests {

    @Autowired
    private DirectExchange localizationJobExchange;

    @Autowired
    private Queue localizationJobQueue;

    @Autowired
    private Queue openaiLocalizationJobQueue;

    @Autowired
    private Binding localizationJobBinding;

    @Autowired
    private Binding openaiLocalizationJobBinding;

    @Autowired
    private MessageConverter rabbitMessageConverter;

    @Test
    void declaresDurableLocalizationJobTopology() {
        assertThat(localizationJobExchange.getName()).isEqualTo("linguaframe.jobs");
        assertThat(localizationJobExchange.isDurable()).isTrue();
        assertThat(localizationJobQueue.getName()).isEqualTo("linguaframe.localization.jobs");
        assertThat(localizationJobQueue.isDurable()).isTrue();
        assertThat(openaiLocalizationJobQueue.getName()).isEqualTo("linguaframe.localization.openai.jobs");
        assertThat(openaiLocalizationJobQueue.isDurable()).isTrue();
        assertThat(localizationJobBinding.getExchange()).isEqualTo("linguaframe.jobs");
        assertThat(localizationJobBinding.getRoutingKey()).isEqualTo("localization.queued");
        assertThat(localizationJobBinding.getDestination()).isEqualTo("linguaframe.localization.jobs");
        assertThat(openaiLocalizationJobBinding.getExchange()).isEqualTo("linguaframe.jobs");
        assertThat(openaiLocalizationJobBinding.getRoutingKey()).isEqualTo("localization.openai");
        assertThat(openaiLocalizationJobBinding.getDestination()).isEqualTo("linguaframe.localization.openai.jobs");
    }

    @Test
    void usesJsonMessageConverterForJobMessages() {
        assertThat(rabbitMessageConverter).isInstanceOf(Jackson2JsonMessageConverter.class);
    }
}
