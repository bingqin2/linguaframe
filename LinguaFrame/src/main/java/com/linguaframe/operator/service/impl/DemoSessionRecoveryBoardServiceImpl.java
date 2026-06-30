package com.linguaframe.operator.service.impl;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateVo;
import com.linguaframe.job.domain.vo.DemoRunMonitorVo;
import com.linguaframe.job.domain.vo.LocalizationJobSummaryVo;
import com.linguaframe.job.domain.vo.StuckJobRecoveryVo;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.DemoAcceptanceGateService;
import com.linguaframe.job.service.DemoRunMonitorService;
import com.linguaframe.job.service.StuckJobRecoveryService;
import com.linguaframe.operator.domain.vo.DemoSessionRecoveryBoardActionVo;
import com.linguaframe.operator.domain.vo.DemoSessionRecoveryBoardCheckVo;
import com.linguaframe.operator.domain.vo.DemoSessionRecoveryBoardJobVo;
import com.linguaframe.operator.domain.vo.DemoSessionRecoveryBoardLinkVo;
import com.linguaframe.operator.domain.vo.DemoSessionRecoveryBoardVo;
import com.linguaframe.operator.service.DemoSessionRecoveryBoardService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class DemoSessionRecoveryBoardServiceImpl implements DemoSessionRecoveryBoardService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final String READY = "READY";
    private static final String ATTENTION = "ATTENTION";
    private static final String BLOCKED = "BLOCKED";

    private final LocalizationJobRepository jobRepository;
    private final DemoRunMonitorService monitorService;
    private final StuckJobRecoveryService stuckJobRecoveryService;
    private final DemoAcceptanceGateService acceptanceGateService;

    public DemoSessionRecoveryBoardServiceImpl(
            LocalizationJobRepository jobRepository,
            DemoRunMonitorService monitorService,
            StuckJobRecoveryService stuckJobRecoveryService,
            DemoAcceptanceGateService acceptanceGateService
    ) {
        this.jobRepository = jobRepository;
        this.monitorService = monitorService;
        this.stuckJobRecoveryService = stuckJobRecoveryService;
        this.acceptanceGateService = acceptanceGateService;
    }

    @Override
    public DemoSessionRecoveryBoardVo board(Integer limit) {
        int resolvedLimit = resolvedLimit(limit);
        List<LocalizationJobSummaryVo> summaries = jobRepository.findSummaries(null, resolvedLimit, 0);
        List<DemoSessionRecoveryBoardJobVo> jobs = summaries.stream()
                .map(this::job)
                .sorted(Comparator.comparingInt((DemoSessionRecoveryBoardJobVo item) -> classificationRank(item.classification()))
                        .thenComparing(DemoSessionRecoveryBoardJobVo::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        int recoverNow = count(jobs, "RECOVER_NOW");
        int watch = count(jobs, "WATCH");
        int ready = count(jobs, "READY_TO_PRESENT");
        int needsReview = count(jobs, "NEEDS_REVIEW");
        int noAction = count(jobs, "NO_ACTION");
        String overall = recoverNow > 0 ? BLOCKED : needsReview > 0 || watch > 0 ? ATTENTION : jobs.isEmpty() ? "EMPTY" : READY;
        DemoSessionRecoveryBoardActionVo primaryAction = jobs.stream()
                .flatMap(job -> job.actions().stream())
                .filter(DemoSessionRecoveryBoardActionVo::primary)
                .findFirst()
                .orElse(null);
        String headline = headline(overall, recoverNow, needsReview, watch, ready);
        String nextAction = nextAction(overall, primaryAction, recoverNow, needsReview, watch);
        List<DemoSessionRecoveryBoardCheckVo> checks = checks(recoverNow, needsReview, watch, ready, noAction);
        List<DemoSessionRecoveryBoardLinkVo> links = boardLinks();
        List<String> safetyNotes = safetyNotes();
        String markdown = markdown(overall, headline, nextAction, recoverNow, watch, ready, needsReview, noAction, jobs, checks, links, safetyNotes);
        return new DemoSessionRecoveryBoardVo(
                Instant.now(),
                overall,
                headline,
                nextAction,
                recoverNow,
                watch,
                ready,
                needsReview,
                noAction,
                primaryAction,
                jobs,
                checks,
                links,
                safetyNotes,
                markdown
        );
    }

    @Override
    public String boardMarkdown(Integer limit) {
        return board(limit).markdown();
    }

    private DemoSessionRecoveryBoardJobVo job(LocalizationJobSummaryVo summary) {
        DemoRunMonitorVo monitor = monitor(summary);
        StuckJobRecoveryVo recovery = recovery(summary);
        DemoAcceptanceGateVo gate = gate(summary);
        String classification = classification(summary, recovery, gate);
        String attention = attention(classification);
        String nextAction = recommendedNextAction(summary, classification, recovery, gate, monitor);
        return new DemoSessionRecoveryBoardJobVo(
                summary.jobId(),
                summary.videoId(),
                safe(summary.filename()),
                safe(summary.demoProfileId()),
                summary.status().name(),
                monitor == null || monitor.currentStage() == null ? null : monitor.currentStage().name(),
                monitor == null ? null : monitor.elapsedMs(),
                summary.createdAt(),
                latest(summary),
                classification,
                attention,
                recovery == null ? null : recovery.classification(),
                gate == null ? null : gate.gateStatus(),
                nextAction,
                actions(summary, classification),
                links(summary, classification)
        );
    }

    private String classification(LocalizationJobSummaryVo summary, StuckJobRecoveryVo recovery, DemoAcceptanceGateVo gate) {
        if (summary.status() == LocalizationJobStatus.QUEUED
                || summary.status() == LocalizationJobStatus.RETRYING
                || summary.status() == LocalizationJobStatus.PROCESSING
                || summary.status() == LocalizationJobStatus.FAILED) {
            if (recovery != null && ("BLOCKED".equals(recovery.status()) || hasEnabledAction(recovery))) {
                return "RECOVER_NOW";
            }
            return summary.status() == LocalizationJobStatus.FAILED ? "RECOVER_NOW" : "WATCH";
        }
        if (summary.status() == LocalizationJobStatus.COMPLETED) {
            if (gate != null && !"READY".equals(gate.gateStatus())) {
                return "NEEDS_REVIEW";
            }
            return "READY_TO_PRESENT";
        }
        return "NO_ACTION";
    }

    private List<DemoSessionRecoveryBoardActionVo> actions(LocalizationJobSummaryVo summary, String classification) {
        List<DemoSessionRecoveryBoardActionVo> actions = new ArrayList<>();
        if ("RECOVER_NOW".equals(classification)) {
            actions.add(new DemoSessionRecoveryBoardActionVo(
                    "OPEN_STUCK_RECOVERY",
                    "Open stuck-job recovery",
                    "/api/jobs/" + summary.jobId() + "/stuck-job-recovery",
                    "Inspect per-job recovery checks and run explicit confirmed actions when appropriate.",
                    true
            ));
            if (summary.status() == LocalizationJobStatus.FAILED) {
                actions.add(new DemoSessionRecoveryBoardActionVo(
                        "OPEN_FAILURE_TRIAGE",
                        "Open failure triage",
                        "/api/jobs/" + summary.jobId(),
                        "Open job detail and failure triage evidence.",
                        false
                ));
            }
        } else if ("NEEDS_REVIEW".equals(classification)) {
            actions.add(new DemoSessionRecoveryBoardActionVo(
                    "OPEN_ACCEPTANCE_GATE",
                    "Open acceptance gate",
                    "/api/jobs/" + summary.jobId() + "/demo-acceptance-gate",
                    "Inspect blocked acceptance checks and recovery runbook.",
                    true
            ));
            actions.add(new DemoSessionRecoveryBoardActionVo(
                    "OPEN_NARRATION_RECOVERY",
                    "Open narration recovery handoff",
                    "/api/jobs/" + summary.jobId() + "/narration-recovery-handoff",
                    "Inspect narration recovery handoff when acceptance is blocked by narration review.",
                    false
            ));
        } else if ("READY_TO_PRESENT".equals(classification)) {
            actions.add(new DemoSessionRecoveryBoardActionVo(
                    "OPEN_COMPLETION_CERTIFICATE",
                    "Open completion certificate",
                    "/api/jobs/" + summary.jobId() + "/demo-completion-certificate",
                    "Inspect final proof of completion.",
                    true
            ));
        } else if ("WATCH".equals(classification)) {
            actions.add(new DemoSessionRecoveryBoardActionVo(
                    "OPEN_RUN_MONITOR",
                    "Open run monitor",
                    "/api/jobs/" + summary.jobId() + "/demo-run-monitor",
                    "Watch current stage progress.",
                    true
            ));
        }
        return List.copyOf(actions);
    }

    private List<DemoSessionRecoveryBoardLinkVo> links(LocalizationJobSummaryVo summary, String classification) {
        List<DemoSessionRecoveryBoardLinkVo> links = new ArrayList<>();
        links.add(new DemoSessionRecoveryBoardLinkVo("JOB", "Job detail", "/api/jobs/" + summary.jobId(), "application/json", "Selected job detail."));
        links.add(new DemoSessionRecoveryBoardLinkVo("RUN_MONITOR", "Demo run monitor", "/api/jobs/" + summary.jobId() + "/demo-run-monitor", "application/json", "Current stage monitor."));
        if ("RECOVER_NOW".equals(classification) || "WATCH".equals(classification)) {
            links.add(new DemoSessionRecoveryBoardLinkVo("STUCK_RECOVERY", "Stuck job recovery", "/api/jobs/" + summary.jobId() + "/stuck-job-recovery", "application/json", "Per-job recovery cockpit."));
        }
        if ("NEEDS_REVIEW".equals(classification) || "READY_TO_PRESENT".equals(classification)) {
            links.add(new DemoSessionRecoveryBoardLinkVo("ACCEPTANCE_GATE", "Acceptance gate", "/api/jobs/" + summary.jobId() + "/demo-acceptance-gate", "application/json", "Final readiness gate."));
        }
        if ("READY_TO_PRESENT".equals(classification)) {
            links.add(new DemoSessionRecoveryBoardLinkVo("DEMO_PACKAGE", "Demo run package", "/api/jobs/" + summary.jobId() + "/demo-run-package/download", "application/zip", "Safe run package."));
        }
        return List.copyOf(links);
    }

    private List<DemoSessionRecoveryBoardCheckVo> checks(int recoverNow, int needsReview, int watch, int ready, int noAction) {
        return List.of(
                new DemoSessionRecoveryBoardCheckVo("recover-now", "Recover now", recoverNow > 0 ? BLOCKED : READY, recoverNow + " job(s) need recovery.", recoverNow > 0 ? "Open the first recovery row." : "No immediate recovery rows.", recoverNow > 0),
                new DemoSessionRecoveryBoardCheckVo("needs-review", "Needs review", needsReview > 0 ? ATTENTION : READY, needsReview + " completed job(s) need review.", needsReview > 0 ? "Open acceptance or narration recovery evidence." : "No blocked completed runs.", false),
                new DemoSessionRecoveryBoardCheckVo("watch", "Watch active", watch > 0 ? ATTENTION : READY, watch + " job(s) are active and within expected monitoring.", watch > 0 ? "Keep run monitor open." : "No active watch rows.", false),
                new DemoSessionRecoveryBoardCheckVo("ready", "Ready", READY, ready + " completed job(s) are ready to present.", ready > 0 ? "Use completion and handoff evidence." : "Complete a run for presentation evidence.", false),
                new DemoSessionRecoveryBoardCheckVo("no-action", "No action", READY, noAction + " job(s) have no session recovery action.", "Keep for audit only.", false)
        );
    }

    private List<DemoSessionRecoveryBoardLinkVo> boardLinks() {
        return List.of(
                new DemoSessionRecoveryBoardLinkVo("COMMAND_CENTER", "Demo session command center", "/api/operator/demo-session-command-center", "application/json", "Run-day command center."),
                new DemoSessionRecoveryBoardLinkVo("LAUNCHER", "Demo run launcher", "/api/operator/demo-run-launcher", "application/json", "Start the next demo run."),
                new DemoSessionRecoveryBoardLinkVo("MARKDOWN", "Recovery board Markdown", "/api/operator/demo-session-recovery-board/markdown/download", "text/markdown", "Downloadable board report.")
        );
    }

    private String markdown(
            String overall,
            String headline,
            String nextAction,
            int recoverNow,
            int watch,
            int ready,
            int needsReview,
            int noAction,
            List<DemoSessionRecoveryBoardJobVo> jobs,
            List<DemoSessionRecoveryBoardCheckVo> checks,
            List<DemoSessionRecoveryBoardLinkVo> links,
            List<String> safetyNotes
    ) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# LinguaFrame Demo Session Recovery Board\n\n");
        markdown.append("- Overall: ").append(overall).append('\n');
        markdown.append("- Headline: ").append(headline).append('\n');
        markdown.append("- Next action: ").append(nextAction).append('\n');
        markdown.append("- Recover now: ").append(recoverNow).append('\n');
        markdown.append("- Watch: ").append(watch).append('\n');
        markdown.append("- Ready: ").append(ready).append('\n');
        markdown.append("- Needs review: ").append(needsReview).append('\n');
        markdown.append("- No action: ").append(noAction).append("\n\n");
        markdown.append("## Jobs\n\n");
        if (jobs.isEmpty()) {
            markdown.append("- No recent jobs. Open the demo run launcher.\n");
        } else {
            for (DemoSessionRecoveryBoardJobVo job : jobs) {
                markdown.append("- ").append(job.classification()).append(" ").append(job.jobId()).append(" ")
                        .append(job.status()).append(": ").append(job.recommendedNextAction()).append('\n');
                for (DemoSessionRecoveryBoardLinkVo link : job.links()) {
                    markdown.append("  - ").append(link.label()).append(": ").append(link.href()).append('\n');
                }
            }
        }
        markdown.append("\n## Checks\n\n");
        for (DemoSessionRecoveryBoardCheckVo check : checks) {
            markdown.append("- ").append(check.status()).append(" ").append(check.label()).append(": ")
                    .append(check.detail()).append('\n');
        }
        markdown.append("\n## Board Links\n\n");
        for (DemoSessionRecoveryBoardLinkVo link : links) {
            markdown.append("- ").append(link.label()).append(": ").append(link.href()).append('\n');
        }
        markdown.append("\n## Safety Notes\n\n");
        safetyNotes.forEach(note -> markdown.append("- ").append(note).append('\n'));
        return markdown.toString();
    }

    private String headline(String overall, int recoverNow, int needsReview, int watch, int ready) {
        if (BLOCKED.equals(overall)) {
            return recoverNow + " job(s) need explicit recovery before the session is clean.";
        }
        if (ATTENTION.equals(overall)) {
            return needsReview + " job(s) need review and " + watch + " job(s) are still running.";
        }
        if (ready > 0) {
            return ready + " completed job(s) are ready for presentation evidence.";
        }
        return "No recent demo job requires recovery.";
    }

    private String nextAction(String overall, DemoSessionRecoveryBoardActionVo primaryAction, int recoverNow, int needsReview, int watch) {
        if (primaryAction != null) {
            return primaryAction.description();
        }
        if (recoverNow > 0) {
            return "Open the first recover-now row.";
        }
        if (needsReview > 0) {
            return "Resolve completed-run acceptance or narration review blockers.";
        }
        if (watch > 0) {
            return "Keep monitoring the active run.";
        }
        return "Use the demo run launcher for the next run or present the ready completed run.";
    }

    private DemoRunMonitorVo monitor(LocalizationJobSummaryVo summary) {
        try {
            return monitorService.buildMonitor(summary.jobId());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private StuckJobRecoveryVo recovery(LocalizationJobSummaryVo summary) {
        if (summary.status() == LocalizationJobStatus.COMPLETED || summary.status() == LocalizationJobStatus.CANCELLED) {
            return null;
        }
        try {
            return stuckJobRecoveryService.recovery(summary.jobId());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private DemoAcceptanceGateVo gate(LocalizationJobSummaryVo summary) {
        if (summary.status() != LocalizationJobStatus.COMPLETED) {
            return null;
        }
        try {
            return acceptanceGateService.buildGate(summary.jobId());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean hasEnabledAction(StuckJobRecoveryVo recovery) {
        return recovery.actions().stream().anyMatch(action -> action.enabled());
    }

    private static int resolvedLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(MAX_LIMIT, limit));
    }

    private static int count(List<DemoSessionRecoveryBoardJobVo> jobs, String classification) {
        return (int) jobs.stream().filter(job -> classification.equals(job.classification())).count();
    }

    private static int classificationRank(String classification) {
        return switch (classification) {
            case "RECOVER_NOW" -> 0;
            case "NEEDS_REVIEW" -> 1;
            case "WATCH" -> 2;
            case "READY_TO_PRESENT" -> 3;
            default -> 4;
        };
    }

    private static String attention(String classification) {
        return switch (classification) {
            case "RECOVER_NOW" -> BLOCKED;
            case "NEEDS_REVIEW", "WATCH" -> ATTENTION;
            default -> READY;
        };
    }

    private static String recommendedNextAction(
            LocalizationJobSummaryVo summary,
            String classification,
            StuckJobRecoveryVo recovery,
            DemoAcceptanceGateVo gate,
            DemoRunMonitorVo monitor
    ) {
        if ("RECOVER_NOW".equals(classification)) {
            if (recovery != null) {
                return recovery.recommendedNextAction();
            }
            return "Open stuck-job recovery for " + summary.jobId() + ".";
        }
        if ("NEEDS_REVIEW".equals(classification)) {
            return gate == null ? "Open acceptance gate for this completed run." : gate.recommendedNextAction();
        }
        if ("WATCH".equals(classification)) {
            return monitor == null ? "Open run monitor for this active job." : monitor.recommendedNextAction();
        }
        if ("READY_TO_PRESENT".equals(classification)) {
            return "Use completion certificate and handoff package for presentation.";
        }
        return "No recovery action is recommended for this job.";
    }

    private static Instant latest(LocalizationJobSummaryVo summary) {
        if (summary.completedAt() != null) {
            return summary.completedAt();
        }
        if (summary.failedAt() != null) {
            return summary.failedAt();
        }
        if (summary.startedAt() != null) {
            return summary.startedAt();
        }
        return summary.createdAt();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static List<String> safetyNotes() {
        return List.of(
                "Demo session recovery board is metadata-only and read-only.",
                "It does not execute recovery actions automatically.",
                "It excludes media bytes, object keys, local paths, transcripts, subtitles, narration text, reviewer note bodies, external request or response bodies, API keys, bearer tokens, demo tokens, and credentials."
        );
    }
}
