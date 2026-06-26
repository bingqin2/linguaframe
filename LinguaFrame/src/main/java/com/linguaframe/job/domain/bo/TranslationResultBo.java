package com.linguaframe.job.domain.bo;

import java.util.List;

public record TranslationResultBo(
        List<TranslationSegmentBo> segments
) {
}
