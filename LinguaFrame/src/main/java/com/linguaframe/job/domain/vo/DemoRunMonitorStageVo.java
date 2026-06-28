package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;

import java.time.Instant;

public record DemoRunMonitorStageVo(
        LocalizationJobStage stage,
        JobTimelineEventStatus status,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        Long runningForMs,
        String attention,
        String message
) {
}
