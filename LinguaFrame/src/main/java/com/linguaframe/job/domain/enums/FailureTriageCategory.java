package com.linguaframe.job.domain.enums;

public enum FailureTriageCategory {
    CONFIGURATION,
    OPENAI_AUTH_OR_MODEL,
    OPENAI_TIMEOUT_OR_NETWORK,
    BUDGET_GUARD,
    MEDIA_PROCESSING,
    STORAGE_OR_ARTIFACT,
    WORKER_OR_QUEUE,
    USER_CANCELLED,
    UNKNOWN
}
