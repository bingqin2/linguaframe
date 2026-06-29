package com.linguaframe.job.domain.vo;

public record OpenAiSmokeProofCheckVo(
        String name,
        String status,
        String detail,
        String nextAction
) {
}
