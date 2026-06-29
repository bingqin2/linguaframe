package com.linguaframe.job.domain.vo;

import java.util.List;

public record DemoEvidenceClosureSectionVo(
        String key,
        String title,
        String status,
        String summary,
        List<String> facts,
        List<String> links
) {
}
