package com.linguaframe.operator.domain.vo;

import java.time.Instant;
import java.util.List;

public record PrivateDemoDeliveryReceiptVo(
        Instant generatedAt,
        String overallStatus,
        String selectedJobId,
        String recommendedJobId,
        String recommendedVideoId,
        String recommendedReadiness,
        String operationsStatus,
        String launchStatus,
        String galleryStatus,
        String archiveStatus,
        String commandCenterStatus,
        String recoveryStatus,
        String modelUsageStatus,
        String openAiReadinessStatus,
        List<PrivateDemoDeliveryReceiptCheckVo> checks,
        List<PrivateDemoDeliveryReceiptSectionVo> sections,
        List<PrivateDemoDeliveryReceiptActionVo> actions,
        List<PrivateDemoDeliveryReceiptLinkVo> evidenceLinks,
        List<PrivateDemoDeliveryReceiptPackageEntryVo> packageEntries,
        List<String> safetyNotes,
        String receiptNotesMarkdown
) {
}
