package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.domain.enums.ModelCallStatus;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.ModelCallVo;
import com.linguaframe.job.domain.vo.OpenAiSmokeProofArtifactVo;
import com.linguaframe.job.domain.vo.OpenAiSmokeProofCallVo;
import com.linguaframe.job.domain.vo.OpenAiSmokeProofCheckVo;
import com.linguaframe.job.domain.vo.OpenAiSmokeProofLinkVo;
import com.linguaframe.job.domain.vo.OpenAiSmokeProofVo;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.job.service.OpenAiSmokeProofService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class OpenAiSmokeProofServiceImpl implements OpenAiSmokeProofService {

    private static final Set<JobArtifactType> REQUIRED_ARTIFACT_TYPES = EnumSet.of(
            JobArtifactType.TRANSCRIPT_JSON,
            JobArtifactType.TARGET_SUBTITLE_JSON,
            JobArtifactType.TARGET_SUBTITLE_SRT,
            JobArtifactType.TARGET_SUBTITLE_VTT
    );

    private final LocalizationJobQueryService queryService;
    private final JobArtifactService artifactService;

    public OpenAiSmokeProofServiceImpl(LocalizationJobQueryService queryService, JobArtifactService artifactService) {
        this.queryService = queryService;
        this.artifactService = artifactService;
    }

    @Override
    public OpenAiSmokeProofVo getProof(String jobId) {
        LocalizationJobVo job = queryService.getJob(jobId);
        List<JobArtifactVo> artifacts = artifactService.listArtifacts(jobId);
        List<OpenAiSmokeProofCheckVo> requiredChecks = requiredChecks(job, artifacts);
        List<OpenAiSmokeProofCheckVo> optionalChecks = optionalChecks(job, artifacts);
        String overallStatus = overallStatus(requiredChecks, optionalChecks);
        return new OpenAiSmokeProofVo(
                job.jobId(),
                job.videoId(),
                job.targetLanguage(),
                overallStatus,
                phase(overallStatus),
                recommendedNextAction(overallStatus),
                job.completedAt(),
                requiredChecks,
                optionalChecks,
                job.modelCalls().stream()
                        .filter(call -> call.provider() == ModelCallProvider.OPENAI)
                        .sorted(Comparator.comparing(ModelCallVo::createdAt))
                        .map(this::callProof)
                        .toList(),
                artifacts.stream()
                        .filter(artifact -> REQUIRED_ARTIFACT_TYPES.contains(artifact.type()) || isOptionalArtifact(artifact.type()))
                        .sorted(Comparator.comparing(JobArtifactVo::createdAt))
                        .map(this::artifactProof)
                        .toList(),
                safeLinks(job.jobId(), job.targetLanguage()),
                List.of(
                        "Metadata only: no media bytes, local paths, object keys, provider payloads, transcripts, subtitles, tokens, or credentials are included.",
                        "Use OpenAI readiness evidence before upload and this smoke proof after a completed provider-backed job."
                )
        );
    }

    @Override
    public String renderMarkdown(String jobId) {
        OpenAiSmokeProofVo proof = getProof(jobId);
        List<String> lines = new ArrayList<>();
        lines.add("# LinguaFrame OpenAI Smoke Proof");
        lines.add("");
        lines.add("## Summary");
        lines.add("- Job: " + value(proof.jobId()));
        lines.add("- Video: " + value(proof.videoId()));
        lines.add("- Target language: " + value(proof.targetLanguage()));
        lines.add("- Overall status: " + value(proof.overallStatus()));
        lines.add("- Phase: " + value(proof.phase()));
        lines.add("- Completed at: " + value(proof.completedAt()));
        lines.add("- Recommended next action: " + value(proof.recommendedNextAction()));
        lines.add("");
        appendChecks(lines, "Required Checks", proof.requiredChecks());
        appendChecks(lines, "Optional Evidence", proof.optionalChecks());
        lines.add("## OpenAI Model Calls");
        if (proof.modelCalls().isEmpty()) {
            lines.add("- None recorded.");
        } else {
            for (OpenAiSmokeProofCallVo call : proof.modelCalls()) {
                lines.add("- " + call.operation() + ": " + call.status()
                        + ", stage=" + call.stage()
                        + ", model=" + value(call.model())
                        + ", prompt=" + value(call.promptVersion())
                        + ", latencyMs=" + call.latencyMs()
                        + ", estimatedCostUsd=" + value(call.estimatedCostUsd()));
                if (call.safeErrorSummary() != null && !call.safeErrorSummary().isBlank()) {
                    lines.add("  - Safe error summary: " + call.safeErrorSummary());
                }
            }
        }
        lines.add("");
        lines.add("## Artifacts");
        for (OpenAiSmokeProofArtifactVo artifact : proof.artifacts()) {
            lines.add("- " + artifact.type() + ": " + artifact.filename()
                    + ", contentType=" + artifact.contentType()
                    + ", sizeBytes=" + artifact.sizeBytes()
                    + ", sha256=" + value(artifact.contentSha256()));
        }
        lines.add("");
        lines.add("## Safe Links");
        for (OpenAiSmokeProofLinkVo link : proof.safeLinks()) {
            lines.add("- " + link.label() + ": " + link.href());
        }
        lines.add("");
        lines.add("## Safety Notes");
        for (String note : proof.safetyNotes()) {
            lines.add("- " + note);
        }
        lines.add("");
        return String.join("\n", lines);
    }

    private List<OpenAiSmokeProofCheckVo> requiredChecks(LocalizationJobVo job, List<JobArtifactVo> artifacts) {
        List<OpenAiSmokeProofCheckVo> checks = new ArrayList<>();
        checks.add(job.status() == LocalizationJobStatus.COMPLETED
                ? ready("Job completed", "Job reached COMPLETED at " + value(job.completedAt()))
                : blocked("Job completed", "Job status is " + job.status(), "Wait for completion or inspect failure triage."));
        checks.add(openAiCallCheck(job, ModelCallOperation.TRANSCRIPTION, "OpenAI transcription call"));
        checks.add(openAiCallCheck(job, ModelCallOperation.TRANSLATION, "OpenAI translation call"));
        for (JobArtifactType type : REQUIRED_ARTIFACT_TYPES) {
            checks.add(artifactCheck(artifacts, type, "Required artifact " + type.name()));
        }
        if (job.modelCalls().stream().anyMatch(call -> call.provider() == ModelCallProvider.OPENAI && call.status() == ModelCallStatus.FAILED)) {
            checks.add(blocked("OpenAI failed calls", "One or more OpenAI model calls failed.", "Inspect model calls before presenting this run."));
        } else {
            checks.add(ready("OpenAI failed calls", "No failed OpenAI model calls recorded."));
        }
        return checks;
    }

    private List<OpenAiSmokeProofCheckVo> optionalChecks(LocalizationJobVo job, List<JobArtifactVo> artifacts) {
        List<OpenAiSmokeProofCheckVo> checks = new ArrayList<>();
        checks.add(job.qualityEvaluation() == null
                ? attention("Quality evaluation", "Quality evaluation was not recorded.", "Use quality evidence when available, but core smoke proof can still be reviewed.")
                : ready("Quality evaluation", "Quality score " + job.qualityEvaluation().score() + " / 100, verdict " + job.qualityEvaluation().verdict() + "."));
        checks.add(optionalArtifactCheck(artifacts, JobArtifactType.DUBBING_AUDIO, "TTS dubbing audio"));
        checks.add(optionalArtifactCheck(artifacts, JobArtifactType.BURNED_VIDEO, "Burned video"));
        checks.add(optionalArtifactCheck(artifacts, JobArtifactType.DUBBED_VIDEO, "Dubbed video"));
        checks.add(ready("Demo run package", "Safe package route is available."));
        checks.add(ready("AI audit package", "Safe package route is available."));
        return checks;
    }

    private OpenAiSmokeProofCheckVo openAiCallCheck(LocalizationJobVo job, ModelCallOperation operation, String name) {
        boolean succeeded = job.modelCalls().stream()
                .anyMatch(call -> call.provider() == ModelCallProvider.OPENAI
                        && call.operation() == operation
                        && call.status() == ModelCallStatus.SUCCEEDED);
        if (succeeded) {
            return ready(name, "Successful OpenAI " + operation.name() + " call recorded.");
        }
        return blocked(name, "Missing successful OpenAI " + operation.name() + " call.", "Inspect model calls and rerun OpenAI smoke after fixing provider configuration.");
    }

    private OpenAiSmokeProofCheckVo artifactCheck(List<JobArtifactVo> artifacts, JobArtifactType type, String name) {
        boolean present = artifacts.stream().anyMatch(artifact -> artifact.type() == type);
        if (present) {
            return ready(name, type.name() + " artifact is recorded.");
        }
        return blocked(name, "Missing " + type.name() + " artifact.", "Inspect pipeline artifacts and rerun the smoke job if required.");
    }

    private OpenAiSmokeProofCheckVo optionalArtifactCheck(List<JobArtifactVo> artifacts, JobArtifactType type, String name) {
        boolean present = artifacts.stream().anyMatch(artifact -> artifact.type() == type);
        if (present) {
            return ready(name, type.name() + " artifact is recorded.");
        }
        return attention(name, type.name() + " artifact is not recorded.", "Optional evidence is unavailable for this run.");
    }

    private String overallStatus(List<OpenAiSmokeProofCheckVo> requiredChecks, List<OpenAiSmokeProofCheckVo> optionalChecks) {
        if (requiredChecks.stream().anyMatch(check -> "BLOCKED".equals(check.status()))) {
            return "BLOCKED";
        }
        if (optionalChecks.stream().anyMatch(check -> "ATTENTION".equals(check.status()) || "BLOCKED".equals(check.status()))) {
            return "ATTENTION";
        }
        return "READY";
    }

    private static String phase(String status) {
        return switch (status) {
            case "READY" -> "OPENAI_SMOKE_PROVEN";
            case "ATTENTION" -> "OPENAI_SMOKE_NEEDS_REVIEW";
            default -> "OPENAI_SMOKE_BLOCKED";
        };
    }

    private static String recommendedNextAction(String status) {
        return switch (status) {
            case "READY" -> "Use this proof, demo run package, and AI audit package to present the completed OpenAI smoke run.";
            case "ATTENTION" -> "Review optional evidence gaps, then decide whether the completed core OpenAI smoke proof is enough to present.";
            default -> "Inspect model calls, artifacts, and job diagnostics before rerunning OpenAI smoke.";
        };
    }

    private OpenAiSmokeProofCallVo callProof(ModelCallVo call) {
        return new OpenAiSmokeProofCallVo(
                value(call.stage()),
                value(call.operation()),
                value(call.provider()),
                call.model(),
                call.promptVersion(),
                value(call.status()),
                call.latencyMs(),
                call.inputTokens(),
                call.outputTokens(),
                call.audioSeconds(),
                call.characterCount(),
                call.estimatedCostUsd(),
                safeSummary(call.safeErrorSummary())
        );
    }

    private OpenAiSmokeProofArtifactVo artifactProof(JobArtifactVo artifact) {
        return new OpenAiSmokeProofArtifactVo(
                artifact.artifactId(),
                value(artifact.type()),
                artifact.filename(),
                artifact.contentType(),
                artifact.sizeBytes(),
                artifact.contentSha256(),
                artifact.cacheHit(),
                artifact.createdAt()
        );
    }

    private static boolean isOptionalArtifact(JobArtifactType type) {
        return type == JobArtifactType.DUBBING_AUDIO
                || type == JobArtifactType.BURNED_VIDEO
                || type == JobArtifactType.DUBBED_VIDEO;
    }

    private static List<OpenAiSmokeProofLinkVo> safeLinks(String jobId, String targetLanguage) {
        return List.of(
                link("Job detail", "/api/jobs/" + jobId, "application/json", "Safe job detail."),
                link("OpenAI smoke proof Markdown", "/api/jobs/" + jobId + "/openai-smoke-proof/markdown/download", "text/markdown", "This proof as Markdown."),
                link("Diagnostics", "/api/jobs/" + jobId + "/diagnostics/download", "application/json", "Safe diagnostics report."),
                link("Quality evidence", "/api/jobs/" + jobId + "/quality-evaluation/evidence/markdown/download", "text/markdown", "Quality evaluation evidence."),
                link("Subtitle review", "/api/jobs/" + jobId + "/subtitle-review?language=" + value(targetLanguage), "application/json", "Safe subtitle review summary."),
                link("Demo run package", "/api/jobs/" + jobId + "/demo-run-package/download", "application/zip", "Metadata-only demo run package."),
                link("AI audit package", "/api/jobs/" + jobId + "/ai-audit-package/download", "application/zip", "Prompt, model-call, usage, and cost audit package.")
        );
    }

    private static OpenAiSmokeProofLinkVo link(String label, String href, String contentType, String description) {
        return new OpenAiSmokeProofLinkVo(label, href, contentType, description);
    }

    private static OpenAiSmokeProofCheckVo ready(String name, String detail) {
        return new OpenAiSmokeProofCheckVo(name, "READY", detail, "No action required.");
    }

    private static OpenAiSmokeProofCheckVo attention(String name, String detail, String nextAction) {
        return new OpenAiSmokeProofCheckVo(name, "ATTENTION", detail, nextAction);
    }

    private static OpenAiSmokeProofCheckVo blocked(String name, String detail, String nextAction) {
        return new OpenAiSmokeProofCheckVo(name, "BLOCKED", detail, nextAction);
    }

    private static void appendChecks(List<String> lines, String title, List<OpenAiSmokeProofCheckVo> checks) {
        lines.add("## " + title);
        for (OpenAiSmokeProofCheckVo check : checks) {
            lines.add("- " + check.status() + " " + check.name() + ": " + check.detail());
        }
        lines.add("");
    }

    private static String safeSummary(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value
                .replaceAll("sk-[A-Za-z0-9._-]+", "[redacted-key]")
                .replace("OPENAI_API_KEY", "[redacted-env]")
                .replace("private-demo-token", "[redacted-token]")
                .replaceAll("/Users/[^\\s,;)]*", "[redacted-local-path]")
                .replace("provider request payload", "[redacted-provider-payload]")
                .replace("raw transcript text", "[redacted-transcript]")
                .replace("raw subtitle text", "[redacted-subtitle]")
                .replaceAll("job-artifacts/[^\\s,;)]*", "[redacted-object-key]");
    }

    private static String value(Object value) {
        return value == null ? "N/A" : value.toString();
    }
}
