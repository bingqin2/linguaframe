package com.linguaframe.job.service;

import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;

public interface JobQueuePublisher {

    void publish(QueuedLocalizationJobMessage message);
}
