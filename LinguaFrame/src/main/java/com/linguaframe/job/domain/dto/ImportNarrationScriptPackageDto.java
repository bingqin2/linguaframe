package com.linguaframe.job.domain.dto;

import java.util.List;

public record ImportNarrationScriptPackageDto(
        Boolean replaceExisting,
        List<ImportNarrationScriptPackageSegmentDto> segments,
        UpdateNarrationMixSettingsDto mixSettings,
        List<ImportNarrationMixKeyframeDto> mixKeyframes
) {
    public ImportNarrationScriptPackageDto(
            Boolean replaceExisting,
            List<ImportNarrationScriptPackageSegmentDto> segments,
            UpdateNarrationMixSettingsDto mixSettings
    ) {
        this(replaceExisting, segments, mixSettings, List.of());
    }
}
