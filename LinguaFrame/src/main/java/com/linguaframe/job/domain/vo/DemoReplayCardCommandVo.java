package com.linguaframe.job.domain.vo;

public record DemoReplayCardCommandVo(
        String kind,
        String label,
        String command,
        String note
) {
}
