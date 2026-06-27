package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.FailureTriageCategory;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.domain.enums.ModelCallStatus;
import com.linguaframe.job.domain.vo.FailureTriageVo;
import com.linguaframe.job.domain.vo.ModelCallVo;
import com.linguaframe.job.service.FailureTriageService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class FailureTriageServiceImpl implements FailureTriageService {

    @Override
    public FailureTriageVo triage(
            LocalizationJobRecord record,
            List<JobTimelineEventRecord> timelineEvents,
            List<ModelCallVo> modelCalls
    ) {
        if (record.status() == LocalizationJobStatus.CANCELLED) {
            return triage(
                    FailureTriageCategory.USER_CANCELLED,
                    "The job was cancelled before completion.",
                    "Upload a new job when you are ready to process the video again.",
                    false,
                    null,
                    List.of("status=CANCELLED")
            );
        }
        if (record.status() != LocalizationJobStatus.FAILED) {
            return null;
        }

        Evidence evidence = Evidence.from(record, timelineEvents, modelCalls);
        if (evidence.hasAny("budget exceeded", "cost budget", "daily cost", "max job cost")) {
            return triage(
                    FailureTriageCategory.BUDGET_GUARD,
                    "The configured cost budget stopped the job before a later provider call.",
                    "Use the deterministic demo profile or raise the relevant job or daily budget environment value before retrying.",
                    false,
                    "scripts/demo/docker-e2e-success.sh",
                    evidence.safeDetails()
            );
        }
        if (evidence.hasOpenAiFailure() && evidence.hasAny(
                "401",
                "403",
                "invalid_api_key",
                "incorrect api key",
                "missing model",
                "unknown model",
                "model not found",
                "does not exist",
                "not have access"
        )) {
            return triage(
                    FailureTriageCategory.OPENAI_AUTH_OR_MODEL,
                    "OpenAI rejected the configured credentials or model.",
                    "Run the OpenAI preflight, then fix OPENAI_API_KEY, OPENAI_BASE_URL, or the enabled OpenAI model values before retrying.",
                    false,
                    "scripts/demo/openai-demo-preflight.sh",
                    evidence.safeDetails()
            );
        }
        if (evidence.hasOpenAiFailure() && evidence.hasAny(
                "timeout",
                "timed out",
                "connection reset",
                "connect",
                "dns",
                "rate limit",
                "429",
                "500",
                "502",
                "503",
                "504"
        )) {
            return triage(
                    FailureTriageCategory.OPENAI_TIMEOUT_OR_NETWORK,
                    "The OpenAI provider call failed because of network, timeout, rate-limit, or server-side availability evidence.",
                    "Re-run the OpenAI preflight and retry after connectivity or rate-limit pressure clears.",
                    true,
                    "scripts/demo/openai-demo-preflight.sh",
                    evidence.safeDetails()
            );
        }
        if (record.failureStage() == LocalizationJobStage.AUDIO_EXTRACTION
                || record.failureStage() == LocalizationJobStage.SUBTITLE_BURN_IN
                || evidence.hasAny("ffmpeg", "unsupported media", "unreadable media", "duration", "audio extraction", "burn-in", "burned video")) {
            return triage(
                    FailureTriageCategory.MEDIA_PROCESSING,
                    "The job failed while reading, extracting, or rendering media.",
                    "Check that the video is readable, within the configured duration limit, and playable by FFmpeg before uploading again.",
                    false,
                    null,
                    evidence.safeDetails()
            );
        }
        if (evidence.hasAny("artifact", "minio", "object storage", "archive", "bundle", "download")) {
            return triage(
                    FailureTriageCategory.STORAGE_OR_ARTIFACT,
                    "The job failed while storing or packaging generated artifacts.",
                    "Check MinIO/object-storage readiness and rerun the job after storage is healthy.",
                    true,
                    null,
                    evidence.safeDetails()
            );
        }
        if (record.failureStage() == LocalizationJobStage.WORKER_RECEIVED
                || record.failureStage() == LocalizationJobStage.WORKER_SMOKE
                || evidence.hasAny("worker", "dispatch", "queue", "rabbitmq", "message")) {
            return triage(
                    FailureTriageCategory.WORKER_OR_QUEUE,
                    "The worker or queue handoff failed before the full localization pipeline could run.",
                    "Check backend worker logs, RabbitMQ readiness, and dispatch status, then retry the failed job.",
                    true,
                    null,
                    evidence.safeDetails()
            );
        }
        if (evidence.hasAny("missing required", "not configured", "configuration", "config", "required property")) {
            return triage(
                    FailureTriageCategory.CONFIGURATION,
                    "A required runtime configuration value appears to be missing or invalid.",
                    "Fix the relevant environment variable or application property, restart the backend, and retry.",
                    false,
                    null,
                    evidence.safeDetails()
            );
        }
        return triage(
                FailureTriageCategory.UNKNOWN,
                "The failure did not match a known triage category.",
                "Inspect diagnostics and backend logs, then retry only after the underlying cause is understood.",
                true,
                null,
                evidence.safeDetails()
        );
    }

    private FailureTriageVo triage(
            FailureTriageCategory category,
            String summary,
            String recommendedAction,
            boolean retryable,
            String runbookCommand,
            List<String> safeDetails
    ) {
        return new FailureTriageVo(category, summary, recommendedAction, retryable, runbookCommand, safeDetails);
    }

    private record Evidence(String combined, List<String> safeDetails, boolean hasOpenAiFailure) {

        static Evidence from(
                LocalizationJobRecord record,
                List<JobTimelineEventRecord> timelineEvents,
                List<ModelCallVo> modelCalls
        ) {
            List<String> details = new ArrayList<>();
            if (record.failureStage() != null) {
                details.add("failureStage=" + record.failureStage());
            }
            if (hasText(record.failureReason())) {
                details.add("failureReason=" + record.failureReason());
            }
            for (JobTimelineEventRecord event : timelineEvents) {
                if (hasText(event.errorSummary())) {
                    details.add("timeline:" + event.stage() + "=" + event.errorSummary());
                }
            }

            boolean openAiFailure = false;
            for (ModelCallVo call : modelCalls) {
                if (call.provider() == ModelCallProvider.OPENAI && call.status() == ModelCallStatus.FAILED) {
                    openAiFailure = true;
                }
                if (hasText(call.safeErrorSummary())) {
                    details.add("modelCall:" + call.operation() + ":" + call.provider() + "=" + call.safeErrorSummary());
                }
            }

            String combinedText = String.join(" ", details).toLowerCase(Locale.ROOT);
            return new Evidence(combinedText, List.copyOf(details), openAiFailure);
        }

        boolean hasAny(String... needles) {
            for (String needle : needles) {
                if (combined.contains(needle.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
