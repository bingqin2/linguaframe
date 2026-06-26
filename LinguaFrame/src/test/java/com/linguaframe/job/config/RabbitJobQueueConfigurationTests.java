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
    private Binding localizationJobBinding;

    @Autowired
    private MessageConverter rabbitMessageConverter;

    @Test
    void declaresDurableLocalizationJobTopology() {
        assertThat(localizationJobExchange.getName()).isEqualTo("linguaframe.jobs");
        assertThat(localizationJobExchange.isDurable()).isTrue();
        assertThat(localizationJobQueue.getName()).isEqualTo("linguaframe.localization.jobs");
        assertThat(localizationJobQueue.isDurable()).isTrue();
        assertThat(localizationJobBinding.getExchange()).isEqualTo("linguaframe.jobs");
        assertThat(localizationJobBinding.getRoutingKey()).isEqualTo("localization.queued");
        assertThat(localizationJobBinding.getDestination()).isEqualTo("linguaframe.localization.jobs");
    }

    @Test
    void usesJsonMessageConverterForJobMessages() {
        assertThat(rabbitMessageConverter).isInstanceOf(Jackson2JsonMessageConverter.class);
    }
}
