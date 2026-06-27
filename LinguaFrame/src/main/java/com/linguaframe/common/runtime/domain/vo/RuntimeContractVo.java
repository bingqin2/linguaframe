package com.linguaframe.common.runtime.domain.vo;

import java.util.List;

public record RuntimeContractVo(
        String appVersion,
        int latestMigrationVersion,
        List<String> requiredRoutes
) {
}
