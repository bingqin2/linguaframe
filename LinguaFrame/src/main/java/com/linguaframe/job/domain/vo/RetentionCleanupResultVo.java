package com.linguaframe.job.domain.vo;

public record RetentionCleanupResultVo(
        boolean dryRun,
        int candidateJobCount,
        int deletedJobCount,
        int deletedVideoCount,
        int deletedObjectCount,
        int skippedObjectCount,
        int failureCount
) {
    public static RetentionCleanupResultVo emptyDryRun() {
        return new RetentionCleanupResultVo(true, 0, 0, 0, 0, 0, 0);
    }
}
