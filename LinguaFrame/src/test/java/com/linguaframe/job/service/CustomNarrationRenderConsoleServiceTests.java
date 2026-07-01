package com.linguaframe.job.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.dto.CustomNarrationRenderPreflightDto;
import com.linguaframe.job.domain.dto.CustomNarrationRenderDto;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.vo.CustomNarrationRenderVo;
import com.linguaframe.job.domain.vo.CustomNarrationRenderPreflightVo;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.NarratedVideoGenerationVo;
import com.linguaframe.job.domain.vo.NarrationDeliveryPackageVo;
import com.linguaframe.job.domain.vo.NarrationEvidenceVo;
import com.linguaframe.job.domain.vo.NarrationGenerationVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewVo;
import com.linguaframe.job.domain.vo.NarrationRenderReviewVo;
import com.linguaframe.job.domain.vo.NarrationSceneBoardCheckVo;
import com.linguaframe.job.domain.vo.NarrationSceneBoardVo;
import com.linguaframe.job.domain.vo.NarrationWorkspaceVo;
import com.linguaframe.job.service.impl.CustomNarrationRenderConsoleServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class CustomNarrationRenderConsoleServiceTests {

    @Test
    void preflightsReadySavedWorkspaceWithoutExposingRowText() {
        CustomNarrationRenderConsoleService service = service(
                properties("demo"),
                new StaticWorkspaceService(workspace("job-custom-render", "READY", 3, "Sensitive narration text")),
                new StaticSceneBoardService(sceneBoard("job-custom-render", "READY", true, false)),
                new StaticRenderReviewService(renderReview("job-custom-render", "ATTENTION", false, false)),
                new StaticEvidenceService(evidence("job-custom-render", "ATTENTION")),
                new StaticArtifactService(List.of(artifact("base-video", JobArtifactType.BURNED_VIDEO)))
        );

        CustomNarrationRenderPreflightVo result = service.preflight(
                "job-custom-render",
                new CustomNarrationRenderPreflightDto(true, false, true)
        );

        assertThat(result.jobId()).isEqualTo("job-custom-render");
        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.segmentCount()).isEqualTo(3);
        assertThat(result.characterCount()).isEqualTo(72);
        assertThat(result.totalNarrationSeconds()).isEqualByComparingTo("18.500");
        assertThat(result.voiceSummary()).isEqualTo("alloy x2, verse x1");
        assertThat(result.sceneBoardStatus()).isEqualTo("READY");
        assertThat(result.providerMode()).isEqualTo("demo");
        assertThat(result.paidProvider()).isFalse();
        assertThat(result.generateNarratedVideo()).isTrue();
        assertThat(result.audioReady()).isFalse();
        assertThat(result.videoReady()).isFalse();
        assertThat(result.safeNextCommand()).isEqualTo("LINGUAFRAME_DEMO_JOB_ID=job-custom-render scripts/demo/custom-narration-render.sh");
        assertThat(result.safeRoutes()).contains(
                "/api/jobs/job-custom-render/custom-narration-render",
                "/api/jobs/job-custom-render/custom-narration-render/markdown/download"
        );
        assertThat(result.checks())
                .extracting(check -> check.key() + ":" + check.status())
                .contains(
                        "WORKSPACE:PASS",
                        "SCENE_BOARD:PASS",
                        "TTS_PROVIDER:PASS",
                        "VIDEO_ACKNOWLEDGEMENT:PASS",
                        "NARRATED_VIDEO_INPUT:PASS"
                );
        assertThat(result.toString()).doesNotContain("Sensitive narration text");
    }

    @Test
    void blocksWhenSavedWorkspaceHasNoRows() {
        CustomNarrationRenderConsoleService service = service(
                properties("demo"),
                new StaticWorkspaceService(workspace("job-empty-render", "EMPTY", 0, "")),
                new StaticSceneBoardService(sceneBoard("job-empty-render", "BLOCKED", false, false)),
                new StaticRenderReviewService(renderReview("job-empty-render", "BLOCKED", false, false)),
                new StaticEvidenceService(evidence("job-empty-render", "BLOCKED")),
                new StaticArtifactService(List.of(artifact("base-video", JobArtifactType.BURNED_VIDEO)))
        );

        CustomNarrationRenderPreflightVo result = service.preflight(
                "job-empty-render",
                new CustomNarrationRenderPreflightDto(true, false, true)
        );

        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.checks())
                .extracting(check -> check.key() + ":" + check.status())
                .contains("WORKSPACE:BLOCK");
    }

    @Test
    void blocksWhenSceneBoardHasBlockingChecks() {
        CustomNarrationRenderConsoleService service = service(
                properties("demo"),
                new StaticWorkspaceService(workspace("job-scene-blocked", "READY", 2, "Hidden text")),
                new StaticSceneBoardService(sceneBoard("job-scene-blocked", "BLOCKED", false, true)),
                new StaticRenderReviewService(renderReview("job-scene-blocked", "ATTENTION", false, false)),
                new StaticEvidenceService(evidence("job-scene-blocked", "ATTENTION")),
                new StaticArtifactService(List.of(artifact("base-video", JobArtifactType.BURNED_VIDEO)))
        );

        CustomNarrationRenderPreflightVo result = service.preflight(
                "job-scene-blocked",
                new CustomNarrationRenderPreflightDto(true, false, true)
        );

        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.checks())
                .extracting(check -> check.key() + ":" + check.status())
                .contains("SCENE_BOARD:BLOCK");
    }

    @Test
    void blocksNarratedVideoWhenBaseVideoIsMissingOrNotAcknowledged() {
        CustomNarrationRenderConsoleService service = service(
                properties("demo"),
                new StaticWorkspaceService(workspace("job-missing-video", "READY", 2, "Hidden text")),
                new StaticSceneBoardService(sceneBoard("job-missing-video", "READY", false, false)),
                new StaticRenderReviewService(renderReview("job-missing-video", "ATTENTION", false, false)),
                new StaticEvidenceService(evidence("job-missing-video", "ATTENTION")),
                new StaticArtifactService(List.of())
        );

        CustomNarrationRenderPreflightVo result = service.preflight(
                "job-missing-video",
                new CustomNarrationRenderPreflightDto(true, false, false)
        );

        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.requiredAcknowledgements()).contains("VIDEO_RENDER");
        assertThat(result.checks())
                .extracting(check -> check.key() + ":" + check.status())
                .contains(
                        "VIDEO_ACKNOWLEDGEMENT:BLOCK",
                        "NARRATED_VIDEO_INPUT:BLOCK"
                );
    }

    @Test
    void blocksPaidProviderUntilCostIsAcknowledged() {
        CustomNarrationRenderConsoleService service = service(
                properties("openai"),
                new StaticWorkspaceService(workspace("job-paid-provider", "READY", 2, "Hidden text")),
                new StaticSceneBoardService(sceneBoard("job-paid-provider", "READY", false, false)),
                new StaticRenderReviewService(renderReview("job-paid-provider", "ATTENTION", false, false)),
                new StaticEvidenceService(evidence("job-paid-provider", "ATTENTION")),
                new StaticArtifactService(List.of(artifact("base-video", JobArtifactType.BURNED_VIDEO)))
        );

        CustomNarrationRenderPreflightVo result = service.preflight(
                "job-paid-provider",
                new CustomNarrationRenderPreflightDto(false, false, false)
        );

        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.providerMode()).isEqualTo("openai");
        assertThat(result.paidProvider()).isTrue();
        assertThat(result.requiredAcknowledgements()).contains("PROVIDER_COST");
        assertThat(result.checks())
                .extracting(check -> check.key() + ":" + check.status())
                .contains("TTS_PROVIDER:BLOCK");
    }

    @Test
    void rendersSavedWorkspaceAudioAndVideoWithoutApplyingDemoPreset() {
        RecordingNarrationAudioService audioService = new RecordingNarrationAudioService(false);
        RecordingNarratedVideoService videoService = new RecordingNarratedVideoService(false);
        RecordingPlaybackReviewService playbackReviewService = new RecordingPlaybackReviewService();
        RecordingDeliveryPackageService deliveryPackageService = new RecordingDeliveryPackageService();
        CustomNarrationRenderConsoleService service = renderService(
                properties("demo"),
                new StaticWorkspaceService(workspace("job-render", "READY", 3, "Hidden custom script")),
                new StaticSceneBoardService(sceneBoard("job-render", "READY", false, false)),
                new StaticRenderReviewService(renderReview("job-render", "ATTENTION", false, false)),
                new StaticEvidenceService(evidence("job-render", "ATTENTION")),
                new StaticArtifactService(List.of(artifact("base-video", JobArtifactType.BURNED_VIDEO))),
                audioService,
                videoService,
                playbackReviewService,
                deliveryPackageService
        );

        CustomNarrationRenderVo result = service.render(
                "job-render",
                new CustomNarrationRenderDto(true, false, true)
        );

        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.preflight().status()).isEqualTo("READY");
        assertThat(result.narrationAudio().filename()).isEqualTo("narration-audio.mp3");
        assertThat(result.narratedVideo().filename()).isEqualTo("narrated-video.mp4");
        assertThat(result.generatedArtifactCount()).isEqualTo(2);
        assertThat(result.steps())
                .extracting(step -> step.key() + ":" + step.status())
                .contains(
                        "PREFLIGHT:SUCCEEDED",
                        "NARRATION_AUDIO:SUCCEEDED",
                        "NARRATED_VIDEO:SUCCEEDED",
                        "RENDER_REVIEW:SUCCEEDED",
                        "PLAYBACK_REVIEW:SUCCEEDED",
                        "NARRATION_EVIDENCE:SUCCEEDED",
                        "DELIVERY_PACKAGE:SUCCEEDED"
                );
        assertThat(audioService.calls).containsExactly("job-render");
        assertThat(videoService.calls).containsExactly("job-render");
        assertThat(playbackReviewService.calls).containsExactly("job-render");
        assertThat(deliveryPackageService.calls).containsExactly("job-render");
        assertThat(result.toString()).doesNotContain("Hidden custom script");
    }

    @Test
    void preservesAudioAndReturnsPartialWhenVideoGenerationFails() {
        CustomNarrationRenderConsoleService service = renderService(
                properties("demo"),
                new StaticWorkspaceService(workspace("job-partial", "READY", 3, "Hidden custom script")),
                new StaticSceneBoardService(sceneBoard("job-partial", "READY", false, false)),
                new StaticRenderReviewService(renderReview("job-partial", "ATTENTION", false, false)),
                new StaticEvidenceService(evidence("job-partial", "ATTENTION")),
                new StaticArtifactService(List.of(artifact("base-video", JobArtifactType.BURNED_VIDEO))),
                new RecordingNarrationAudioService(false),
                new RecordingNarratedVideoService(true),
                new RecordingPlaybackReviewService(),
                new RecordingDeliveryPackageService()
        );

        CustomNarrationRenderVo result = service.render(
                "job-partial",
                new CustomNarrationRenderDto(true, false, true)
        );

        assertThat(result.status()).isEqualTo("PARTIAL");
        assertThat(result.narrationAudio()).isNotNull();
        assertThat(result.narratedVideo()).isNull();
        assertThat(result.generatedArtifactCount()).isEqualTo(1);
        assertThat(result.steps())
                .extracting(step -> step.key() + ":" + step.status())
                .contains("NARRATION_AUDIO:SUCCEEDED", "NARRATED_VIDEO:FAILED");
    }

    private static CustomNarrationRenderConsoleService service(
            LinguaFrameProperties properties,
            NarrationWorkspaceService workspaceService,
            NarrationSceneBoardService sceneBoardService,
            NarrationRenderReviewService renderReviewService,
            NarrationEvidenceService evidenceService,
            JobArtifactService artifactService
    ) {
        return new CustomNarrationRenderConsoleServiceImpl(
                properties,
                workspaceService,
                sceneBoardService,
                renderReviewService,
                evidenceService,
                artifactService
        );
    }

    private static CustomNarrationRenderConsoleService renderService(
            LinguaFrameProperties properties,
            NarrationWorkspaceService workspaceService,
            NarrationSceneBoardService sceneBoardService,
            NarrationRenderReviewService renderReviewService,
            NarrationEvidenceService evidenceService,
            JobArtifactService artifactService,
            NarrationAudioService narrationAudioService,
            NarratedVideoService narratedVideoService,
            NarrationPlaybackReviewService playbackReviewService,
            NarrationDeliveryPackageService deliveryPackageService
    ) {
        return new CustomNarrationRenderConsoleServiceImpl(
                properties,
                workspaceService,
                sceneBoardService,
                renderReviewService,
                evidenceService,
                artifactService,
                narrationAudioService,
                narratedVideoService,
                playbackReviewService,
                deliveryPackageService
        );
    }

    private static LinguaFrameProperties properties(String ttsProvider) {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getTts().setProvider(ttsProvider);
        return properties;
    }

    private static NarrationWorkspaceVo workspace(String jobId, String status, int segmentCount, String hiddenText) {
        return new NarrationWorkspaceVo(
                jobId,
                status,
                segmentCount,
                new BigDecimal("18.500"),
                segmentCount == 0 ? 0 : 72,
                segmentCount > 0,
                null,
                null,
                null,
                List.of(),
                List.of(hiddenText)
        );
    }

    private static NarrationSceneBoardVo sceneBoard(String jobId, String status, boolean videoReady, boolean blockedCheck) {
        return new NarrationSceneBoardVo(
                jobId,
                Instant.parse("2026-07-01T00:00:00Z"),
                status,
                "BLOCKED".equals(status) ? 0 : 3,
                new BigDecimal("18.500"),
                new BigDecimal("20.000"),
                new BigDecimal("92.5"),
                0,
                BigDecimal.ZERO,
                false,
                2,
                0,
                0,
                false,
                videoReady,
                List.of(),
                blockedCheck
                        ? List.of(new NarrationSceneBoardCheckVo("OVERLAP", "Timeline overlap", "BLOCK", "Resolve overlap."))
                        : List.of(new NarrationSceneBoardCheckVo("TIMELINE", "Timeline", "PASS", "Timeline is ready.")),
                List.of(),
                List.of(),
                List.of("No row text is exposed.")
        );
    }

    private static NarrationRenderReviewVo renderReview(String jobId, String status, boolean audioReady, boolean videoReady) {
        return new NarrationRenderReviewVo(
                jobId,
                status,
                "Generate narration audio.",
                3,
                new BigDecimal("18.500"),
                new BigDecimal("20.000"),
                0,
                BigDecimal.ZERO,
                false,
                "alloy x2, verse x1",
                0,
                "No segment mix overrides.",
                0,
                "No mix keyframes.",
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
                List.of()
        );
    }

    private static NarrationEvidenceVo evidence(String jobId, String status) {
        return new NarrationEvidenceVo(
                jobId,
                status,
                3,
                72,
                new BigDecimal("18.500"),
                0,
                BigDecimal.ZERO,
                false,
                2,
                "alloy x2, verse x1",
                "alloy",
                true,
                1,
                "mixed",
                true,
                true,
                1,
                "ducked",
                new BigDecimal("0.35"),
                BigDecimal.ONE,
                120,
                "workspace",
                0,
                "No segment mix overrides.",
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static JobArtifactVo artifact(String artifactId, JobArtifactType type) {
        return new JobArtifactVo(
                artifactId,
                "job-custom-render",
                type,
                type.name().toLowerCase() + ".mp4",
                "video/mp4",
                1024,
                "sha256",
                false,
                null,
                Instant.parse("2026-07-01T00:00:00Z")
        );
    }

    private record StaticWorkspaceService(NarrationWorkspaceVo workspace) implements NarrationWorkspaceService {
        @Override
        public NarrationWorkspaceVo getWorkspace(String jobId) {
            return workspace;
        }

        @Override
        public NarrationWorkspaceVo saveWorkspace(String jobId, com.linguaframe.job.domain.dto.SaveNarrationSegmentsRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public NarrationWorkspaceVo updateMixSettings(String jobId, com.linguaframe.job.domain.dto.UpdateNarrationMixSettingsDto request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public NarrationWorkspaceVo clearWorkspace(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticSceneBoardService(NarrationSceneBoardVo sceneBoard) implements NarrationSceneBoardService {
        @Override
        public NarrationSceneBoardVo getSceneBoard(String jobId) {
            return sceneBoard;
        }

        @Override
        public String renderMarkdown(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticRenderReviewService(NarrationRenderReviewVo review) implements NarrationRenderReviewService {
        @Override
        public NarrationRenderReviewVo getReview(String jobId) {
            return review;
        }

        @Override
        public String renderMarkdown(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticEvidenceService(NarrationEvidenceVo evidence) implements NarrationEvidenceService {
        @Override
        public NarrationEvidenceVo getEvidence(String jobId) {
            return evidence;
        }

        @Override
        public String renderMarkdown(String jobId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.linguaframe.job.domain.bo.StoredNarrationEvidencePackageBo openPackage(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticArtifactService(List<JobArtifactVo> artifacts) implements JobArtifactService {
        @Override
        public JobArtifactVo createArtifact(com.linguaframe.job.domain.bo.CreateJobArtifactCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JobArtifactVo createReusedArtifact(String jobId, com.linguaframe.job.domain.entity.JobArtifactRecord source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<JobArtifactVo> listArtifacts(String jobId) {
            return artifacts;
        }

        @Override
        public com.linguaframe.job.domain.bo.StoredObjectResourceBo openArtifact(String jobId, String artifactId) {
            throw new UnsupportedOperationException();
        }
    }

    private static class RecordingNarrationAudioService implements NarrationAudioService {
        private final boolean fail;
        private final List<String> calls = new ArrayList<>();

        private RecordingNarrationAudioService(boolean fail) {
            this.fail = fail;
        }

        @Override
        public NarrationGenerationVo generateAudio(String jobId) {
            calls.add(jobId);
            if (fail) {
                throw new IllegalStateException("audio failed");
            }
            return new NarrationGenerationVo(
                    jobId,
                    "audio-artifact",
                    "narration-audio.mp3",
                    "audio/mpeg",
                    1024,
                    3,
                    72,
                    new BigDecimal("18.500"),
                    "PRESET:demo-voice",
                    "mixed",
                    true,
                    3,
                    "READY"
            );
        }
    }

    private static class RecordingNarratedVideoService implements NarratedVideoService {
        private final boolean fail;
        private final List<String> calls = new ArrayList<>();

        private RecordingNarratedVideoService(boolean fail) {
            this.fail = fail;
        }

        @Override
        public NarratedVideoGenerationVo generateVideo(String jobId) {
            calls.add(jobId);
            if (fail) {
                throw new IllegalStateException("video failed");
            }
            return new NarratedVideoGenerationVo(
                    jobId,
                    "video-artifact",
                    "narrated-video.mp4",
                    "video/mp4",
                    2048,
                    "BURNED_VIDEO",
                    "audio-artifact",
                    "ducked",
                    new BigDecimal("0.350"),
                    BigDecimal.ONE,
                    250,
                    3,
                    "READY"
            );
        }
    }

    private static class RecordingPlaybackReviewService implements NarrationPlaybackReviewService {
        private final List<String> calls = new ArrayList<>();

        @Override
        public NarrationPlaybackReviewVo getReview(String jobId) {
            calls.add(jobId);
            return new NarrationPlaybackReviewVo(
                    jobId,
                    Instant.parse("2026-07-01T00:00:00Z"),
                    "ATTENTION",
                    "Review playback.",
                    3,
                    0,
                    0,
                    0,
                    0,
                    3,
                    true,
                    1,
                    true,
                    1,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        @Override
        public NarrationPlaybackReviewVo updateSegmentReview(String jobId, int segmentIndex, com.linguaframe.job.domain.dto.UpdateNarrationPlaybackReviewSegmentDto request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String renderMarkdown(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private static class RecordingDeliveryPackageService implements NarrationDeliveryPackageService {
        private final List<String> calls = new ArrayList<>();

        @Override
        public NarrationDeliveryPackageVo getSummary(String jobId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public NarrationDeliveryPackageVo getPackage(String jobId) {
            calls.add(jobId);
            return new NarrationDeliveryPackageVo(
                    jobId,
                    Instant.parse("2026-07-01T00:00:00Z"),
                    "READY",
                    "NARRATION_DELIVERY_READY",
                    "Review package.",
                    true,
                    true,
                    0,
                    "ATTENTION",
                    "READY",
                    "ATTENTION",
                    "ATTENTION",
                    "READY",
                    "READY",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        @Override
        public String renderMarkdown(String jobId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.linguaframe.job.domain.bo.StoredNarrationDeliveryPackageBo openPackage(String jobId) {
            throw new UnsupportedOperationException();
        }
    }
}
