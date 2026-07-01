package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.StoredArtifactArchiveBo;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.QualityEvaluationStatus;
import com.linguaframe.job.domain.vo.DeliveryManifestVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateRunbookStepVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateVo;
import com.linguaframe.job.domain.vo.DemoCompletionCertificateCheckVo;
import com.linguaframe.job.domain.vo.DemoCompletionCertificateLinkVo;
import com.linguaframe.job.domain.vo.DemoCompletionCertificateSectionVo;
import com.linguaframe.job.domain.vo.DemoCompletionCertificateVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackDownloadVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackRunVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackVo;
import com.linguaframe.job.domain.vo.DemoReplayCardCommandVo;
import com.linguaframe.job.domain.vo.DemoReplayCardLinkVo;
import com.linguaframe.job.domain.vo.DemoReplayCardSettingVo;
import com.linguaframe.job.domain.vo.DemoReplayCardVo;
import com.linguaframe.job.domain.vo.DemoRunMatrixJobVo;
import com.linguaframe.job.domain.vo.DemoRunMatrixVo;
import com.linguaframe.job.domain.vo.DemoRunSnapshotLinkVo;
import com.linguaframe.job.domain.vo.DemoRunSnapshotSectionVo;
import com.linguaframe.job.domain.vo.DemoRunSnapshotVo;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewResolutionSegmentVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewResolutionVo;
import com.linguaframe.job.domain.vo.NarrationDeliveryPackageArtifactVo;
import com.linguaframe.job.domain.vo.NarrationDeliveryPackageCheckVo;
import com.linguaframe.job.domain.vo.NarrationDeliveryPackageLinkVo;
import com.linguaframe.job.domain.vo.NarrationDeliveryPackageVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.service.impl.DemoAcceptanceGateServiceImpl;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.domain.vo.MediaUploadDetailVo;
import com.linguaframe.media.domain.vo.MediaUploadVo;
import com.linguaframe.media.service.MediaUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoAcceptanceGateServiceTests {

    private static final Instant NOW = Instant.parse("2026-06-29T12:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void buildsReadyAcceptanceGateFromCompletedRunEvidence() {
        LocalizationJobVo job = job("job-acceptance", LocalizationJobStatus.COMPLETED, 91, "GOOD", "0.00007800", 2);
        DemoAcceptanceGateService service = service(job, artifacts(true, true, true), true, "READY", matrix(job, "job-baseline"));

        DemoAcceptanceGateVo gate = service.buildGate("job-acceptance");

        assertThat(gate.jobId()).isEqualTo("job-acceptance");
        assertThat(gate.videoId()).isEqualTo("video-acceptance");
        assertThat(gate.generatedAt()).isEqualTo(NOW);
        assertThat(gate.gateStatus()).isEqualTo("READY");
        assertThat(gate.checks()).extracting("status").containsOnly("PASS");
        assertThat(gate.evidence()).extracting("key")
                .contains(
                        "SOURCE_DURATION",
                        "SUBTITLE_OUTPUT_COUNT",
                        "MEDIA_OUTPUT_COUNT",
                        "QUALITY_SCORE",
                        "CERTIFICATE_STATUS",
                        "CUSTOM_NARRATION_RENDER_STATUS",
                        "CUSTOM_NARRATION_RENDER_OUTPUT_PLAN",
                        "CUSTOM_NARRATION_RENDER_SEGMENT_COUNT",
                        "NARRATION_DELIVERY_PACKAGE_STATUS",
                        "NARRATION_DELIVERY_AUDIO_READY",
                        "NARRATION_DELIVERY_VIDEO_READY",
                        "NARRATION_DELIVERY_PACKAGE_ENTRY_COUNT"
                );
        assertThat(gate.links()).extracting("kind")
                .contains(
                        "ACCEPTANCE_GATE_JSON",
                        "COMPLETION_CERTIFICATE_JSON",
                        "DEMO_RUN_PACKAGE",
                        "SNAPSHOT_DOWNLOAD",
                        "CUSTOM_NARRATION_RENDER_REPORT",
                        "NARRATION_DELIVERY_PACKAGE_JSON",
                        "NARRATION_DELIVERY_PACKAGE_MARKDOWN",
                        "NARRATION_DELIVERY_PACKAGE_ZIP"
                );
        assertThat(gate.checks())
                .anySatisfy(check -> {
                    assertThat(check.key()).isEqualTo("NARRATION_DELIVERY_PACKAGE_READY");
                    assertThat(check.status()).isEqualTo("PASS");
                    assertThat(check.required()).isFalse();
                    assertThat(check.detail()).contains("status=READY").contains("audioReady=true").contains("videoReady=true");
                });
        assertThat(gate.customNarrationRender().status()).isEqualTo("NOT_APPLICABLE");
        assertThat(gate.customNarrationRender().reportRoute())
                .isEqualTo("/api/jobs/job-acceptance/custom-narration-render/markdown/download");
        assertThat(gate.checks())
                .anySatisfy(check -> {
                    assertThat(check.key()).isEqualTo("CUSTOM_NARRATION_RENDER_HANDOFF");
                    assertThat(check.status()).isEqualTo("PASS");
                    assertThat(check.required()).isFalse();
                    assertThat(check.detail()).contains("outputPlan=No saved custom narration rows");
                });
        assertThat(gate.recommendedNextAction()).contains("Present this run");
        assertThat(gate.runbookSteps()).isEmpty();
        assertThat(gate.toString())
                .doesNotContain("sk-test")
                .doesNotContain("/Users/example")
                .doesNotContain("provider payload")
                .doesNotContain("raw transcript text");
    }

    @Test
    void marksCompletedRunAttentionWhenWarningsRemain() {
        LocalizationJobVo job = job("job-attention", LocalizationJobStatus.COMPLETED, 72, "GOOD", "1.25000000", 0);
        DemoAcceptanceGateService service = service(job, artifacts(true, true, false), false, "READY", matrix(job, null));

        DemoAcceptanceGateVo gate = service.buildGate("job-attention");

        assertThat(gate.gateStatus()).isEqualTo("ATTENTION");
        assertThat(gate.checks())
                .anySatisfy(check -> {
                    assertThat(check.key()).isEqualTo("REVIEWED_HANDOFF_AVAILABLE");
                    assertThat(check.status()).isEqualTo("WARN");
                    assertThat(check.required()).isFalse();
                })
                .anySatisfy(check -> {
                    assertThat(check.key()).isEqualTo("BASELINE_RECOMMENDED");
                    assertThat(check.status()).isEqualTo("WARN");
                })
                .anySatisfy(check -> {
                    assertThat(check.key()).isEqualTo("QUALITY_SCORE_DEMO_READY");
                    assertThat(check.status()).isEqualTo("WARN");
                });
        assertThat(gate.runbookSteps())
                .anySatisfy(step -> {
                    assertThat(step.key()).isEqualTo("REVIEWED_HANDOFF_AVAILABLE");
                    assertThat(step.status()).isEqualTo("ATTENTION");
                    assertThat(step.primaryAction()).isEqualTo("Review this acceptance check, resolve the cited evidence, then re-run the acceptance gate.");
                    assertThat(step.safeCommand()).isEqualTo("LINGUAFRAME_DEMO_JOB_ID=job-attention scripts/demo/demo-acceptance-gate.sh");
                    assertThat(step.safeLink()).isEqualTo("/api/jobs/job-attention/demo-acceptance-gate");
                });
    }

    @Test
    void blocksGateWhenJobIsNotCompleted() {
        LocalizationJobVo job = job("job-running", LocalizationJobStatus.PROCESSING, 91, "GOOD", "0.00007800", 2);
        DemoAcceptanceGateService service = service(job, artifacts(true, true, true), true, "BLOCKED", matrix(job, "job-baseline"));

        DemoAcceptanceGateVo gate = service.buildGate("job-running");

        assertThat(gate.gateStatus()).isEqualTo("BLOCKED");
        assertThat(gate.checks())
                .anySatisfy(check -> {
                    assertThat(check.key()).isEqualTo("JOB_COMPLETED");
                    assertThat(check.status()).isEqualTo("FAIL");
                    assertThat(check.required()).isTrue();
                });
        assertThat(gate.safetyNotes()).contains("Incomplete jobs are blocked from demo acceptance until processing reaches COMPLETED.");
    }

    @Test
    void blocksGateWhenMediaOutputIsMissing() {
        LocalizationJobVo job = job("job-no-media", LocalizationJobStatus.COMPLETED, 91, "GOOD", "0.00007800", 2);
        DemoAcceptanceGateService service = service(job, artifacts(true, false, true), true, "READY", matrix(job, "job-baseline"));

        DemoAcceptanceGateVo gate = service.buildGate("job-no-media");

        assertThat(gate.gateStatus()).isEqualTo("BLOCKED");
        assertThat(gate.checks())
                .anySatisfy(check -> {
                    assertThat(check.key()).isEqualTo("MEDIA_OUTPUT_AVAILABLE");
                    assertThat(check.status()).isEqualTo("FAIL");
                    assertThat(check.required()).isTrue();
                });
    }

    @Test
    void blocksGateWhenNarrationPlaybackResolutionHasUnresolvedRows() {
        LocalizationJobVo job = job("job-narration-blocked", LocalizationJobStatus.COMPLETED, 91, "GOOD", "0.00007800", 2);
        DemoAcceptanceGateService service = service(
                job,
                artifacts(true, true, true),
                true,
                "READY",
                matrix(job, "job-baseline"),
                unresolvedNarrationResolution(job)
        );

        DemoAcceptanceGateVo gate = service.buildGate("job-narration-blocked");

        assertThat(gate.gateStatus()).isEqualTo("BLOCKED");
        assertThat(gate.checks())
                .anySatisfy(check -> {
                    assertThat(check.key()).isEqualTo("NARRATION_PLAYBACK_RESOLVED");
                    assertThat(check.status()).isEqualTo("FAIL");
                    assertThat(check.required()).isTrue();
                    assertThat(check.detail())
                            .contains("status=ATTENTION")
                            .contains("unresolved=2")
                            .contains("textRevision=1")
                            .contains("rerender=1")
                            .contains("unreviewed=0");
                });
        assertThat(gate.evidence())
                .anySatisfy(evidence -> {
                    assertThat(evidence.key()).isEqualTo("NARRATION_PLAYBACK_RESOLUTION_STATUS");
                    assertThat(evidence.value()).isEqualTo("ATTENTION");
                    assertThat(evidence.status()).isEqualTo("BLOCKED");
                })
                .anySatisfy(evidence -> {
                    assertThat(evidence.key()).isEqualTo("NARRATION_PLAYBACK_UNRESOLVED_COUNT");
                    assertThat(evidence.value()).isEqualTo("2");
                    assertThat(evidence.status()).isEqualTo("BLOCKED");
                });
        assertThat(gate.links()).extracting("kind")
                .contains(
                        "NARRATION_PLAYBACK_RESOLUTION_JSON",
                        "NARRATION_PLAYBACK_RESOLUTION_MARKDOWN",
                        "NARRATION_DELIVERY_PACKAGE_JSON",
                        "NARRATION_DELIVERY_PACKAGE_MARKDOWN",
                        "NARRATION_DELIVERY_PACKAGE_ZIP"
                );
        assertThat(gate.runbookSteps())
                .anySatisfy(step -> {
                    assertThat(step).isEqualTo(new DemoAcceptanceGateRunbookStepVo(
                            "NARRATION_PLAYBACK_RESOLVED",
                            "Resolve narration playback",
                            "BLOCKED",
                            "Narration playback resolution status=ATTENTION; unresolved=2; textRevision=1; rerender=1; unreviewed=0.",
                            "Open playback resolution, focus unresolved narration rows, save revisions, regenerate narration media, then re-run acceptance gate.",
                            "LINGUAFRAME_DEMO_JOB_ID=job-narration-blocked scripts/demo/narration-playback-review-resolution.sh",
                            "/api/jobs/job-narration-blocked/narration-playback-review/resolution"
                    ));
                });
        assertThat(gate.safetyNotes())
                .contains("Narration playback resolution is included as metadata-only counts and safe routes; narration text and reviewer note bodies are excluded.");
        assertThat(gate.toString())
                .doesNotContain("Explain the first scene")
                .doesNotContain("Do not leak this playback resolution note")
                .doesNotContain("sk-test")
                .doesNotContain("/Users/example")
                .doesNotContain("provider payload");
    }

    @Test
    void doesNotBlockGateWhenNarrationRowsAreAbsent() {
        LocalizationJobVo job = job("job-no-narration", LocalizationJobStatus.COMPLETED, 91, "GOOD", "0.00007800", 2);
        DemoAcceptanceGateService service = service(
                job,
                artifacts(true, true, true),
                true,
                "READY",
                matrix(job, "job-baseline"),
                noNarrationResolution(job)
        );

        DemoAcceptanceGateVo gate = service.buildGate("job-no-narration");

        assertThat(gate.gateStatus()).isEqualTo("READY");
        assertThat(gate.checks())
                .anySatisfy(check -> {
                    assertThat(check.key()).isEqualTo("NARRATION_PLAYBACK_RESOLVED");
                    assertThat(check.status()).isEqualTo("PASS");
                    assertThat(check.required()).isTrue();
                    assertThat(check.detail()).contains("No narration rows are saved");
                });
    }

    private static DemoAcceptanceGateService service(
            LocalizationJobVo job,
            List<JobArtifactVo> artifacts,
            boolean handoffReady,
            String certificateStatus,
            DemoRunMatrixVo matrix
    ) {
        return service(job, artifacts, handoffReady, certificateStatus, matrix, readyNarrationResolution(job));
    }

    private static DemoAcceptanceGateService service(
            LocalizationJobVo job,
            List<JobArtifactVo> artifacts,
            boolean handoffReady,
            String certificateStatus,
            DemoRunMatrixVo matrix,
            NarrationPlaybackReviewResolutionVo narrationResolution
    ) {
        return new DemoAcceptanceGateServiceImpl(
                new StaticQueryService(job),
                new StaticMediaUploadService(upload(job)),
                new StaticArtifactService(artifacts),
                new StaticDeliveryManifestService(manifest(job, handoffReady)),
                new StaticCompletionCertificateService(certificate(job, certificateStatus)),
                new StaticPresenterPackService(presenterPack(job)),
                new StaticReplayCardService(replayCard(job, matrix)),
                new StaticSnapshotService(snapshot(job)),
                new StaticMatrixService(matrix),
                new StaticNarrationPlaybackReviewResolutionService(narrationResolution),
                new StaticNarrationDeliveryPackageService(deliveryPackage(job, narrationResolution)),
                CLOCK
        );
    }

    private static LocalizationJobVo job(
            String jobId,
            LocalizationJobStatus status,
            int qualityScore,
            String qualityVerdict,
            String estimatedCostUsd,
            int providerCacheHits
    ) {
        return new LocalizationJobVo(
                jobId,
                "video-acceptance",
                "zh-CN",
                "verse",
                "FORMAL",
                "HIGH_CONTRAST",
                3,
                "abc123",
                "BALANCED",
                "tears-showcase",
                status,
                NOW.minusSeconds(120),
                NOW.minusSeconds(90),
                status == LocalizationJobStatus.COMPLETED ? NOW.minusSeconds(10) : null,
                null,
                null,
                null,
                0,
                JobDispatchEventStatus.DISPATCHED,
                0,
                NOW.minusSeconds(110),
                List.of(),
                new JobUsageSummaryVo(3, 0, 4200, new BigDecimal(estimatedCostUsd), 100, 80, new BigDecimal("45.0"), 1200),
                new JobCacheSummaryVo(1, 6, providerCacheHits),
                List.of(),
                quality(jobId, qualityScore, qualityVerdict),
                null,
                null
        );
    }

    private static QualityEvaluationVo quality(String jobId, int score, String verdict) {
        return new QualityEvaluationVo(
                "quality-" + jobId,
                jobId,
                "zh-CN",
                score,
                verdict,
                score,
                score,
                score,
                score,
                List.of(),
                List.of(),
                QualityEvaluationStatus.SUCCEEDED,
                null,
                NOW.minusSeconds(20)
        );
    }

    private static MediaUploadDetailVo upload(LocalizationJobVo job) {
        return new MediaUploadDetailVo(
                job.videoId(),
                "tears-demo.mp4",
                "video/mp4",
                123_456_789L,
                732,
                MediaUploadStatus.UPLOADED,
                NOW.minusSeconds(180)
        );
    }

    private static List<JobArtifactVo> artifacts(boolean includeSubtitle, boolean includeMedia, boolean includeReviewed) {
        java.util.ArrayList<JobArtifactVo> artifacts = new java.util.ArrayList<>();
        if (includeSubtitle) {
            artifacts.add(artifact("target-srt", JobArtifactType.TARGET_SUBTITLE_SRT));
        }
        if (includeMedia) {
            artifacts.add(artifact("dubbed-video", JobArtifactType.DUBBED_VIDEO));
        }
        if (includeReviewed) {
            artifacts.add(artifact("reviewed-srt", JobArtifactType.REVIEWED_SUBTITLE_SRT));
        }
        return List.copyOf(artifacts);
    }

    private static JobArtifactVo artifact(String id, JobArtifactType type) {
        return new JobArtifactVo(id, "job-acceptance", type, id + ".dat", "application/octet-stream", 1234L, "abc123", false, null, NOW);
    }

    private static DeliveryManifestVo manifest(LocalizationJobVo job, boolean handoffReady) {
        return new DeliveryManifestVo(
                job.jobId(),
                job.videoId(),
                job.targetLanguage(),
                job.subtitleStylePreset(),
                job.translationGlossaryEntryCount(),
                job.translationGlossaryHash(),
                job.subtitlePolishingMode(),
                job.demoProfileId(),
                job.status(),
                NOW,
                handoffReady,
                handoffReady ? 3 : 0,
                false,
                6,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static DemoCompletionCertificateVo certificate(LocalizationJobVo job, String status) {
        return new DemoCompletionCertificateVo(
                job.jobId(),
                job.videoId(),
                NOW,
                status,
                job.status(),
                job.targetLanguage(),
                job.demoProfileId(),
                "tears-showcase completion certificate",
                "Certificate summary.",
                "Use the completion certificate.",
                "job-baseline",
                job.jobId(),
                "job-baseline",
                List.of(new DemoCompletionCertificateCheckVo("JOB_COMPLETED", "Job completed", "READY".equals(status) ? "PASS" : "FAIL", "Job status.", true)),
                List.of(new DemoCompletionCertificateSectionVo("EVIDENCE", "Evidence", status, List.of("Demo run package ready."))),
                List.of(
                        new DemoCompletionCertificateLinkVo("CERTIFICATE_JSON", "Completion certificate JSON", "/api/jobs/%s/demo-completion-certificate".formatted(job.jobId())),
                        new DemoCompletionCertificateLinkVo("DEMO_RUN_PACKAGE", "Demo run package", "/api/jobs/%s/demo-run-package/download".formatted(job.jobId()))
                ),
                List.of("Metadata-only certificate.")
        );
    }

    private static DemoPresenterPackVo presenterPack(LocalizationJobVo job) {
        return new DemoPresenterPackVo(
                job.jobId(),
                job.videoId(),
                NOW,
                "tears-showcase demo to zh-CN",
                "READY",
                "job-baseline",
                job.jobId(),
                "job-baseline",
                List.of(new DemoPresenterPackRunVo(job.jobId(), job.demoProfileId(), job.status(), job.completedAt(), 91, new BigDecimal("0.00007800"), 3, 2, true, List.of("ANCHOR"))),
                List.of(new DemoPresenterPackDownloadVo("DEMO_RUN_PACKAGE", "Demo run package", "/api/jobs/%s/demo-run-package/download".formatted(job.jobId()))),
                "# Presenter pack\n"
        );
    }

    private static DemoReplayCardVo replayCard(LocalizationJobVo job, DemoRunMatrixVo matrix) {
        return new DemoReplayCardVo(
                job.jobId(),
                job.videoId(),
                NOW,
                "tears-showcase replay card to zh-CN",
                "READY",
                job.status(),
                job.targetLanguage(),
                job.demoProfileId(),
                91,
                "GOOD",
                3,
                2,
                1,
                new BigDecimal("0.00007800"),
                matrix.recommendedBaselineJobId(),
                matrix.bestQualityJobId(),
                matrix.lowestCostJobId(),
                List.of(new DemoReplayCardSettingVo("demoProfileId", "Demo profile", "tears-showcase")),
                List.of(new DemoReplayCardCommandVo("EXPORT_REPLAY_CARD", "Export this replay card", "LINGUAFRAME_DEMO_JOB_ID=%s scripts/demo/demo-replay-card.sh".formatted(job.jobId()), "Writes JSON.")),
                List.of(new DemoReplayCardLinkVo("REPLAY_CARD_JSON", "Replay card JSON", "/api/jobs/%s/demo-replay-card".formatted(job.jobId()))),
                List.of("Metadata-only replay card.")
        );
    }

    private static DemoRunSnapshotVo snapshot(LocalizationJobVo job) {
        return new DemoRunSnapshotVo(
                job.jobId(),
                job.videoId(),
                job.targetLanguage(),
                job.demoProfileId(),
                NOW,
                "READY",
                "tears-showcase demo to zh-CN",
                "Static snapshot.",
                List.of(new DemoRunSnapshotSectionVo("INDEX_HTML", "Offline index", "READY", "index.html", "Self-contained index.")),
                List.of("index.html", "manifest.json", "README.md"),
                List.of(new DemoRunSnapshotLinkVo("SNAPSHOT_DOWNLOAD", "Static snapshot ZIP", "/api/jobs/%s/demo-run-snapshot/download".formatted(job.jobId()))),
                List.of("media bytes", "provider payloads"),
                "# Snapshot\n"
        );
    }

    private static DemoRunMatrixVo matrix(LocalizationJobVo job, String baselineJobId) {
        return new DemoRunMatrixVo(
                job.jobId(),
                job.videoId(),
                NOW,
                baselineJobId == null ? List.of(matrixJob(job)) : List.of(matrixJob(job), matrixJob(job, baselineJobId, "quick-baseline")),
                baselineJobId,
                job.jobId(),
                baselineJobId
        );
    }

    private static DemoRunMatrixJobVo matrixJob(LocalizationJobVo job) {
        return matrixJob(job, job.jobId(), job.demoProfileId());
    }

    private static DemoRunMatrixJobVo matrixJob(LocalizationJobVo job, String jobId, String demoProfileId) {
        return new DemoRunMatrixJobVo(
                jobId,
                job.videoId(),
                "tears-demo.mp4",
                job.targetLanguage(),
                demoProfileId,
                job.ttsVoice(),
                job.translationStyle(),
                job.subtitleStylePreset(),
                job.translationGlossaryEntryCount(),
                job.translationGlossaryHash(),
                job.subtitlePolishingMode(),
                job.status(),
                job.createdAt(),
                job.completedAt(),
                null,
                null,
                0,
                91,
                "GOOD",
                3,
                0,
                new BigDecimal("0.00007800"),
                1,
                6,
                2,
                true
        );
    }

    private static NarrationPlaybackReviewResolutionVo readyNarrationResolution(LocalizationJobVo job) {
        return new NarrationPlaybackReviewResolutionVo(
                job.jobId(),
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

    private static NarrationPlaybackReviewResolutionVo unresolvedNarrationResolution(LocalizationJobVo job) {
        return new NarrationPlaybackReviewResolutionVo(
                job.jobId(),
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

    private static NarrationPlaybackReviewResolutionVo noNarrationResolution(LocalizationJobVo job) {
        return new NarrationPlaybackReviewResolutionVo(
                job.jobId(),
                NOW,
                "BLOCKED",
                "Save narration segments before resolving playback review.",
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                0,
                false,
                0,
                List.of(),
                List.of(),
                List.of("Resolution is metadata-only.")
        );
    }

    private static NarrationDeliveryPackageVo deliveryPackage(
            LocalizationJobVo job,
            NarrationPlaybackReviewResolutionVo resolution
    ) {
        String status = resolution.segmentCount() == 0 ? "EMPTY" : resolution.unresolvedSegmentCount() == 0 ? "READY" : "BLOCKED";
        boolean ready = "READY".equals(status);
        return new NarrationDeliveryPackageVo(
                job.jobId(),
                NOW,
                status,
                ready ? "NARRATION_DELIVERY_READY" : "EMPTY".equals(status) ? "NARRATION_DELIVERY_EMPTY" : "NARRATION_DELIVERY_BLOCKED",
                ready ? "Download the narration delivery package and continue with final handoff." : "Resolve narration playback before final handoff.",
                ready,
                ready,
                resolution.unresolvedSegmentCount(),
                ready ? "READY" : "ATTENTION",
                "READY",
                ready ? "READY" : "ATTENTION",
                ready ? "READY" : "ATTENTION",
                resolution.status(),
                ready ? "READY" : "BLOCKED",
                ready ? List.of(new NarrationDeliveryPackageArtifactVo("audio-artifact", "NARRATION_AUDIO", "narration-audio.mp3", "audio/mpeg", 1024, false, "/api/jobs/" + job.jobId() + "/artifacts/audio-artifact/download")) : List.of(),
                List.of(new NarrationDeliveryPackageCheckVo("NARRATION_PLAYBACK_RESOLUTION", "Playback resolution", ready ? "READY" : "BLOCKED", "Resolution status is " + resolution.status() + ".", "Re-run playback resolution.", true)),
                List.of(
                        new NarrationDeliveryPackageLinkVo("NARRATION_DELIVERY_PACKAGE_JSON", "Narration delivery package JSON", "/api/jobs/" + job.jobId() + "/narration-delivery-package", "application/json", "Delivery metadata."),
                        new NarrationDeliveryPackageLinkVo("NARRATION_DELIVERY_PACKAGE_MARKDOWN", "Narration delivery package Markdown", "/api/jobs/" + job.jobId() + "/narration-delivery-package/markdown/download", "text/markdown", "Delivery Markdown."),
                        new NarrationDeliveryPackageLinkVo("NARRATION_DELIVERY_PACKAGE_ZIP", "Narration delivery package ZIP", "/api/jobs/" + job.jobId() + "/narration-delivery-package/download", "application/zip", "Delivery ZIP.")
                ),
                List.of("manifest.json", "narration-delivery-package.json", "narration-delivery-package.md", "README.md"),
                List.of("Narration delivery package is metadata-only.")
        );
    }

    private record StaticQueryService(LocalizationJobVo job) implements LocalizationJobQueryService {
        @Override
        public com.linguaframe.job.domain.vo.LocalizationJobListVo listJobs(LocalizationJobStatus status, Integer limit, Integer offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LocalizationJobVo getJob(String jobId) {
            return job;
        }

        @Override
        public com.linguaframe.job.domain.vo.JobDiagnosticsReportVo getDiagnosticsReport(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticMediaUploadService(MediaUploadDetailVo upload) implements MediaUploadService {
        @Override
        public MediaUploadVo createUpload(MultipartFile file, String targetLanguage, String ttsVoice, String translationStyle, String subtitleStylePreset, String translationGlossary, String subtitlePolishingMode, String demoProfileId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MediaUploadDetailVo getUpload(String videoId) {
            return upload;
        }

        @Override
        public StoredObjectResourceBo openSourceMedia(String videoId) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticArtifactService(List<JobArtifactVo> artifacts) implements JobArtifactService {
        @Override
        public JobArtifactVo createArtifact(CreateJobArtifactCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JobArtifactVo createReusedArtifact(String jobId, JobArtifactRecord source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<JobArtifactVo> listArtifacts(String jobId) {
            return artifacts;
        }

        @Override
        public StoredObjectResourceBo openArtifact(String jobId, String artifactId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StoredArtifactArchiveBo openArtifactArchive(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticDeliveryManifestService(DeliveryManifestVo manifest) implements DeliveryManifestService {
        @Override
        public DeliveryManifestVo buildManifest(String jobId) {
            return manifest;
        }

        @Override
        public String buildMarkdownManifest(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticCompletionCertificateService(DemoCompletionCertificateVo certificate) implements DemoCompletionCertificateService {
        @Override
        public DemoCompletionCertificateVo buildCertificate(String jobId) {
            return certificate;
        }
    }

    private record StaticPresenterPackService(DemoPresenterPackVo presenterPack) implements DemoPresenterPackService {
        @Override
        public DemoPresenterPackVo buildPresenterPack(String jobId) {
            return presenterPack;
        }
    }

    private record StaticReplayCardService(DemoReplayCardVo replayCard) implements DemoReplayCardService {
        @Override
        public DemoReplayCardVo buildReplayCard(String jobId) {
            return replayCard;
        }
    }

    private record StaticSnapshotService(DemoRunSnapshotVo snapshot) implements DemoRunSnapshotService {
        @Override
        public DemoRunSnapshotVo buildSnapshot(String jobId) {
            return snapshot;
        }

        @Override
        public com.linguaframe.job.domain.bo.StoredDemoRunSnapshotPackageBo openSnapshotPackage(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticMatrixService(DemoRunMatrixVo matrix) implements DemoRunMatrixService {
        @Override
        public DemoRunMatrixVo buildMatrix(String anchorJobId, Integer limit) {
            return matrix;
        }
    }

    private record StaticNarrationPlaybackReviewResolutionService(
            NarrationPlaybackReviewResolutionVo resolution
    ) implements NarrationPlaybackReviewResolutionService {
        @Override
        public NarrationPlaybackReviewResolutionVo getResolution(String jobId) {
            return resolution;
        }

        @Override
        public String renderMarkdown(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticNarrationDeliveryPackageService(
            NarrationDeliveryPackageVo deliveryPackage
    ) implements NarrationDeliveryPackageService {
        @Override
        public NarrationDeliveryPackageVo getSummary(String jobId) {
            return deliveryPackage;
        }

        @Override
        public NarrationDeliveryPackageVo getPackage(String jobId) {
            return deliveryPackage;
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
