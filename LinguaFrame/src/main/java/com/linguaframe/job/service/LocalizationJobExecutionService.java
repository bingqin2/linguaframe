package com.linguaframe.job.service;

import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.domain.vo.LocalizationJobExecutionResultVo;

public interface LocalizationJobExecutionService {

    LocalizationJobExecutionResultVo execute(QueuedLocalizationJobMessage message);
}
