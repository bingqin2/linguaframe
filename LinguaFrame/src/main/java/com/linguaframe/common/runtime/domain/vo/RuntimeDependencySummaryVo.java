package com.linguaframe.common.runtime.domain.vo;

public record RuntimeDependencySummaryVo(
        NetworkDependencyVo database,
        NetworkDependencyVo redis,
        NetworkDependencyVo rabbitmq,
        StorageDependencyVo storage,
        DemoReadinessVo readiness
) {
}
