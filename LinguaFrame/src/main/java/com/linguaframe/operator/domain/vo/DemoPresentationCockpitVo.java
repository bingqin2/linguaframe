package com.linguaframe.operator.domain.vo;

import java.time.Instant;
import java.util.List;

public record DemoPresentationCockpitVo(
        Instant generatedAt,
        String overallStatus,
        String phase,
        String recommendedNextAction,
        DemoPresentationCockpitRunVo selectedRun,
        DemoPresentationCockpitRunVo activeRun,
        DemoPresentationCockpitRunVo recommendedRun,
        List<DemoPresentationCockpitCheckVo> checks,
        List<DemoPresentationCockpitLinkVo> links,
        List<String> safetyNotes
) {
}
