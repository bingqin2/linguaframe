package com.linguaframe.job.service;

import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.CustomNarrationRenderHandoffVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateVo;
import com.linguaframe.job.domain.vo.DemoHandoffPortalVo;
import com.linguaframe.job.domain.vo.DemoReviewerWorkspaceVo;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.NarrationDeliveryPackageVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewResolutionVo;
import com.linguaframe.job.domain.vo.NarrationRenderReviewVo;
import com.linguaframe.job.domain.vo.NarrationSceneBoardVo;
import com.linguaframe.job.domain.vo.NarrationStudioVo;
import com.linguaframe.job.domain.vo.NarrationWorkspaceVo;
import com.linguaframe.job.domain.vo.UploadNarrationLaunchpadVo;
import com.linguaframe.job.service.impl.NarrationStudioServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NarrationStudioServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-01T05:15:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void buildsReadyStudioForRenderedCustomNarrationHandoff() {
        NarrationStudioService service = service(scenario("READY", 3, true, true));

        NarrationStudioVo studio = service.getStudio("job-studio");

        assertThat(studio.jobId()).isEqualTo("job-studio");
        assertThat(studio.videoId()).isEqualTo("video-studio");
        assertThat(studio.generatedAt()).isEqualTo(NOW);
        assertThat(studio.overallStatus()).isEqualTo("READY");
        assertThat(studio.phase()).isEqualTo("NARRATION_STUDIO_READY");
        assertThat(studio.segmentCount()).isEqualTo(3);
        assertThat(studio.characterCount()).isEqualTo(180);
        assertThat(studio.audioReady()).isTrue();
        assertThat(studio.videoReady()).isTrue();
        assertThat(studio.steps()).extracting("key")
                .containsExactly("AUTHOR_ROWS", "PREVIEW_TTS", "RENDER_CUSTOM", "REVIEW_PLAYBACK", "PACKAGE_DELIVERY", "FINAL_HANDOFF");
        assertThat(studio.steps()).extracting("status").containsOnly("READY");
        assertThat(studio.links()).extracting("kind")
                .contains(
                        "NARRATION_WORKSPACE",
                        "UPLOAD_NARRATION_LAUNCHPAD",
                        "CUSTOM_NARRATION_RENDER_REPORT",
                        "NARRATION_DELIVERY_PACKAGE",
                        "DEMO_ACCEPTANCE_GATE",
                        "DEMO_REVIEWER_WORKSPACE",
                        "DEMO_HANDOFF_PORTAL"
                );
        assertThat(studio.toString())
                .doesNotContain("raw transcript text")
                .doesNotContain("private reviewer note body")
                .doesNotContain("/Users/")
                .doesNotContain("sk-test")
                .doesNotContain("{\"provider\":\"payload\"}");
    }

    @Test
    void marksAttentionWhenSavedRowsAreNotRenderedYet() {
        NarrationStudioService service = service(scenario("ATTENTION", 2, false, false));

        NarrationStudioVo studio = service.getStudio("job-studio");

        assertThat(studio.overallStatus()).isEqualTo("ATTENTION");
        assertThat(studio.phase()).isEqualTo("NARRATION_STUDIO_NEEDS_ACTION");
        assertThat(studio.audioReady()).isFalse();
        assertThat(studio.recommendedNextAction()).contains("Run the custom narration render");
        assertThat(studio.steps())
                .anySatisfy(step -> {
                    assertThat(step.key()).isEqualTo("RENDER_CUSTOM");
                    assertThat(step.status()).isEqualTo("ATTENTION");
                    assertThat(step.safeLink()).isEqualTo("/api/jobs/job-studio/custom-narration-render/markdown/download");
                });
    }

    @Test
    void marksEmptyWhenNoNarrationRowsExist() {
        NarrationStudioService service = service(scenario("EMPTY", 0, false, false));

        NarrationStudioVo studio = service.getStudio("job-studio");

        assertThat(studio.overallStatus()).isEqualTo("EMPTY");
        assertThat(studio.phase()).isEqualTo("NARRATION_STUDIO_EMPTY");
        assertThat(studio.recommendedNextAction()).contains("Add rows");
        assertThat(studio.steps())
                .anySatisfy(step -> {
                    assertThat(step.key()).isEqualTo("AUTHOR_ROWS");
                    assertThat(step.status()).isEqualTo("EMPTY");
                });
    }

    private static NarrationStudioService service(Scenario scenario) {
        return new NarrationStudioServiceImpl(
                new StaticQueryService(),
                new StaticWorkspaceService(scenario),
                jobId -> launchpad(scenario),
                new StaticSceneBoardService(scenario),
                new StaticRenderReviewService(scenario),
                new StaticPlaybackResolutionService(scenario),
                new StaticDeliveryPackageService(scenario),
                jobId -> customRender(scenario),
                jobId -> acceptance(scenario),
                new StaticReviewerWorkspaceService(scenario),
                new StaticHandoffPortalService(scenario),
                CLOCK
        );
    }

    private static Scenario scenario(String status, int segmentCount, boolean audioReady, boolean videoReady) {
        return new Scenario(status, segmentCount, segmentCount * 60, audioReady, videoReady);
    }

    private static NarrationWorkspaceVo workspace(Scenario scenario) {
        return new NarrationWorkspaceVo(
                "job-studio",
                scenario.segmentCount() == 0 ? "EMPTY" : "READY",
                scenario.segmentCount(),
                new BigDecimal("42.0"),
                scenario.characterCount(),
                scenario.segmentCount() > 0,
                null,
                null,
                null,
                List.of(),
                List.of("Workspace metadata only.")
        );
    }

    private static UploadNarrationLaunchpadVo launchpad(Scenario scenario) {
        return new UploadNarrationLaunchpadVo(
                "job-studio",
                NOW,
                scenario.segmentCount() == 0 ? "EMPTY" : "READY",
                scenario.segmentCount() == 0 ? "Add rows." : "Continue.",
                scenario.segmentCount(),
                scenario.characterCount(),
                new BigDecimal("42.0"),
                null,
                "demo",
                "alloy",
                "alloy x " + scenario.segmentCount(),
                scenario.segmentCount() == 0 ? "EMPTY" : "READY",
                0,
                0,
                scenario.audioReady(),
                scenario.videoReady(),
                List.of(),
                List.of(),
                List.of("Launchpad metadata only.")
        );
    }

    private static NarrationSceneBoardVo sceneBoard(Scenario scenario) {
        return new NarrationSceneBoardVo(
                "job-studio",
                NOW,
                scenario.segmentCount() == 0 ? "EMPTY" : "READY",
                scenario.segmentCount(),
                new BigDecimal("42.0"),
                new BigDecimal("42.0"),
                new BigDecimal("100.0"),
                0,
                BigDecimal.ZERO,
                false,
                scenario.segmentCount() > 0 ? 1 : 0,
                0,
                0,
                scenario.audioReady(),
                scenario.videoReady(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("Scene board metadata only.")
        );
    }

    private static NarrationRenderReviewVo renderReview(Scenario scenario) {
        return new NarrationRenderReviewVo(
                "job-studio",
                scenario.audioReady() ? "READY" : scenario.segmentCount() == 0 ? "EMPTY" : "ATTENTION",
                scenario.audioReady() ? "Review rendered output." : "Render custom narration.",
                scenario.segmentCount(),
                new BigDecimal("42.0"),
                new BigDecimal("42.0"),
                0,
                BigDecimal.ZERO,
                false,
                "alloy x " + scenario.segmentCount(),
                0,
                "none",
                0,
                "none",
                scenario.audioReady(),
                scenario.audioReady() ? 1 : 0,
                scenario.videoReady(),
                scenario.videoReady() ? 1 : 0,
                false,
                null,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of("Render review metadata only.")
        );
    }

    private static NarrationPlaybackReviewResolutionVo playbackResolution(Scenario scenario) {
        return new NarrationPlaybackReviewResolutionVo(
                "job-studio",
                NOW,
                scenario.segmentCount() == 0 ? "EMPTY" : "READY",
                scenario.segmentCount() == 0 ? "Add rows." : "Playback resolved.",
                scenario.segmentCount(),
                scenario.segmentCount(),
                0,
                0,
                0,
                0,
                scenario.audioReady(),
                scenario.audioReady() ? 1 : 0,
                scenario.videoReady(),
                scenario.videoReady() ? 1 : 0,
                List.of(),
                List.of(),
                List.of("Playback resolution metadata only.")
        );
    }

    private static CustomNarrationRenderHandoffVo customRender(Scenario scenario) {
        String status = scenario.segmentCount() == 0 ? "NOT_APPLICABLE" : scenario.audioReady() ? "READY" : "ATTENTION";
        return new CustomNarrationRenderHandoffVo(
                "job-studio",
                status,
                scenario.videoReady() ? "Audio + narrated video" : scenario.audioReady() ? "Audio only" : "Not rendered",
                scenario.segmentCount(),
                scenario.characterCount(),
                scenario.audioReady(),
                scenario.videoReady(),
                "/api/jobs/job-studio/custom-narration-render/markdown/download",
                "/api/jobs/job-studio/custom-narration-render",
                "/api/jobs/job-studio/narration-evidence",
                "/api/jobs/job-studio/narration-delivery-package",
                scenario.audioReady() ? "Open the custom narration render report." : "Run the custom narration render console before final handoff."
        );
    }

    private static DemoAcceptanceGateVo acceptance(Scenario scenario) {
        return new DemoAcceptanceGateVo(
                "job-studio",
                "video-studio",
                NOW,
                scenario.status().equals("READY") ? "READY" : "ATTENTION",
                LocalizationJobStatus.COMPLETED,
                "zh-CN",
                "tears-showcase",
                "Narration acceptance",
                "Safe summary.",
                scenario.status().equals("READY") ? "Present this run." : "Review warnings.",
                customRender(scenario),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("Acceptance metadata only.")
        );
    }

    private static DemoReviewerWorkspaceVo reviewerWorkspace(Scenario scenario) {
        return new DemoReviewerWorkspaceVo(
                "job-studio",
                "video-studio",
                NOW,
                scenario.status().equals("READY") ? "READY" : "ATTENTION",
                "REVIEW_PACKAGE_READY",
                "Open reviewer workspace.",
                NOW,
                "zh-CN",
                "tears-showcase",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("Reviewer workspace metadata only.")
        );
    }

    private static DemoHandoffPortalVo handoffPortal(Scenario scenario) {
        return new DemoHandoffPortalVo(
                "job-studio",
                "video-studio",
                NOW,
                scenario.status().equals("READY") ? "READY" : "ATTENTION",
                "HANDOFF_PORTAL_READY",
                "Portal",
                "Open portal.",
                NOW,
                "zh-CN",
                "tears-showcase",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("Portal metadata only.")
        );
    }

    private record Scenario(String status, int segmentCount, int characterCount, boolean audioReady, boolean videoReady) {
    }

    private static class StaticQueryService implements LocalizationJobQueryService {

        @Override
        public LocalizationJobVo getJob(String jobId) {
            return new LocalizationJobVo(
                    jobId,
                    "video-studio",
                    "zh-CN",
                    "alloy",
                    LocalizationJobStatus.COMPLETED,
                    NOW.minusSeconds(120),
                    NOW.minusSeconds(90),
                    NOW,
                    null,
                    null,
                    null,
                    0,
                    JobDispatchEventStatus.DISPATCHED,
                    1,
                    NOW.minusSeconds(100),
                    List.of(),
                    new JobUsageSummaryVo(0, 0, 0, BigDecimal.ZERO, null, null, null, null),
                    new JobCacheSummaryVo(0, 0, 0),
                    List.of(),
                    null,
                    null,
                    null
            );
        }

        @Override
        public LocalizationJobListVo listJobs(LocalizationJobStatus status, Integer limit, Integer offset) {
            throw new UnsupportedOperationException("Not needed for narration studio tests.");
        }

        @Override
        public JobDiagnosticsReportVo getDiagnosticsReport(String jobId) {
            throw new UnsupportedOperationException("Not needed for narration studio tests.");
        }
    }

    private static class StaticWorkspaceService implements NarrationWorkspaceService {

        private final Scenario scenario;

        private StaticWorkspaceService(Scenario scenario) {
            this.scenario = scenario;
        }

        @Override
        public NarrationWorkspaceVo getWorkspace(String jobId) {
            return workspace(scenario);
        }

        @Override
        public NarrationWorkspaceVo saveWorkspace(String jobId, com.linguaframe.job.domain.dto.SaveNarrationSegmentsRequest request) {
            throw new UnsupportedOperationException("Not needed for narration studio tests.");
        }

        @Override
        public NarrationWorkspaceVo updateMixSettings(String jobId, com.linguaframe.job.domain.dto.UpdateNarrationMixSettingsDto request) {
            throw new UnsupportedOperationException("Not needed for narration studio tests.");
        }

        @Override
        public NarrationWorkspaceVo clearWorkspace(String jobId) {
            throw new UnsupportedOperationException("Not needed for narration studio tests.");
        }
    }

    private static class StaticSceneBoardService implements NarrationSceneBoardService {

        private final Scenario scenario;

        private StaticSceneBoardService(Scenario scenario) {
            this.scenario = scenario;
        }

        @Override
        public NarrationSceneBoardVo getSceneBoard(String jobId) {
            return sceneBoard(scenario);
        }

        @Override
        public String renderMarkdown(String jobId) {
            return "# Narration scene board\n";
        }
    }

    private static class StaticRenderReviewService implements NarrationRenderReviewService {

        private final Scenario scenario;

        private StaticRenderReviewService(Scenario scenario) {
            this.scenario = scenario;
        }

        @Override
        public NarrationRenderReviewVo getReview(String jobId) {
            return renderReview(scenario);
        }

        @Override
        public String renderMarkdown(String jobId) {
            return "# Narration render review\n";
        }
    }

    private static class StaticPlaybackResolutionService implements NarrationPlaybackReviewResolutionService {

        private final Scenario scenario;

        private StaticPlaybackResolutionService(Scenario scenario) {
            this.scenario = scenario;
        }

        @Override
        public NarrationPlaybackReviewResolutionVo getResolution(String jobId) {
            return playbackResolution(scenario);
        }

        @Override
        public String renderMarkdown(String jobId) {
            return "# Narration playback resolution\n";
        }
    }

    private static class StaticDeliveryPackageService implements NarrationDeliveryPackageService {

        private final Scenario scenario;

        private StaticDeliveryPackageService(Scenario scenario) {
            this.scenario = scenario;
        }

        @Override
        public NarrationDeliveryPackageVo getSummary(String jobId) {
            return packageVo();
        }

        @Override
        public NarrationDeliveryPackageVo getPackage(String jobId) {
            return packageVo();
        }

        @Override
        public String renderMarkdown(String jobId) {
            return "# Narration delivery package\n";
        }

        @Override
        public com.linguaframe.job.domain.bo.StoredNarrationDeliveryPackageBo openPackage(String jobId) {
            throw new UnsupportedOperationException("Not needed for narration studio tests.");
        }

        private NarrationDeliveryPackageVo packageVo() {
            return new NarrationDeliveryPackageVo(
                    "job-studio",
                    NOW,
                    scenario.segmentCount() == 0 ? "EMPTY" : scenario.audioReady() ? "READY" : "ATTENTION",
                    "NARRATION_DELIVERY",
                    scenario.audioReady() ? "Share delivery package." : "Render custom narration.",
                    scenario.audioReady(),
                    scenario.videoReady(),
                    0,
                    scenario.audioReady() ? "READY" : "ATTENTION",
                    "READY",
                    scenario.audioReady() ? "READY" : "ATTENTION",
                    "READY",
                    "READY",
                    "READY",
                    List.of(),
                    List.of(),
                    List.of(),
                    scenario.audioReady() ? List.of("narration-delivery-package.json") : List.of(),
                    List.of("Delivery package metadata only.")
            );
        }
    }

    private static class StaticReviewerWorkspaceService implements DemoReviewerWorkspaceService {

        private final Scenario scenario;

        private StaticReviewerWorkspaceService(Scenario scenario) {
            this.scenario = scenario;
        }

        @Override
        public DemoReviewerWorkspaceVo getWorkspace(String jobId) {
            return reviewerWorkspace(scenario);
        }

        @Override
        public String renderMarkdown(String jobId) {
            return "# Demo reviewer workspace\n";
        }

        @Override
        public com.linguaframe.job.domain.bo.StoredDemoReviewerWorkspacePackageBo openPackage(String jobId) {
            throw new UnsupportedOperationException("Not needed for narration studio tests.");
        }
    }

    private static class StaticHandoffPortalService implements DemoHandoffPortalService {

        private final Scenario scenario;

        private StaticHandoffPortalService(Scenario scenario) {
            this.scenario = scenario;
        }

        @Override
        public DemoHandoffPortalVo getPortal(String jobId) {
            return handoffPortal(scenario);
        }

        @Override
        public String renderMarkdown(String jobId) {
            return "# Demo handoff portal\n";
        }

        @Override
        public com.linguaframe.job.domain.bo.StoredDemoHandoffPortalPackageBo openPackage(String jobId) {
            throw new UnsupportedOperationException("Not needed for narration studio tests.");
        }
    }
}
