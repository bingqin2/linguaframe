package com.linguaframe.job.service;

import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.media.domain.entity.VideoRecord;

public interface JobDispatchOutboxService {

    void enqueueLocalizationJobQueued(VideoRecord video, LocalizationJobRecord job);
}
