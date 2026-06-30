package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.StoredNarrationRecoveryHandoffPackageBo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateCheckVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateEvidenceVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateLinkVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateRunbookStepVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewResolutionSegmentVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewResolutionVo;
import com.linguaframe.job.domain.vo.NarrationRecoveryHandoffVo;
import com.linguaframe.job.service.impl.NarrationRecoveryHandoffServiceImpl;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class NarrationRecoveryHandoffServiceTests {

    private static final Instant NOW = Instant.parse("2026-06-30T09:15:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void buildsBlockedRecoveryHandoffWithSafePackageArtifacts() throws IOException {
        NarrationRecoveryHandoffService service = service(
                blockedGate("job-recovery"),
                unresolvedResolution("job-recovery")
        );

        NarrationRecoveryHandoffVo handoff = service.getHandoff("job-recovery");

        assertThat(handoff.jobId()).isEqualTo("job-recovery");
        assertThat(handoff.videoId()).isEqualTo("video-recovery");
        assertThat(handoff.generatedAt()).isEqualTo(NOW);
        assertThat(handoff.status()).isEqualTo("BLOCKED");
        assertThat(handoff.phase()).isEqualTo("NARRATION_RECOVERY_BLOCKED");
        assertThat(handoff.acceptanceGateStatus()).isEqualTo("BLOCKED");
        assertThat(handoff.playbackResolutionStatus()).isEqualTo("ATTENTION");
        assertThat(handoff.unresolvedSegmentCount()).isEqualTo(2);
        assertThat(handoff.textRevisionRequiredCount()).isEqualTo(1);
        assertThat(handoff.rerenderRequiredCount()).isEqualTo(1);
        assertThat(handoff.audioReady()).isTrue();
        assertThat(handoff.videoReady()).isFalse();
        assertThat(handoff.checks()).extracting("key")
                .contains("ACCEPTANCE_GATE", "PLAYBACK_RESOLUTION", "NARRATED_VIDEO_READY");
        assertThat(handoff.steps()).extracting("key")
                .contains("NARRATION_PLAYBACK_RESOLVED", "SEGMENT_0", "SEGMENT_1");
        assertThat(handoff.safeLinks()).extracting("href")
                .contains(
                        "/api/jobs/job-recovery/narration-recovery-handoff",
                        "/api/jobs/job-recovery/narration-recovery-handoff/markdown/download",
                        "/api/jobs/job-recovery/narration-recovery-handoff/download",
                        "/api/jobs/job-recovery/demo-acceptance-gate",
                        "/api/jobs/job-recovery/narration-playback-review/resolution"
                );
        assertThat(handoff.packageEntries())
                .contains(
                        "narration-recovery-handoff.json",
                        "narration-recovery-handoff.md",
                        "acceptance-gate.json",
                        "playback-resolution.json",
                        "README.md",
                        "manifest.json"
                );

        String markdown = service.renderMarkdown("job-recovery");
        StoredNarrationRecoveryHandoffPackageBo handoffPackage = service.openPackage("job-recovery");
        Map<String, String> entries = zipEntries(handoffPackage.inputStream());
        String combined = markdown + "\n" + handoff + "\n" + String.join("\n", entries.values());

        assertThat(markdown)
                .contains("# LinguaFrame Narration Recovery Handoff")
                .contains("- Status: BLOCKED")
                .contains("- Unresolved segments: 2")
                .contains("Resolve narration playback");
        assertThat(handoffPackage.filename()).isEqualTo("linguaframe-job-job-recovery-narration-recovery-handoff.zip");
        assertThat(handoffPackage.contentType()).isEqualTo("application/zip");
        assertThat(entries.keySet()).containsAll(handoff.packageEntries());
        assertThat(entries.get("README.md")).contains("metadata-only");
        assertThat(combined)
                .doesNotContain("Explain the first scene")
                .doesNotContain("Do not leak this playback resolution note")
                .doesNotContain("raw transcript text")
                .doesNotContain("raw subtitle text")
                .doesNotContain("corrected draft text")
                .doesNotContain("objectKey")
                .doesNotContain("job-artifacts/")
                .doesNotContain("/Users/example")
                .doesNotContain("provider payload")
                .doesNotContain("sk-test")
                .doesNotContain("OPENAI_API_KEY");
    }

    @Test
    void returnsReadyForResolvedNarrationPlayback() {
        NarrationRecoveryHandoffVo handoff = service(
                readyGate("job-ready"),
                readyResolution("job-ready")
        ).getHandoff("job-ready");

        assertThat(handoff.status()).isEqualTo("READY");
        assertThat(handoff.phase()).isEqualTo("NARRATION_RECOVERY_READY");
        assertThat(handoff.unresolvedSegmentCount()).isZero();
        assertThat(handoff.steps()).isEmpty();
        assertThat(handoff.recommendedNextAction()).contains("Continue with final handoff");
    }

    private static NarrationRecoveryHandoffService service(
            DemoAcceptanceGateVo gate,
            NarrationPlaybackReviewResolutionVo resolution
    ) {
        return new NarrationRecoveryHandoffServiceImpl(
                new StaticDemoAcceptanceGateService(gate),
                new StaticNarrationPlaybackReviewResolutionService(resolution),
                CLOCK
        );
    }

    private static DemoAcceptanceGateVo blockedGate(String jobId) {
        return gate(
                jobId,
                "BLOCKED",
                "Resolve narration playback before final demo handoff.",
                List.of(new DemoAcceptanceGateRunbookStepVo(
                        "NARRATION_PLAYBACK_RESOLVED",
                        "Resolve narration playback",
                        "BLOCKED",
                        "Narration playback resolution status=ATTENTION; unresolved=2; textRevision=1; rerender=1; unreviewed=0. Do not leak this playback resolution note.",
                        "Open playback resolution, focus unresolved narration rows, save revisions, regenerate narration media, then re-run acceptance gate.",
                        "LINGUAFRAME_DEMO_JOB_ID=" + jobId + " scripts/demo/narration-playback-review-resolution.sh",
                        "/api/jobs/" + jobId + "/narration-playback-review/resolution"
                )),
                List.of(
                        new DemoAcceptanceGateCheckVo("JOB_COMPLETED", "Job completed", "PASS", "Job completed.", true),
                        new DemoAcceptanceGateCheckVo("NARRATION_PLAYBACK_RESOLVED", "Narration playback resolved", "FAIL", "Narration playback resolution status=ATTENTION; unresolved=2.", true)
                )
        );
    }

    private static DemoAcceptanceGateVo readyGate(String jobId) {
        return gate(jobId, "READY", "Continue with final handoff.", List.of(), List.of(
                new DemoAcceptanceGateCheckVo("JOB_COMPLETED", "Job completed", "PASS", "Job completed.", true),
                new DemoAcceptanceGateCheckVo("NARRATION_PLAYBACK_RESOLVED", "Narration playback resolved", "PASS", "Playback review is resolved.", true)
        ));
    }

    private static DemoAcceptanceGateVo gate(
            String jobId,
            String status,
            String nextAction,
            List<DemoAcceptanceGateRunbookStepVo> runbookSteps,
            List<DemoAcceptanceGateCheckVo> checks
    ) {
        return new DemoAcceptanceGateVo(
                jobId,
                "video-recovery",
                NOW,
                status,
                LocalizationJobStatus.COMPLETED,
                "zh-CN",
                "tears-showcase",
                "Narration recovery handoff",
                "Recovery gate summary.",
                nextAction,
                runbookSteps,
                checks,
                List.of(new DemoAcceptanceGateEvidenceVo("NARRATION_PLAYBACK_UNRESOLVED_COUNT", "Narration unresolved rows", status.equals("READY") ? "0" : "2", status)),
                List.of(new DemoAcceptanceGateLinkVo("ACCEPTANCE_GATE_JSON", "Acceptance gate", "/api/jobs/" + jobId + "/demo-acceptance-gate")),
                List.of("Acceptance gate is metadata-only.")
        );
    }

    private static NarrationPlaybackReviewResolutionVo unresolvedResolution(String jobId) {
        return new NarrationPlaybackReviewResolutionVo(
                jobId,
                NOW,
                "ATTENTION",
                "Resolve playback review issues, save narration edits, and regenerate narration media before handoff.",
                3,
                1,
                2,
                1,
                1,
                0,
                true,
                1,
                false,
                0,
                List.of(
                        new NarrationPlaybackReviewResolutionSegmentVo(
                                0,
                                new BigDecimal("15"),
                                new BigDecimal("28"),
                                new BigDecimal("13"),
                                "NEEDS_EDIT",
                                "TEXT_REVISION_REQUIRED",
                                List.of("TEXT"),
                                "Focus this row in the narration editor, revise the saved script or voice choice, save narration, then regenerate audio/video.",
                                true,
                                NOW.minusSeconds(60)
                        ),
                        new NarrationPlaybackReviewResolutionSegmentVo(
                                1,
                                new BigDecimal("55"),
                                new BigDecimal("70"),
                                new BigDecimal("15"),
                                "NEEDS_RERENDER",
                                "RERENDER_REQUIRED",
                                List.of("MIX", "VIDEO"),
                                "Regenerate narration audio/video after confirming mix, timing, and media artifacts.",
                                true,
                                NOW.minusSeconds(30)
                        )
                ),
                List.of(),
                List.of("Resolution is metadata-only.")
        );
    }

    private static NarrationPlaybackReviewResolutionVo readyResolution(String jobId) {
        return new NarrationPlaybackReviewResolutionVo(
                jobId,
                NOW,
                "READY",
                "Playback review is resolved and narrated video is ready for demo handoff.",
                2,
                2,
                0,
                0,
                0,
                0,
                true,
                1,
                true,
                1,
                List.of(),
                List.of(),
                List.of("Resolution is metadata-only.")
        );
    }

    private static Map<String, String> zipEntries(InputStream inputStream) throws IOException {
        java.util.LinkedHashMap<String, String> entries = new java.util.LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(inputStream)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                zip.transferTo(output);
                entries.put(entry.getName(), output.toString(StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    private record StaticDemoAcceptanceGateService(DemoAcceptanceGateVo gate) implements DemoAcceptanceGateService {
        @Override
        public DemoAcceptanceGateVo buildGate(String jobId) {
            return gate;
        }
    }

    private record StaticNarrationPlaybackReviewResolutionService(NarrationPlaybackReviewResolutionVo resolution)
            implements NarrationPlaybackReviewResolutionService {
        @Override
        public NarrationPlaybackReviewResolutionVo getResolution(String jobId) {
            return resolution;
        }

        @Override
        public String renderMarkdown(String jobId) {
            return "# Narration Playback Resolution\n";
        }
    }
}
