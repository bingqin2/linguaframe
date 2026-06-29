package com.linguaframe.media.domain.vo;

public record UploadSourceReuseDecisionActionVo(
        String id,
        String label,
        String kind,
        boolean enabled,
        String detail,
        String href
) {
}
