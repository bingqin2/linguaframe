package com.linguaframe.job.domain.vo;

import java.util.List;

public record NarrationMixAutomationVo(
        int keyframeCount,
        int duckingKeyframeCount,
        int narrationKeyframeCount,
        int fadeKeyframeCount,
        List<NarrationMixKeyframeVo> keyframes,
        List<String> safetyNotes
) {
}
