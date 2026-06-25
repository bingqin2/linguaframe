package com.linguaframe.common.runtime.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.common.runtime.domain.vo.NetworkDependencyVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeDependencySummaryVo;
import com.linguaframe.common.runtime.domain.vo.StorageDependencyVo;
import com.linguaframe.common.runtime.service.RuntimeDependencySummaryService;
import org.springframework.stereotype.Service;

@Service
public class RuntimeDependencySummaryServiceImpl implements RuntimeDependencySummaryService {

    private final LinguaFrameProperties properties;

    public RuntimeDependencySummaryServiceImpl(LinguaFrameProperties properties) {
        this.properties = properties;
    }

    @Override
    public RuntimeDependencySummaryVo getSummary() {
        return new RuntimeDependencySummaryVo(
                new NetworkDependencyVo(
                        "mysql",
                        properties.getDatabase().getHost(),
                        properties.getDatabase().getPort()
                ),
                new NetworkDependencyVo(
                        "redis",
                        properties.getRedis().getHost(),
                        properties.getRedis().getPort()
                ),
                new NetworkDependencyVo(
                        "rabbitmq",
                        properties.getRabbitmq().getHost(),
                        properties.getRabbitmq().getPort()
                ),
                new StorageDependencyVo(
                        "minio",
                        properties.getStorage().getEndpoint(),
                        properties.getStorage().getBucket()
                )
        );
    }
}
