package com.linguaframe.common.runtime.domain.vo;

public record RuntimeDependencySummaryVo(
        RuntimeContractVo runtime,
        NetworkDependencyVo database,
        NetworkDependencyVo redis,
        NetworkDependencyVo rabbitmq,
        StorageDependencyVo storage,
        DemoReadinessVo readiness
) {
}
