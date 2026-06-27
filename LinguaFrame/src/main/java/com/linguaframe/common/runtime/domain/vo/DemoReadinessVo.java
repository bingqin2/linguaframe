package com.linguaframe.common.runtime.domain.vo;

import java.util.Map;

public record DemoReadinessVo(
        boolean demoAccessGate,
        WorkerReadinessVo worker,
        MediaReadinessVo media,
        FfmpegReadinessVo ffmpeg,
        BudgetReadinessVo budget,
        Map<String, ProviderReadinessVo> providers,
        Map<String, RuntimeFeatureFlagVo> features
) {
}
