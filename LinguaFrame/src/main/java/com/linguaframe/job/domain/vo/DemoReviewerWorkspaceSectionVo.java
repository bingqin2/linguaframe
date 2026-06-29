package com.linguaframe.job.domain.vo;

import java.util.List;

public record DemoReviewerWorkspaceSectionVo(
        String key,
        String title,
        String status,
        List<String> facts
) {
}
