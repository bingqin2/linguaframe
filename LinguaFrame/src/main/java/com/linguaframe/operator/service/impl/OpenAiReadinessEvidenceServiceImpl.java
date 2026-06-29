package com.linguaframe.operator.service.impl;

import com.linguaframe.common.runtime.domain.enums.RuntimeProbeStatus;
import com.linguaframe.common.runtime.domain.vo.ProviderReadinessVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeDependencySummaryVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeLiveCheckSummaryVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeProbeResultVo;
import com.linguaframe.common.runtime.service.RuntimeDependencySummaryService;
import com.linguaframe.common.runtime.service.RuntimeLiveCheckService;
import com.linguaframe.media.domain.vo.DemoUploadReadinessCheckVo;
import com.linguaframe.media.domain.vo.DemoUploadReadinessVo;
import com.linguaframe.media.service.DemoUploadReadinessService;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerSummaryVo;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerVo;
import com.linguaframe.operator.domain.vo.OpenAiReadinessCommandVo;
import com.linguaframe.operator.domain.vo.OpenAiReadinessEvidenceVo;
import com.linguaframe.operator.domain.vo.OpenAiReadinessLiveCheckVo;
import com.linguaframe.operator.domain.vo.OpenAiReadinessModelUsageVo;
import com.linguaframe.operator.domain.vo.OpenAiReadinessProviderVo;
import com.linguaframe.operator.domain.vo.OpenAiReadinessSignalVo;
import com.linguaframe.operator.service.ModelUsageLedgerService;
import com.linguaframe.operator.service.OpenAiReadinessEvidenceService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class OpenAiReadinessEvidenceServiceImpl implements OpenAiReadinessEvidenceService {

    private static final List<String> PROVIDER_ORDER = List.of("transcription", "translation", "evaluation", "tts");
    private static final List<String> REQUIRED_ROUTES = List.of(
            "/api/runtime/dependencies",
            "/api/runtime/live-checks",
            "/api/media/uploads/readiness",
            "/api/operator/model-usage-ledger"
    );

    private final RuntimeDependencySummaryService summaryService;
    private final RuntimeLiveCheckService liveCheckService;
    private final DemoUploadReadinessService uploadReadinessService;
    private final ModelUsageLedgerService modelUsageLedgerService;

    public OpenAiReadinessEvidenceServiceImpl(
            RuntimeDependencySummaryService summaryService,
            RuntimeLiveCheckService liveCheckService,
            DemoUploadReadinessService uploadReadinessService,
            ModelUsageLedgerService modelUsageLedgerService
    ) {
        this.summaryService = summaryService;
        this.liveCheckService = liveCheckService;
        this.uploadReadinessService = uploadReadinessService;
        this.modelUsageLedgerService = modelUsageLedgerService;
    }

    @Override
    public OpenAiReadinessEvidenceVo getEvidence() {
        RuntimeDependencySummaryVo runtime = summaryService.getSummary();
        RuntimeLiveCheckSummaryVo liveChecks = liveCheckService.check();
        DemoUploadReadinessVo uploadReadiness = uploadReadinessService.getReadiness("tears-showcase");
        ModelUsageLedgerVo ledger = modelUsageLedgerService.ledger(20);

        List<OpenAiReadinessProviderVo> providers = providers(runtime);
        OpenAiReadinessLiveCheckVo liveCheck = liveCheck(liveChecks);
        OpenAiReadinessModelUsageVo modelUsage = modelUsage(ledger);
        List<OpenAiReadinessSignalVo> signals = signals(runtime, uploadReadiness, ledger, providers, liveCheck);
        String status = overallStatus(providers, liveCheck, signals, modelUsage);
        String phase = phase(status, providers, liveCheck);
        return new OpenAiReadinessEvidenceVo(
                Instant.now(),
                status,
                phase,
                recommendedNextAction(status, providers, liveCheck, modelUsage),
                providers,
                liveCheck,
                signals,
                modelUsage,
                commands(),
                safeLinks(),
                safetyNotes()
        );
    }

    @Override
    public String evidenceMarkdown() {
        OpenAiReadinessEvidenceVo evidence = getEvidence();
        StringBuilder markdown = new StringBuilder();
        markdown.append("# LinguaFrame OpenAI Readiness Evidence\n\n");
        markdown.append("- Status: ").append(evidence.overallStatus()).append('\n');
        markdown.append("- Phase: ").append(evidence.phase()).append('\n');
        markdown.append("- OpenAI live check: ").append(evidence.liveCheck().status())
                .append(" (").append(evidence.liveCheck().latencyMs()).append(" ms)\n");
        markdown.append("- Recent model calls: ").append(evidence.modelUsage().modelCallCount()).append('\n');
        markdown.append("- Failed model calls: ").append(evidence.modelUsage().failedModelCallCount()).append('\n');
        markdown.append("- Estimated cost USD: ").append(evidence.modelUsage().estimatedCostUsd()).append('\n');
        markdown.append("- Next action: ").append(evidence.recommendedNextAction()).append("\n\n");

        markdown.append("## Providers\n\n");
        for (OpenAiReadinessProviderVo provider : evidence.providers()) {
            markdown.append("- ").append(provider.stage())
                    .append(": ").append(provider.status())
                    .append(" provider=").append(provider.provider())
                    .append(" enabled=").append(provider.enabled())
                    .append(" credentials=").append(provider.credentialsConfigured())
                    .append(" detail=").append(provider.detail()).append('\n');
        }

        markdown.append("\n## Readiness Signals\n\n");
        for (OpenAiReadinessSignalVo signal : evidence.readinessSignals()) {
            markdown.append("- ").append(signal.label())
                    .append(": ").append(signal.status())
                    .append(" - ").append(signal.detail())
                    .append(" Next: ").append(signal.nextAction()).append('\n');
        }

        markdown.append("\n## Commands\n\n");
        for (OpenAiReadinessCommandVo command : evidence.commands()) {
            markdown.append("- `").append(command.command()).append("` - ")
                    .append(command.description()).append('\n');
        }

        markdown.append("\n## Safety Notes\n\n");
        evidence.safetyNotes().forEach(note -> markdown.append("- ").append(note).append('\n'));
        return markdown.toString();
    }

    private List<OpenAiReadinessProviderVo> providers(RuntimeDependencySummaryVo runtime) {
        Map<String, ProviderReadinessVo> rawProviders = runtime.readiness() == null
                ? Map.of()
                : runtime.readiness().providers();
        Map<String, ProviderReadinessVo> providers = rawProviders == null ? Map.of() : rawProviders;
        return PROVIDER_ORDER.stream()
                .map(stage -> provider(stage, providers.get(stage)))
                .toList();
    }

    private OpenAiReadinessProviderVo provider(String stage, ProviderReadinessVo provider) {
        if (provider == null) {
            return new OpenAiReadinessProviderVo(
                    stage,
                    false,
                    "missing",
                    null,
                    false,
                    "BLOCKED",
                    "Provider readiness is missing from runtime summary.",
                    false
            );
        }
        boolean paid = "openai".equalsIgnoreCase(provider.provider());
        String status;
        String detail;
        if (!provider.enabled()) {
            status = "SKIPPED";
            detail = "Stage is disabled.";
        } else if (paid && (!provider.credentialsConfigured() || isBlank(provider.model()))) {
            status = "BLOCKED";
            detail = "OpenAI provider is enabled but credentials or model metadata are not configured.";
        } else if (paid) {
            status = "READY";
            detail = "OpenAI provider is configured for this stage.";
        } else {
            status = "SKIPPED";
            detail = "Stage uses deterministic or non-OpenAI provider mode.";
        }
        return new OpenAiReadinessProviderVo(
                stage,
                provider.enabled(),
                safe(provider.provider()),
                safe(provider.model()),
                provider.credentialsConfigured(),
                status,
                detail,
                paid
        );
    }

    private OpenAiReadinessLiveCheckVo liveCheck(RuntimeLiveCheckSummaryVo liveChecks) {
        RuntimeProbeResultVo openai = liveChecks == null || liveChecks.checks() == null
                ? null
                : liveChecks.checks().get("openai");
        if (openai == null) {
            return new OpenAiReadinessLiveCheckVo("BLOCKED", 0L, "OpenAI live check result is missing.");
        }
        return new OpenAiReadinessLiveCheckVo(
                openai.status().name(),
                openai.latencyMs(),
                safe(openai.message())
        );
    }

    private OpenAiReadinessModelUsageVo modelUsage(ModelUsageLedgerVo ledger) {
        ModelUsageLedgerSummaryVo summary = ledger.summary();
        return new OpenAiReadinessModelUsageVo(
                summary.ledgerStatus(),
                summary.modelCallCount(),
                summary.failedModelCallCount(),
                summary.failureRatePercent(),
                summary.estimatedCostUsd(),
                summary.recommendedNextAction()
        );
    }

    private List<OpenAiReadinessSignalVo> signals(
            RuntimeDependencySummaryVo runtime,
            DemoUploadReadinessVo uploadReadiness,
            ModelUsageLedgerVo ledger,
            List<OpenAiReadinessProviderVo> providers,
            OpenAiReadinessLiveCheckVo liveCheck
    ) {
        List<OpenAiReadinessSignalVo> signals = new ArrayList<>();
        signals.add(runtimeContractSignal(runtime));
        signals.add(providerSignal(providers));
        signals.add(liveCheckSignal(liveCheck, hasPaidProvider(providers)));
        signals.add(uploadReadinessSignal(uploadReadiness));
        signals.add(modelUsageSignal(ledger));
        return signals;
    }

    private OpenAiReadinessSignalVo runtimeContractSignal(RuntimeDependencySummaryVo runtime) {
        List<String> routes = runtime.runtime() == null || runtime.runtime().requiredRoutes() == null
                ? List.of()
                : runtime.runtime().requiredRoutes();
        List<String> missing = REQUIRED_ROUTES.stream()
                .filter(route -> !routes.contains(route))
                .toList();
        if (!missing.isEmpty()) {
            return signal(
                    "RUNTIME_CONTRACT",
                    "Runtime contract",
                    "BLOCKED",
                    "Missing required OpenAI readiness routes: " + String.join(", ", missing),
                    "Restart or rebuild the backend so runtime metadata is current.",
                    true
            );
        }
        return signal(
                "RUNTIME_CONTRACT",
                "Runtime contract",
                "READY",
                "Runtime exposes the readiness, live-check, upload-readiness, and model-ledger routes.",
                "Continue to OpenAI provider checks.",
                false
        );
    }

    private OpenAiReadinessSignalVo providerSignal(List<OpenAiReadinessProviderVo> providers) {
        List<OpenAiReadinessProviderVo> paid = providers.stream()
                .filter(provider -> provider.enabled() && provider.paidProvider())
                .toList();
        if (paid.isEmpty()) {
            return signal(
                    "OPENAI_PROVIDER_MODE",
                    "OpenAI provider mode",
                    "SKIPPED",
                    "No enabled pipeline stage is configured to use OpenAI.",
                    "Use deterministic demo scripts, or switch providers in a local ignored env file before running OpenAI smoke.",
                    false
            );
        }
        List<String> blocked = paid.stream()
                .filter(provider -> "BLOCKED".equals(provider.status()))
                .map(OpenAiReadinessProviderVo::stage)
                .toList();
        if (!blocked.isEmpty()) {
            return signal(
                    "OPENAI_PROVIDER_MODE",
                    "OpenAI provider mode",
                    "BLOCKED",
                    "OpenAI provider configuration is incomplete for: " + String.join(", ", blocked),
                    "Set the missing model or credentials in a local ignored env file and restart the backend.",
                    true
            );
        }
        return signal(
                "OPENAI_PROVIDER_MODE",
                "OpenAI provider mode",
                "READY",
                "OpenAI providers are configured for: " + paid.stream()
                        .map(OpenAiReadinessProviderVo::stage)
                        .reduce((left, right) -> left + ", " + right)
                        .orElse("none"),
                "Run OpenAI preflight before provider-backed upload.",
                false
        );
    }

    private OpenAiReadinessSignalVo liveCheckSignal(OpenAiReadinessLiveCheckVo liveCheck, boolean hasPaidProvider) {
        if (!hasPaidProvider) {
            return signal(
                    "OPENAI_LIVE_CHECK",
                    "OpenAI live check",
                    "SKIPPED",
                    "OpenAI is not in the current provider path.",
                    "No OpenAI live check is required for deterministic demo mode.",
                    false
            );
        }
        String status = liveCheck.status();
        if (RuntimeProbeStatus.UP.name().equals(status)) {
            return signal(
                    "OPENAI_LIVE_CHECK",
                    "OpenAI live check",
                    "READY",
                    liveCheck.message(),
                    "Use this evidence before running the OpenAI smoke upload.",
                    false
            );
        }
        if (RuntimeProbeStatus.SKIPPED.name().equals(status)) {
            return signal(
                    "OPENAI_LIVE_CHECK",
                    "OpenAI live check",
                    "ATTENTION",
                    liveCheck.message(),
                    "Enable LINGUAFRAME_OPENAI_CONNECTIVITY_CHECK_ENABLED=true before paid demos.",
                    false
            );
        }
        return signal(
                "OPENAI_LIVE_CHECK",
                "OpenAI live check",
                "BLOCKED",
                liveCheck.message(),
                "Fix OpenAI base URL, key, model, or network reachability before upload.",
                true
        );
    }

    private OpenAiReadinessSignalVo uploadReadinessSignal(DemoUploadReadinessVo uploadReadiness) {
        String status = uploadReadiness == null ? "BLOCKED" : uploadReadiness.overallStatus();
        if ("BLOCKED".equals(status)) {
            String detail = uploadReadiness == null
                    ? "Upload readiness is unavailable."
                    : blockingDetails(uploadReadiness.checks());
            return signal(
                    "UPLOAD_READINESS",
                    "Upload readiness",
                    "BLOCKED",
                    detail,
                    "Resolve blocking upload readiness checks before uploading media.",
                    true
            );
        }
        if ("ATTENTION".equals(status)) {
            return signal(
                    "UPLOAD_READINESS",
                    "Upload readiness",
                    "ATTENTION",
                    "Upload readiness has warnings for the selected demo profile.",
                    "Review upload readiness warnings before spending provider budget.",
                    false
            );
        }
        return signal(
                "UPLOAD_READINESS",
                "Upload readiness",
                "READY",
                "Upload readiness is not blocking the selected demo profile.",
                "Continue to the OpenAI smoke runner when provider checks are ready.",
                false
        );
    }

    private String blockingDetails(List<DemoUploadReadinessCheckVo> checks) {
        if (checks == null || checks.isEmpty()) {
            return "Upload readiness is blocked.";
        }
        return checks.stream()
                .filter(DemoUploadReadinessCheckVo::blocking)
                .map(check -> check.label() + ": " + check.detail())
                .findFirst()
                .orElse("Upload readiness is blocked.");
    }

    private OpenAiReadinessSignalVo modelUsageSignal(ModelUsageLedgerVo ledger) {
        String status = ledger.summary().ledgerStatus();
        if ("BLOCKED".equals(status)) {
            return signal(
                    "MODEL_USAGE_LEDGER",
                    "Model usage ledger",
                    "BLOCKED",
                    "Recent model-call failure rate is " + ledger.summary().failureRatePercent() + "%.",
                    "Inspect failed model calls and rerun OpenAI preflight before another provider-backed demo.",
                    true
            );
        }
        if ("ATTENTION".equals(status)) {
            return signal(
                    "MODEL_USAGE_LEDGER",
                    "Model usage ledger",
                    "ATTENTION",
                    "Recent model-call failures require review before a live demo.",
                    "Review safe model-call errors before spending provider budget.",
                    false
            );
        }
        if ("EMPTY".equals(status)) {
            return signal(
                    "MODEL_USAGE_LEDGER",
                    "Model usage ledger",
                    "ATTENTION",
                    "No recent provider-backed model-call evidence exists yet.",
                    "Run the OpenAI smoke path after preflight passes.",
                    false
            );
        }
        return signal(
                "MODEL_USAGE_LEDGER",
                "Model usage ledger",
                "READY",
                "Recent model calls do not show a blocking failure rate.",
                "Use model usage ledger as cost and latency evidence.",
                false
        );
    }

    private String overallStatus(
            List<OpenAiReadinessProviderVo> providers,
            OpenAiReadinessLiveCheckVo liveCheck,
            List<OpenAiReadinessSignalVo> signals,
            OpenAiReadinessModelUsageVo modelUsage
    ) {
        if (!hasPaidProvider(providers)) {
            return "SKIPPED";
        }
        if (signals.stream().anyMatch(OpenAiReadinessSignalVo::blocking)) {
            return "BLOCKED";
        }
        if (!RuntimeProbeStatus.UP.name().equals(liveCheck.status())
                || "ATTENTION".equals(modelUsage.ledgerStatus())
                || "EMPTY".equals(modelUsage.ledgerStatus())
                || signals.stream().anyMatch(signal -> "ATTENTION".equals(signal.status()))) {
            return "ATTENTION";
        }
        return "READY";
    }

    private String phase(
            String status,
            List<OpenAiReadinessProviderVo> providers,
            OpenAiReadinessLiveCheckVo liveCheck
    ) {
        if ("SKIPPED".equals(status)) {
            return "DETERMINISTIC_DEMO_MODE";
        }
        if ("BLOCKED".equals(status)) {
            return "BLOCKED_BEFORE_PROVIDER_UPLOAD";
        }
        if (!RuntimeProbeStatus.UP.name().equals(liveCheck.status())) {
            return "NEEDS_CONNECTIVITY_PROOF";
        }
        return providers.stream().anyMatch(provider -> "READY".equals(provider.status()))
                ? "READY_FOR_OPENAI_SMOKE"
                : "NEEDS_PROVIDER_CONFIGURATION";
    }

    private String recommendedNextAction(
            String status,
            List<OpenAiReadinessProviderVo> providers,
            OpenAiReadinessLiveCheckVo liveCheck,
            OpenAiReadinessModelUsageVo modelUsage
    ) {
        return switch (status) {
            case "SKIPPED" -> "Use deterministic demo scripts, or configure OpenAI providers in an ignored env file before paid smoke.";
            case "BLOCKED" -> "Resolve blocking OpenAI readiness checks before uploading media or running provider-backed demos.";
            case "ATTENTION" -> RuntimeProbeStatus.SKIPPED.name().equals(liveCheck.status())
                    ? "Enable the OpenAI connectivity check and rerun preflight before spending provider budget."
                    : "Review warnings and recent model-call evidence before running the OpenAI smoke upload.";
            default -> "Run scripts/demo/openai-demo-preflight.sh, then scripts/demo/docker-e2e-openai-smoke.sh with a short speech sample.";
        };
    }

    private List<OpenAiReadinessCommandVo> commands() {
        return List.of(
                new OpenAiReadinessCommandVo(
                        "OpenAI preflight",
                        "scripts/demo/openai-demo-preflight.sh",
                        "Validate local ignored OpenAI demo env, runtime readiness, and the OpenAI live check."
                ),
                new OpenAiReadinessCommandVo(
                        "OpenAI smoke runner",
                        "LINGUAFRAME_ENV_FILE=.env.openai-demo LINGUAFRAME_DEMO_SAMPLE_PATH=<short-speech.mp4> scripts/demo/docker-e2e-openai-smoke.sh",
                        "Run the paid provider-backed smoke path only after readiness is acceptable."
                ),
                new OpenAiReadinessCommandVo(
                        "Upload readiness",
                        "scripts/demo/upload-readiness.sh",
                        "Check upload gates, quota, live dependencies, and provider warning posture."
                ),
                new OpenAiReadinessCommandVo(
                        "Model usage ledger",
                        "scripts/demo/model-usage-ledger.sh",
                        "Review recent model-call cost, latency, failure, and cache evidence."
                )
        );
    }

    private List<String> safeLinks() {
        return List.of(
                "/api/operator/openai-readiness-evidence",
                "/api/operator/openai-readiness-evidence/markdown/download",
                "/api/runtime/dependencies",
                "/api/runtime/live-checks",
                "/api/media/uploads/readiness",
                "/api/operator/model-usage-ledger"
        );
    }

    private List<String> safetyNotes() {
        return List.of(
                "Readiness evidence is metadata-only and does not upload media or create jobs.",
                "The OpenAI live-check signal comes from the existing bounded runtime probe.",
                "API keys, bearer tokens, demo tokens, provider payloads, object keys, local paths, transcript text, subtitle text, and media bytes are intentionally excluded."
        );
    }

    private boolean hasPaidProvider(List<OpenAiReadinessProviderVo> providers) {
        return providers.stream().anyMatch(provider -> provider.enabled() && provider.paidProvider());
    }

    private OpenAiReadinessSignalVo signal(
            String id,
            String label,
            String status,
            String detail,
            String nextAction,
            boolean blocking
    ) {
        return new OpenAiReadinessSignalVo(id, label, status, safe(detail), safe(nextAction), blocking);
    }

    private String safe(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer [redacted]")
                .replaceAll("sk-[A-Za-z0-9._-]+", "sk-[redacted]");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
