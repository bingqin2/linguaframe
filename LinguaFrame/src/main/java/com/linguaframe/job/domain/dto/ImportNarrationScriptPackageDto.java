package com.linguaframe.job.domain.dto;

import java.util.List;

public record ImportNarrationScriptPackageDto(
        Boolean replaceExisting,
        List<ImportNarrationScriptPackageSegmentDto> segments,
        UpdateNarrationMixSettingsDto mixSettings
) {
}
