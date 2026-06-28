package com.linguaframe.job.domain.vo;

public record JobComparisonSettingDiffVo(
        String field,
        String baselineValue,
        String comparisonValue
) {
}
