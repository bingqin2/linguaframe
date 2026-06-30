package com.linguaframe.operator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateVo;
import com.linguaframe.job.domain.vo.DemoRunMonitorVo;
import com.linguaframe.job.domain.vo.LocalizationJobSummaryVo;
import com.linguaframe.job.domain.vo.StuckJobRecoveryActionVo;
import com.linguaframe.job.domain.vo.StuckJobRecoveryVo;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.DemoAcceptanceGateService;
import com.linguaframe.job.service.DemoRunMonitorService;
import com.linguaframe.job.service.StuckJobRecoveryService;
import com.linguaframe.operator.domain.vo.DemoSessionRecoveryBoardVo;
import com.linguaframe.operator.service.impl.DemoSessionRecoveryBoardServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DemoSessionRecoveryBoardServiceTests {

    private final StubLocalizationJobRepository jobRepository = new StubLocalizationJobRepository();
    private final StubDemoRunMonitorService monitorService = new StubDemoRunMonitorService();
    private final StubStuckJobRecoveryService stuckJobRecoveryService = new StubStuckJobRecoveryService();
    private final StubDemoAcceptanceGateService acceptanceGateService = new StubDemoAcceptanceGateService();

    private final DemoSessionRecoveryBoardService service = new DemoSessionRecoveryBoardServiceImpl(
            jobRepository,
            monitorService,
            stuckJobRecoveryService,
            acceptanceGateService
    );

    @Test
    void groupsRecentJobsByRecoveryClassificationAndLinksToExistingSurfaces() {
        jobRepository.summaries = List.of(
                summary("job-stale", LocalizationJobStatus.QUEUED, "tears-showcase"),
                summary("job-active", LocalizationJobStatus.PROCESSING, "tears-showcase"),
                summary("job-failed", LocalizationJobStatus.FAILED, "tears-showcase"),
                summary("job-review", LocalizationJobStatus.COMPLETED, "tears-showcase"),
                summary("job-ready", LocalizationJobStatus.COMPLETED, "tears-showcase"),
                summary("job-cancelled", LocalizationJobStatus.CANCELLED, "tears-showcase")
        );
        stuckJobRecoveryService.recoveries.put("job-stale", recovery("job-stale", "BLOCKED", "QUEUED_STALE_DISPATCH", true));
        stuckJobRecoveryService.recoveries.put("job-active", recovery("job-active", "WATCH", "PROCESSING_STALE_STAGE", false));
        stuckJobRecoveryService.recoveries.put("job-failed", recovery("job-failed", "BLOCKED", "FAILED_RETRYABLE", true));
        acceptanceGateService.gates.put("job-review", gate("job-review", "BLOCKED"));
        acceptanceGateService.gates.put("job-ready", gate("job-ready", "READY"));

        DemoSessionRecoveryBoardVo board = service.board(20);

        assertThat(board.overallStatus()).isEqualTo("BLOCKED");
        assertThat(board.recoverNowCount()).isEqualTo(2);
        assertThat(board.watchCount()).isEqualTo(1);
        assertThat(board.needsReviewCount()).isEqualTo(1);
        assertThat(board.readyCount()).isEqualTo(1);
        assertThat(board.noActionCount()).isEqualTo(1);
        assertThat(board.jobs()).extracting("jobId")
                .containsExactly("job-stale", "job-failed", "job-review", "job-active", "job-ready", "job-cancelled");
        assertThat(board.jobs().get(0).classification()).isEqualTo("RECOVER_NOW");
        assertThat(board.jobs().get(0).recommendedNextAction()).contains("requeue");
        assertThat(board.jobs().get(0).links()).extracting("href")
                .contains("/api/jobs/job-stale/stuck-job-recovery");
        assertThat(board.jobs().get(2).classification()).isEqualTo("NEEDS_REVIEW");
        assertThat(board.jobs().get(2).links()).extracting("href")
                .contains("/api/jobs/job-review/demo-acceptance-gate");
        assertThat(board.jobs().get(4).classification()).isEqualTo("READY_TO_PRESENT");
        assertThat(board.primaryAction().href()).isEqualTo("/api/jobs/job-stale/stuck-job-recovery");
        assertThat(board.safetyNotes()).anyMatch(note -> note.contains("metadata-only"));
    }

    @Test
    void rendersMarkdownAndDoesNotExposeUnsafeContent() throws Exception {
        jobRepository.summaries = List.of(summary("job-stale", LocalizationJobStatus.QUEUED, "tears-showcase"));
        stuckJobRecoveryService.recoveries.put("job-stale", recovery("job-stale", "BLOCKED", "QUEUED_STALE_DISPATCH", true));

        DemoSessionRecoveryBoardVo board = service.board(20);
        String markdown = service.boardMarkdown(20);
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(board);

        assertThat(markdown)
                .contains("LinguaFrame Demo Session Recovery Board")
                .contains("job-stale")
                .contains("RECOVER_NOW")
                .contains("/api/jobs/job-stale/stuck-job-recovery");
        assertThat(json)
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("Bearer ")
                .doesNotContain("source-videos/")
                .doesNotContain("job-artifacts/")
                .doesNotContain("/Users/")
                .doesNotContain("provider payload")
                .doesNotContain("raw transcript text")
                .doesNotContain("raw subtitle text")
                .doesNotContain("reviewer note body");
    }

    private static LocalizationJobSummaryVo summary(String jobId, LocalizationJobStatus status, String demoProfileId) {
        return new LocalizationJobSummaryVo(
                jobId,
                "video-" + jobId,
                jobId + ".mp4",
                "zh-CN",
                "alloy",
                "NATURAL",
                "STANDARD",
                0,
                "",
                "OFF",
                demoProfileId,
                status,
                Instant.parse("2026-06-30T10:00:00Z"),
                status == LocalizationJobStatus.QUEUED ? null : Instant.parse("2026-06-30T10:01:00Z"),
                status == LocalizationJobStatus.COMPLETED || status == LocalizationJobStatus.CANCELLED ? Instant.parse("2026-06-30T10:05:00Z") : null,
                status == LocalizationJobStatus.FAILED ? Instant.parse("2026-06-30T10:05:00Z") : null,
                status == LocalizationJobStatus.FAILED ? LocalizationJobStage.TARGET_SUBTITLE_EXPORT : null,
                status == LocalizationJobStatus.FAILED ? "safe failure summary" : null,
                status == LocalizationJobStatus.FAILED ? 1 : 0,
                new BigDecimal("0.01000000")
        );
    }

    private static StuckJobRecoveryVo recovery(String jobId, String status, String classification, boolean enabledAction) {
        return new StuckJobRecoveryVo(
                jobId,
                "video-" + jobId,
                Instant.parse("2026-06-30T10:10:00Z"),
                status,
                status,
                classification,
                "Safe recovery headline.",
                enabledAction ? "Open stuck-job recovery and requeue or retry after confirming readiness." : "Keep watching this active run.",
                LocalizationJobStatus.QUEUED,
                JobDispatchEventStatus.PENDING,
                0,
                null,
                null,
                600,
                600,
                List.of(),
                List.of(new StuckJobRecoveryActionVo("REQUEUE_DISPATCH", "Requeue dispatch", "POST", "/api/jobs/" + jobId + "/stuck-job-recovery/actions", enabledAction, true, "Requeue safely.")),
                List.of(),
                List.of("Metadata only."),
                "# Recovery"
        );
    }

    private static DemoAcceptanceGateVo gate(String jobId, String status) {
        return new DemoAcceptanceGateVo(
                jobId,
                "video-" + jobId,
                Instant.parse("2026-06-30T10:10:00Z"),
                status,
                LocalizationJobStatus.COMPLETED,
                "zh-CN",
                "tears-showcase",
                status.equals("READY") ? "Ready." : "Blocked.",
                "Safe acceptance summary.",
                status.equals("READY") ? "Present this run." : "Resolve acceptance blockers.",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("Metadata only.")
        );
    }

    private static DemoRunMonitorVo monitor(LocalizationJobSummaryVo summary) {
        return new DemoRunMonitorVo(
                summary.jobId(),
                summary.videoId(),
                summary.status(),
                JobDispatchEventStatus.PENDING,
                Instant.parse("2026-06-30T10:10:00Z"),
                summary.startedAt() == null ? null : 120000L,
                summary.status() == LocalizationJobStatus.PROCESSING ? LocalizationJobStage.TARGET_SUBTITLE_EXPORT : null,
                summary.status() == LocalizationJobStatus.COMPLETED ? 12 : 1,
                12,
                summary.status() == LocalizationJobStatus.FAILED ? 1 : 0,
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                120000L,
                summary.status() == LocalizationJobStatus.PROCESSING ? "WATCH" : "READY",
                "Safe monitor summary.",
                "Monitor this run.",
                List.of(),
                List.of(),
                "# Monitor"
        );
    }

    private static final class StubLocalizationJobRepository extends LocalizationJobRepository {
        private List<LocalizationJobSummaryVo> summaries = List.of();

        private StubLocalizationJobRepository() {
            super(null);
        }

        @Override
        public List<LocalizationJobSummaryVo> findSummaries(LocalizationJobStatus status, int limit, int offset) {
            return summaries.stream().limit(limit).toList();
        }
    }

    private static final class StubDemoRunMonitorService implements DemoRunMonitorService {
        @Override
        public DemoRunMonitorVo buildMonitor(String jobId) {
            return monitor(summary(jobId, LocalizationJobStatus.PROCESSING, "tears-showcase"));
        }

        @Override
        public String buildMarkdownMonitor(String jobId) {
            return "# Monitor";
        }
    }

    private static final class StubStuckJobRecoveryService implements StuckJobRecoveryService {
        private final Map<String, StuckJobRecoveryVo> recoveries = new HashMap<>();

        @Override
        public StuckJobRecoveryVo recovery(String jobId) {
            return recoveries.get(jobId);
        }

        @Override
        public String recoveryMarkdown(String jobId) {
            return "# Recovery";
        }

        @Override
        public StuckJobRecoveryVo runAction(String jobId, String actionId, String confirmation) {
            return recovery(jobId);
        }
    }

    private static final class StubDemoAcceptanceGateService implements DemoAcceptanceGateService {
        private final Map<String, DemoAcceptanceGateVo> gates = new HashMap<>();

        @Override
        public DemoAcceptanceGateVo buildGate(String jobId) {
            return gates.get(jobId);
        }
    }
}
