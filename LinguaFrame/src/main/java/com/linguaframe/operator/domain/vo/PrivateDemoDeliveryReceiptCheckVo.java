package com.linguaframe.operator.domain.vo;

public record PrivateDemoDeliveryReceiptCheckVo(
        String id,
        String label,
        String status,
        String detail,
        String nextAction,
        boolean blocking
) {
}
