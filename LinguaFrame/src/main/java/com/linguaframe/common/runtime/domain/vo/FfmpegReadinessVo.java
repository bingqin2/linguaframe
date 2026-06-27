package com.linguaframe.common.runtime.domain.vo;

public record FfmpegReadinessVo(
        boolean audioEnabled,
        boolean burnInEnabled,
        boolean binaryConfigured,
        boolean workspaceConfigured,
        int audioTimeoutSeconds,
        int burnInTimeoutSeconds
) {
}
