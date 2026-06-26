package com.linguaframe.job.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface LocalizationJobProgressStreamService {

    SseEmitter streamJob(String jobId);
}
