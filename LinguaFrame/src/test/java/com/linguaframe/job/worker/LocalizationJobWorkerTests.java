package com.linguaframe.job.worker;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.domain.vo.LocalizationJobExecutionResultVo;
import com.linguaframe.job.service.LocalizationJobExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class LocalizationJobWorkerTests {

    @Autowired
    private LocalizationJobWorker contextWorker;

    @Test
    void listenerBeanExistsWithRabbitListenerAnnotation() throws Exception {
        RabbitListener listener = contextWorker.getClass()
                .getMethod("handle", QueuedLocalizationJobMessage.class)
                .getAnnotation(RabbitListener.class);

        assertThat(listener).isNotNull();
        assertThat(listener.queues()).containsExactly("${linguaframe.rabbitmq.listener-queue}");
        assertThat(listener.autoStartup()).isEqualTo("${linguaframe.worker.execution-enabled:false}");
    }

    @Test
    void handleDelegatesExactMessageToExecutionService() {
        RecordingExecutionService executionService = new RecordingExecutionService(false);
        LocalizationJobWorker worker = new LocalizationJobWorker(executionService);
        QueuedLocalizationJobMessage message = message();

        worker.handle(message);

        assertThat(executionService.message).isSameAs(message);
    }

    @Test
    void handleDoesNotSwallowExecutionException() {
        RecordingExecutionService executionService = new RecordingExecutionService(true);
        LocalizationJobWorker worker = new LocalizationJobWorker(executionService);

        assertThatThrownBy(() -> worker.handle(message()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("execution failed");
    }

    private QueuedLocalizationJobMessage message() {
        return new QueuedLocalizationJobMessage(
                "worker-job-1",
                "worker-video-1",
                "source-videos/worker-video-1/sample.mp4",
                "zh-CN",
                Instant.parse("2026-06-26T18:00:00Z")
        );
    }

    private static class RecordingExecutionService implements LocalizationJobExecutionService {

        private final boolean fail;
        private QueuedLocalizationJobMessage message;

        private RecordingExecutionService(boolean fail) {
            this.fail = fail;
        }

        @Override
        public LocalizationJobExecutionResultVo execute(QueuedLocalizationJobMessage message) {
            this.message = message;
            if (fail) {
                throw new IllegalStateException("execution failed");
            }
            return new LocalizationJobExecutionResultVo(message.jobId(), true, LocalizationJobStatus.COMPLETED);
        }
    }
}
