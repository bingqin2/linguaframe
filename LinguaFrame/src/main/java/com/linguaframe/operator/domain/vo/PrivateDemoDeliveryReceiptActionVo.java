package com.linguaframe.operator.domain.vo;

public record PrivateDemoDeliveryReceiptActionVo(
        String id,
        String label,
        String command,
        String description,
        boolean primary
) {
}
