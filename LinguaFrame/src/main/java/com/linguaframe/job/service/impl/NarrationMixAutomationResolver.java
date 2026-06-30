package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.entity.NarrationMixKeyframeRecord;
import com.linguaframe.job.domain.entity.NarrationMixSettingsRecord;
import com.linguaframe.job.domain.entity.NarrationSegmentRecord;
import com.linguaframe.job.domain.enums.NarrationMixLane;
import com.linguaframe.media.domain.bo.NarrationWindowBo;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public class NarrationMixAutomationResolver {

    public NarrationWindowBo resolveWindow(
            NarrationSegmentRecord segment,
            NarrationMixSettingsRecord mixSettings,
            List<NarrationMixKeyframeRecord> keyframes
    ) {
        return new NarrationWindowBo(
                segment.startSeconds(),
                segment.endSeconds(),
                segment.duckingVolume() == null
                        ? keyframeValue(keyframes, NarrationMixLane.DUCKING_VOLUME, segment.startSeconds(), mixSettings.duckingVolume())
                        : segment.duckingVolume(),
                segment.narrationVolume() == null
                        ? keyframeValue(keyframes, NarrationMixLane.NARRATION_VOLUME, segment.startSeconds(), mixSettings.narrationVolume())
                        : segment.narrationVolume(),
                segment.fadeDurationMs() == null
                        ? keyframeValue(keyframes, NarrationMixLane.FADE_DURATION_MS, segment.startSeconds(), BigDecimal.valueOf(mixSettings.fadeDurationMs())).intValue()
                        : segment.fadeDurationMs()
        );
    }

    private BigDecimal keyframeValue(
            List<NarrationMixKeyframeRecord> keyframes,
            NarrationMixLane lane,
            BigDecimal startSeconds,
            BigDecimal defaultValue
    ) {
        return keyframes.stream()
                .filter(keyframe -> keyframe.lane() == lane)
                .filter(keyframe -> keyframe.timeSeconds().compareTo(startSeconds) <= 0)
                .max(Comparator.comparing(NarrationMixKeyframeRecord::timeSeconds))
                .map(NarrationMixKeyframeRecord::value)
                .orElse(defaultValue);
    }
}
