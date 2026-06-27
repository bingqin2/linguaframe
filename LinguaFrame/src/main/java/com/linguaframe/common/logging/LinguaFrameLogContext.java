package com.linguaframe.common.logging;

import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LinguaFrameLogContext {

    public static final String JOB_ID = "jobId";
    public static final String VIDEO_ID = "videoId";
    public static final String STAGE = "stage";
    public static final String WORKER_ROLE = "workerRole";

    private LinguaFrameLogContext() {
    }

    public static AutoCloseable withJob(String jobId, String videoId) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(JOB_ID, jobId);
        values.put(VIDEO_ID, videoId);
        return put(values);
    }

    public static AutoCloseable withStage(String stage, String workerRole) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(STAGE, stage);
        values.put(WORKER_ROLE, workerRole);
        return put(values);
    }

    private static AutoCloseable put(Map<String, String> values) {
        Map<String, String> previousValues = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            previousValues.put(key, MDC.get(key));
            if (value == null || value.isBlank()) {
                MDC.remove(key);
            } else {
                MDC.put(key, value);
            }
        }
        return () -> previousValues.forEach((key, value) -> {
            if (value == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, value);
            }
        });
    }
}
