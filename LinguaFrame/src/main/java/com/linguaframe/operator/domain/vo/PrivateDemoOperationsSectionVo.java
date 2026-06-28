package com.linguaframe.operator.domain.vo;

import java.util.List;

public record PrivateDemoOperationsSectionVo(
        String title,
        String status,
        List<PrivateDemoOperationsCheckVo> checks
) {
}
