package com.linguaframe.media.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.common.quota.OwnerQuotaPreflightService;
import com.linguaframe.common.quota.OwnerQuotaPreflightVo;
import com.linguaframe.common.runtime.domain.enums.RuntimeProbeStatus;
import com.linguaframe.common.runtime.domain.vo.ProviderReadinessVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeDependencySummaryVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeLiveCheckSummaryVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeProbeResultVo;
import com.linguaframe.common.runtime.service.RuntimeDependencySummaryService;
import com.linguaframe.common.runtime.service.RuntimeLiveCheckService;
import com.linguaframe.demo.domain.vo.DemoRunProfileVo;
import com.linguaframe.demo.service.DemoRunProfileService;
import com.linguaframe.media.domain.vo.DemoUploadReadinessCheckVo;
import com.linguaframe.media.domain.vo.DemoUploadReadinessVo;
import com.linguaframe.media.service.DemoUploadReadinessService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DemoUploadReadinessServiceImpl implements DemoUploadReadinessService {

    private static final List<String> EVIDENCE_ROUTES = List.of(
            "/api/media/uploads/readiness",
            "/api/media/uploads/preflight",
            "/api/runtime/dependencies",
            "/api/runtime/live-checks",
            "/api/demo-session",
            "/api/demo-run-profiles",
            "/api/media/uploads/validate"
    );

    private final LinguaFrameProperties properties;
    private final RuntimeDependencySummaryService runtimeDependencySummaryService;
    private final RuntimeLiveCheckService runtimeLiveCheckService;
    private final OwnerQuotaPreflightService ownerQuotaPreflightService;
    private final DemoRunProfileService demoRunProfileService;

    public DemoUploadReadinessServiceImpl(
            LinguaFrameProperties properties,
            RuntimeDependencySummaryService runtimeDependencySummaryService,
            RuntimeLiveCheckService runtimeLiveCheckService,
            OwnerQuotaPreflightService ownerQuotaPreflightService,
            DemoRunProfileService demoRunProfileService
    ) {
        this.properties = properties;
        this.runtimeDependencySummaryService = runtimeDependencySummaryService;
        this.runtimeLiveCheckService = runtimeLiveCheckService;
        this.ownerQuotaPreflightService = ownerQuotaPreflightService;
        this.demoRunProfileService = demoRunProfileService;
    }

    @Override
    public DemoUploadReadinessVo getReadiness(String demoProfileId) {
        RuntimeDependencySummaryVo runtime = runtimeDependencySummaryService.getSummary();
        RuntimeLiveCheckSummaryVo liveChecks = runtimeLiveCheckService.check();
        OwnerQuotaPreflightVo ownerQuota = ownerQuotaPreflightService.getPreflight();
        String normalizedProfileId = demoRunProfileService.normalizeProfileId(demoProfileId);
        Optional<DemoRunProfileVo> profile = demoRunProfileService.findById(normalizedProfileId);

        List<DemoUploadReadinessCheckVo> checks = new ArrayList<>();
        checks.add(ownerSessionCheck());
        checks.add(runtimeContractCheck(runtime));
        checks.add(liveDependenciesCheck(liveChecks));
        checks.add(ownerQuotaCheck(ownerQuota));
        checks.add(demoProfileCheck(normalizedProfileId, profile));
        checks.add(paidProviderCheck(runtime, liveChecks));

        String overallStatus = overallStatus(checks);
        return new DemoUploadReadinessVo(
                overallStatus,
                ownerQuota.ownerId(),
                normalizedProfileId,
                Instant.now(),
                List.copyOf(checks),
                requiredActions(overallStatus),
                EVIDENCE_ROUTES
        );
    }

    private DemoUploadReadinessCheckVo ownerSessionCheck() {
        if (properties.getDemo().isAccessGateEnabled()) {
            return new DemoUploadReadinessCheckVo(
                    "owner-session",
                    "Owner session",
                    "READY",
                    "Private demo access gate is enabled; this readiness response is only returned after API access passes.",
                    "No owner-session action required for this authenticated API request.",
                    false
            );
        }
        return new DemoUploadReadinessCheckVo(
                "owner-session",
                "Owner session",
                "READY",
                "Demo access gate is open.",
                "No owner-session action required.",
                false
        );
    }

    private DemoUploadReadinessCheckVo runtimeContractCheck(RuntimeDependencySummaryVo runtime) {
        if (runtime.runtime().requiredRoutes().contains("/api/media/uploads")
                && runtime.runtime().requiredRoutes().contains("/api/media/uploads/preflight")) {
            return new DemoUploadReadinessCheckVo(
                    "runtime-contract",
                    "Runtime contract",
                    "READY",
                    "Backend runtime exposes upload and owner quota preflight routes.",
                    "No runtime-contract action required.",
                    false
            );
        }
        return new DemoUploadReadinessCheckVo(
                "runtime-contract",
                "Runtime contract",
                "BLOCKED",
                "Backend runtime contract is missing upload readiness routes.",
                "Rebuild and recreate the backend container from the current code.",
                true
        );
    }

    private DemoUploadReadinessCheckVo liveDependenciesCheck(RuntimeLiveCheckSummaryVo liveChecks) {
        if (liveChecks.healthy()) {
            return new DemoUploadReadinessCheckVo(
                    "live-dependencies",
                    "Live dependencies",
                    "READY",
                    "Required live dependency probes are healthy.",
                    "No dependency action required.",
                    false
            );
        }
        String down = liveChecks.checks().entrySet().stream()
                .filter(entry -> entry.getValue().status() == RuntimeProbeStatus.DOWN)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("dependency");
        return new DemoUploadReadinessCheckVo(
                "live-dependencies",
                "Live dependencies",
                "BLOCKED",
                "A required dependency probe is down: " + down + ".",
                "Run scripts/demo/private-demo-preflight.sh and fix the reported dependency.",
                true
        );
    }

    private DemoUploadReadinessCheckVo ownerQuotaCheck(OwnerQuotaPreflightVo ownerQuota) {
        if (ownerQuota.allowed()) {
            return new DemoUploadReadinessCheckVo(
                    "owner-quota",
                    "Owner quota",
                    "READY",
                    "Owner quota allows upload: active " + ownerQuota.activeJobs()
                            + ", queued " + ownerQuota.queuedJobs()
                            + ", daily cost $" + ownerQuota.dailyEstimatedCostUsd() + ".",
                    "No owner quota action required.",
                    false
            );
        }
        String detail = ownerQuota.blockingReasons().isEmpty()
                ? "Owner quota preflight blocked upload."
                : String.join(" ", ownerQuota.blockingReasons());
        return new DemoUploadReadinessCheckVo(
                "owner-quota",
                "Owner quota",
                "BLOCKED",
                detail,
                "Wait for active jobs to finish or raise the private-demo owner quota.",
                true
        );
    }

    private DemoUploadReadinessCheckVo demoProfileCheck(
            String normalizedProfileId,
            Optional<DemoRunProfileVo> profile
    ) {
        if (profile.isPresent()) {
            return new DemoUploadReadinessCheckVo(
                    "demo-profile",
                    "Demo profile",
                    "READY",
                    "Selected demo profile is available: " + normalizedProfileId + ".",
                    "No demo profile action required.",
                    false
            );
        }
        return new DemoUploadReadinessCheckVo(
                "demo-profile",
                "Demo profile",
                "BLOCKED",
                "Unknown demo profile id: " + normalizedProfileId + ".",
                "Choose one of the built-in demo profiles before upload.",
                true
        );
    }

    private DemoUploadReadinessCheckVo paidProviderCheck(
            RuntimeDependencySummaryVo runtime,
            RuntimeLiveCheckSummaryVo liveChecks
    ) {
        boolean paidProviderEnabled = runtime.readiness().providers().values().stream()
                .anyMatch(this::isPaidProviderEnabled);
        RuntimeProbeResultVo openAiCheck = liveChecks.checks().get("openai");
        if (paidProviderEnabled && openAiCheck != null && openAiCheck.status() == RuntimeProbeStatus.SKIPPED) {
            return new DemoUploadReadinessCheckVo(
                    "paid-provider-check",
                    "Paid provider check",
                    "ATTENTION",
                    "OpenAI provider mode is enabled, but the live OpenAI connectivity check is skipped.",
                    "Run the OpenAI preflight before provider-backed uploads.",
                    false
            );
        }
        return new DemoUploadReadinessCheckVo(
                "paid-provider-check",
                "Paid provider check",
                "READY",
                paidProviderEnabled
                        ? "Paid provider readiness is configured and the OpenAI probe is not skipped."
                        : "No paid provider mode is enabled.",
                "No paid provider action required.",
                false
        );
    }

    private boolean isPaidProviderEnabled(ProviderReadinessVo provider) {
        return provider.enabled() && "openai".equalsIgnoreCase(provider.provider());
    }

    private String overallStatus(List<DemoUploadReadinessCheckVo> checks) {
        if (checks.stream().anyMatch(DemoUploadReadinessCheckVo::blocking)) {
            return "BLOCKED";
        }
        if (checks.stream().anyMatch(check -> "ATTENTION".equals(check.status()))) {
            return "ATTENTION";
        }
        return "READY";
    }

    private List<String> requiredActions(String overallStatus) {
        return switch (overallStatus) {
            case "BLOCKED" -> List.of("Resolve blocking upload readiness checks before uploading media.");
            case "ATTENTION" -> List.of("Review attention checks before paid or full-video upload.");
            default -> List.of("Upload can start after file validation passes.");
        };
    }
}
