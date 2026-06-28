package com.linguaframe.operator.domain.vo;

public record DemoSampleMediaConfiguredPathVo(
        String envVar,
        String status,
        String filename,
        String extension,
        Long sizeBytes,
        String message,
        boolean fullPathExposed
) {
}
