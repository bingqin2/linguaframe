package com.linguaframe.media.domain.vo;

import java.util.List;

public record UploadNarrationScriptIntakeVo(
        String status,
        boolean supplied,
        int segmentCount,
        int characterCount,
        String voiceSummary,
        List<String> errors
) {
}
