package com.linguaframe.operator.domain.vo;

import java.util.List;

public record PrivateDemoDeliveryReceiptSectionVo(
        String id,
        String title,
        String status,
        List<String> facts
) {
}
