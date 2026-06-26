package com.linguaframe.job.domain.vo;

import java.util.List;

public record LocalizationJobListVo(
        List<LocalizationJobSummaryVo> jobs,
        int limit,
        int offset,
        int total
) {
}
