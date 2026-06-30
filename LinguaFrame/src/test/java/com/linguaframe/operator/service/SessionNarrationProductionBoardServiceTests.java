package com.linguaframe.operator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateVo;
import com.linguaframe.job.domain.vo.LocalizationJobSummaryVo;
import com.linguaframe.job.domain.vo.NarrationDeliveryPackageVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewResolutionVo;
import com.linguaframe.job.domain.vo.NarrationRenderReviewVo;
import com.linguaframe.job.domain.vo.NarrationSceneBoardVo;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.DemoAcceptanceGateService;
import com.linguaframe.job.service.NarrationDeliveryPackageService;
import com.linguaframe.job.service.NarrationPlaybackReviewResolutionService;
import com.linguaframe.job.service.NarrationRenderReviewService;
import com.linguaframe.job.service.NarrationSceneBoardService;
import com.linguaframe.operator.domain.vo.SessionNarrationProductionBoardVo;
import com.linguaframe.operator.service.impl.SessionNarrationProductionBoardServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionNarrationProductionBoardServiceTests {

    private final StubLocalizationJobRepository jobRepository = new StubLocalizationJobRepository();
    private final StubNarrationSceneBoardService sceneBoardService = new StubNarrationSceneBoardService();
    private final StubNarrationRenderReviewService renderReviewService = new StubNarrationRenderReviewService();
    private final StubNarrationPlaybackReviewResolutionService playbackResolutionService = new StubNarrationPlaybackReviewResolutionService();
    private final StubNarrationDeliveryPackageService deliveryPackageService = new StubNarrationDeliveryPackageService();
    private final StubDemoAcceptanceGateService acceptanceGateService = new StubDemoAcceptanceGateService();

    private final SessionNarrationProductionBoardService service = new SessionNarrationProductionBoardServiceImpl(
            jobRepository,
            sceneBoardService,
            renderReviewService,
            playbackResolutionService,
            deliveryPackageService,
            acceptanceGateService
    );

    @Test
    void groupsRecentJobsByNarrationProductionReadiness() {
        jobRepository.summaries = List.of(
                summary("job-ready", LocalizationJobStatus.COMPLETED),
                summary("job-review", LocalizationJobStatus.COMPLETED),
                summary("job-render", LocalizationJobStatus.COMPLETED),
                summary("job-author", LocalizationJobStatus.COMPLETED),
                summary("job-blocked", LocalizationJobStatus.COMPLETED),
                summary("job-queued", LocalizationJobStatus.QUEUED)
        );
        sceneBoardService.boards.put("job-ready", sceneBoard("job-ready", "READY", 4, true, true));
        renderReviewService.reviews.put("job-ready", renderReview("job-ready", "READY", true, true));
        playbackResolutionService.resolutions.put("job-ready", resolution("job-ready", "READY", 0, true, true));
        deliveryPackageService.packages.put("job-ready", delivery("job-ready", "READY", true, true, 0));
        acceptanceGateService.gates.put("job-ready", gate("job-ready", "READY"));

        sceneBoardService.boards.put("job-review", sceneBoard("job-review", "READY", 3, true, true));
        renderReviewService.reviews.put("job-review", renderReview("job-review", "READY", true, true));
        playbackResolutionService.resolutions.put("job-review", resolution("job-review", "ATTENTION", 2, true, true));
        deliveryPackageService.packages.put("job-review", delivery("job-review", "BLOCKED", true, true, 2));
        acceptanceGateService.gates.put("job-review", gate("job-review", "BLOCKED"));

        sceneBoardService.boards.put("job-render", sceneBoard("job-render", "ATTENTION", 2, false, false));
        renderReviewService.reviews.put("job-render", renderReview("job-render", "BLOCKED", false, false));
        playbackResolutionService.resolutions.put("job-render", resolution("job-render", "BLOCKED", 2, false, false));
        deliveryPackageService.packages.put("job-render", delivery("job-render", "BLOCKED", false, false, 2));

        sceneBoardService.boards.put("job-author", sceneBoard("job-author", "EMPTY", 0, false, false));
        sceneBoardService.boards.put("job-blocked", sceneBoard("job-blocked", "BLOCKED", 1, false, false));

        SessionNarrationProductionBoardVo board = service.board(25);

        assertThat(board.overallStatus()).isEqualTo("BLOCKED");
        assertThat(board.readyToDeliverCount()).isEqualTo(1);
        assertThat(board.needsReviewCount()).isEqualTo(1);
        assertThat(board.needsRenderCount()).isEqualTo(1);
        assertThat(board.needsAuthoringCount()).isEqualTo(1);
        assertThat(board.blockedCount()).isEqualTo(1);
        assertThat(board.notApplicableCount()).isEqualTo(1);
        assertThat(board.jobs()).extracting("classification")
                .contains("READY_TO_DELIVER", "NEEDS_REVIEW", "NEEDS_RENDER", "NEEDS_AUTHORING", "BLOCKED", "NOT_APPLICABLE");
        assertThat(board.jobs()).filteredOn(job -> job.jobId().equals("job-ready")).singleElement()
                .satisfies(job -> {
                    assertThat(job.audioReady()).isTrue();
                    assertThat(job.videoReady()).isTrue();
                    assertThat(job.deliveryReady()).isTrue();
                    assertThat(job.links()).extracting("href")
                            .contains("/api/jobs/job-ready/narration-scene-board",
                                    "/api/jobs/job-ready/narration-delivery-package",
                                    "/api/jobs/job-ready/demo-acceptance-gate");
                });
        assertThat(board.primaryAction()).isNotNull();
        assertThat(board.primaryAction().href()).contains("job-blocked");
        assertThat(board.safetyNotes()).anyMatch(note -> note.contains("metadata-only"));
    }

    @Test
    void convertsPerJobServiceFailuresToBlockedRowsAndKeepsMarkdownSafe() throws Exception {
        jobRepository.summaries = List.of(summary("job-sensitive", LocalizationJobStatus.COMPLETED));
        sceneBoardService.failures.put("job-sensitive", new IllegalStateException("provider payload source-videos/private.mov /Users/example/raw.mp4 OPENAI_API_KEY"));

        SessionNarrationProductionBoardVo board = service.board(10);
        String markdown = service.boardMarkdown(10);
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(board);

        assertThat(board.overallStatus()).isEqualTo("BLOCKED");
        assertThat(board.blockedCount()).isEqualTo(1);
        assertThat(board.jobs()).singleElement().satisfies(job -> {
            assertThat(job.classification()).isEqualTo("BLOCKED");
            assertThat(job.primaryBlocker()).contains("Narration production evidence could not be loaded");
        });
        assertThat(markdown)
                .contains("LinguaFrame Session Narration Production Board")
                .contains("job-sensitive")
                .contains("BLOCKED");
        assertThat(json + markdown)
                .doesNotContain("Narration script body")
                .doesNotContain("reviewer note body")
                .doesNotContain("source-videos/")
                .doesNotContain("job-artifacts/")
                .doesNotContain("/Users/example")
                .doesNotContain("provider payload")
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("private-demo-token")
                .doesNotContain("raw transcript text")
                .doesNotContain("raw subtitle text");
    }

    private static LocalizationJobSummaryVo summary(String jobId, LocalizationJobStatus status) {
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
                "tears-showcase",
                status,
                Instant.parse("2026-06-30T10:00:00Z"),
                status == LocalizationJobStatus.QUEUED ? null : Instant.parse("2026-06-30T10:01:00Z"),
                status == LocalizationJobStatus.COMPLETED ? Instant.parse("2026-06-30T10:05:00Z") : null,
                status == LocalizationJobStatus.FAILED ? Instant.parse("2026-06-30T10:05:00Z") : null,
                status == LocalizationJobStatus.FAILED ? LocalizationJobStage.TARGET_SUBTITLE_EXPORT : null,
                status == LocalizationJobStatus.FAILED ? "safe failure summary" : null,
                status == LocalizationJobStatus.FAILED ? 1 : 0,
                new BigDecimal("0.01000000")
        );
    }

    private static NarrationSceneBoardVo sceneBoard(String jobId, String status, int segments, boolean audioReady, boolean videoReady) {
        return new NarrationSceneBoardVo(
                jobId,
                Instant.parse("2026-06-30T10:10:00Z"),
                status,
                segments,
                BigDecimal.valueOf(segments * 10L),
                BigDecimal.valueOf(segments * 12L),
                segments == 0 ? BigDecimal.ZERO : new BigDecimal("83.33"),
                status.equals("BLOCKED") ? 1 : 0,
                status.equals("BLOCKED") ? BigDecimal.TEN : BigDecimal.ZERO,
                status.equals("BLOCKED"),
                segments == 0 ? 0 : 1,
                1,
                1,
                audioReady,
                videoReady,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("Metadata-only scene board.")
        );
    }

    private static NarrationRenderReviewVo renderReview(String jobId, String status, boolean audioReady, boolean videoReady) {
        return new NarrationRenderReviewVo(
                jobId,
                status,
                status.equals("READY") ? "Continue to delivery." : "Render narration audio/video.",
                audioReady ? 2 : 0,
                audioReady ? BigDecimal.valueOf(20) : BigDecimal.ZERO,
                audioReady ? BigDecimal.valueOf(24) : BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                false,
                "DEFAULT",
                1,
                "1 override",
                1,
                "1 keyframe",
                audioReady,
                audioReady ? 1 : 0,
                videoReady,
                videoReady ? 1 : 0,
                false,
                null,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of("Metadata-only render review.")
        );
    }

    private static NarrationPlaybackReviewResolutionVo resolution(String jobId, String status, int unresolved, boolean audioReady, boolean videoReady) {
        return new NarrationPlaybackReviewResolutionVo(
                jobId,
                Instant.parse("2026-06-30T10:11:00Z"),
                status,
                unresolved == 0 ? "Download delivery package." : "Resolve playback review rows.",
                3,
                3 - unresolved,
                unresolved,
                unresolved,
                0,
                0,
                audioReady,
                audioReady ? 1 : 0,
                videoReady,
                videoReady ? 1 : 0,
                List.of(),
                List.of(),
                List.of("Metadata-only playback resolution.")
        );
    }

    private static NarrationDeliveryPackageVo delivery(String jobId, String status, boolean audioReady, boolean videoReady, int unresolved) {
        return new NarrationDeliveryPackageVo(
                jobId,
                Instant.parse("2026-06-30T10:12:00Z"),
                status,
                status.equals("READY") ? "READY" : "BLOCKED",
                status.equals("READY") ? "Download delivery package." : "Resolve narration blockers.",
                audioReady,
                videoReady,
                unresolved,
                "READY",
                "READY",
                audioReady && videoReady ? "READY" : "BLOCKED",
                "READY",
                unresolved == 0 ? "READY" : "BLOCKED",
                "READY",
                List.of(),
                List.of(),
                List.of(),
                List.of("narration-delivery-package.md"),
                List.of("Metadata-only delivery package.")
        );
    }

    private static DemoAcceptanceGateVo gate(String jobId, String status) {
        return new DemoAcceptanceGateVo(
                jobId,
                "video-" + jobId,
                Instant.parse("2026-06-30T10:13:00Z"),
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

    private static final class StubNarrationSceneBoardService implements NarrationSceneBoardService {
        private final Map<String, NarrationSceneBoardVo> boards = new HashMap<>();
        private final Map<String, RuntimeException> failures = new HashMap<>();

        @Override
        public NarrationSceneBoardVo getSceneBoard(String jobId) {
            RuntimeException failure = failures.get(jobId);
            if (failure != null) {
                throw failure;
            }
            return boards.getOrDefault(jobId, sceneBoard(jobId, "EMPTY", 0, false, false));
        }

        @Override
        public String renderMarkdown(String jobId) {
            return "# Scene board";
        }
    }

    private static final class StubNarrationRenderReviewService implements NarrationRenderReviewService {
        private final Map<String, NarrationRenderReviewVo> reviews = new HashMap<>();

        @Override
        public NarrationRenderReviewVo getReview(String jobId) {
            return reviews.get(jobId);
        }

        @Override
        public String renderMarkdown(String jobId) {
            return "# Render review";
        }
    }

    private static final class StubNarrationPlaybackReviewResolutionService implements NarrationPlaybackReviewResolutionService {
        private final Map<String, NarrationPlaybackReviewResolutionVo> resolutions = new HashMap<>();

        @Override
        public NarrationPlaybackReviewResolutionVo getResolution(String jobId) {
            return resolutions.get(jobId);
        }

        @Override
        public String renderMarkdown(String jobId) {
            return "# Playback resolution";
        }
    }

    private static final class StubNarrationDeliveryPackageService implements NarrationDeliveryPackageService {
        private final Map<String, NarrationDeliveryPackageVo> packages = new HashMap<>();

        @Override
        public NarrationDeliveryPackageVo getSummary(String jobId) {
            return packages.get(jobId);
        }

        @Override
        public NarrationDeliveryPackageVo getPackage(String jobId) {
            return getSummary(jobId);
        }

        @Override
        public String renderMarkdown(String jobId) {
            return "# Delivery package";
        }

        @Override
        public com.linguaframe.job.domain.bo.StoredNarrationDeliveryPackageBo openPackage(String jobId) {
            throw new UnsupportedOperationException("Not needed for production board tests");
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
