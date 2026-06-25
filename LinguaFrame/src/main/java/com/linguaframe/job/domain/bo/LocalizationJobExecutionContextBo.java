package com.linguaframe.job.domain.bo;

import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;

import java.time.Instant;

public record LocalizationJobExecutionContextBo(
        LocalizationJobRecord job,
        QueuedLocalizationJobMessage message,
        Instant startedAt
) {
}
