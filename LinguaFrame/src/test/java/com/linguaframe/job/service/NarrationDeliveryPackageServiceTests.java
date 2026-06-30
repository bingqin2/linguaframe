package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.StoredNarrationDeliveryPackageBo;
import com.linguaframe.job.domain.bo.StoredNarrationEvidencePackageBo;
import com.linguaframe.job.domain.bo.StoredNarrationRecoveryHandoffPackageBo;
import com.linguaframe.job.domain.bo.StoredNarrationScriptPackageBo;
import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.NarrationDeliveryPackageVo;
import com.linguaframe.job.domain.vo.NarrationEvidenceCheckVo;
import com.linguaframe.job.domain.vo.NarrationEvidenceLinkVo;
import com.linguaframe.job.domain.vo.NarrationEvidenceVo;
import com.linguaframe.job.domain.vo.NarrationMixSettingsVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewCategoryCountVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewResolutionVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewVo;
import com.linguaframe.job.domain.vo.NarrationRecoveryHandoffVo;
import com.linguaframe.job.domain.vo.NarrationRenderReviewVo;
import com.linguaframe.job.domain.vo.NarrationScriptPackageVo;
import com.linguaframe.job.domain.vo.NarrationVoiceCatalogVo;
import com.linguaframe.job.service.impl.NarrationDeliveryPackageServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class NarrationDeliveryPackageServiceTests {

    private static final Instant NOW = Instant.parse("2026-06-30T12:20:00Z");

    @Test
    void buildsReadyDeliveryPackageWithSafeMediaLinksAndZipEntries() throws IOException {
        NarrationDeliveryPackageService service = service(
                List.of(
                        artifact("audio-artifact", "job-delivery", JobArtifactType.NARRATION_AUDIO, "narration-audio.mp3", "audio/mpeg"),
                        artifact("video-artifact", "job-delivery", JobArtifactType.NARRATED_VIDEO, "narrated-video.mp4", "video/mp4")
                ),
                evidence("job-delivery", "READY", true, true),
                scriptPackage("job-delivery", "READY"),
                renderReview("job-delivery", "READY", true, true),
                playbackReview("job-delivery", "READY", true, true),
                resolution("job-delivery", "READY", 0, true, true),
                handoff("job-delivery", "READY")
        );

        NarrationDeliveryPackageVo delivery = service.getPackage("job-delivery");

        assertThat(delivery.jobId()).isEqualTo("job-delivery");
        assertThat(delivery.status()).isEqualTo("READY");
        assertThat(delivery.phase()).isEqualTo("NARRATION_DELIVERY_READY");
        assertThat(delivery.audioReady()).isTrue();
        assertThat(delivery.videoReady()).isTrue();
        assertThat(delivery.artifacts()).extracting("artifactType")
                .containsExactly("NARRATION_AUDIO", "NARRATED_VIDEO");
        assertThat(delivery.artifacts()).extracting("downloadHref")
                .contains(
                        "/api/jobs/job-delivery/artifacts/audio-artifact/download",
                        "/api/jobs/job-delivery/artifacts/video-artifact/download"
                );
        assertThat(delivery.checks()).extracting("key")
                .contains(
                        "NARRATION_EVIDENCE",
                        "NARRATION_SCRIPT_PACKAGE",
                        "NARRATION_RENDER_REVIEW",
                        "NARRATION_PLAYBACK_REVIEW",
                        "NARRATION_PLAYBACK_RESOLUTION",
                        "NARRATION_RECOVERY_HANDOFF"
                );
        assertThat(delivery.safeLinks()).extracting("href")
                .contains(
                        "/api/jobs/job-delivery/narration-delivery-package",
                        "/api/jobs/job-delivery/narration-delivery-package/markdown/download",
                        "/api/jobs/job-delivery/narration-delivery-package/download",
                        "/api/jobs/job-delivery/narration-evidence/download",
                        "/api/jobs/job-delivery/narration-script-package/download",
                        "/api/jobs/job-delivery/narration-recovery-handoff/download"
                );
        assertThat(delivery.packageEntries())
                .contains(
                        "manifest.json",
                        "narration-delivery-package.json",
                        "narration-delivery-package.md",
                        "narration-evidence.json",
                        "narration-evidence.md",
                        "narration-script-package.json",
                        "narration-render-review.json",
                        "narration-render-review.md",
                        "narration-playback-review.json",
                        "narration-playback-review.md",
                        "narration-playback-resolution.json",
                        "narration-playback-resolution.md",
                        "narration-recovery-handoff.json",
                        "narration-recovery-handoff.md",
                        "README.md"
                );

        String markdown = service.renderMarkdown("job-delivery");
        StoredNarrationDeliveryPackageBo packageBo = service.openPackage("job-delivery");
        Map<String, String> entries = zipEntries(packageBo.inputStream());
        String combined = markdown + "\n" + delivery + "\n" + String.join("\n", entries.values());

        assertThat(markdown)
                .contains("# LinguaFrame Narration Delivery Package")
                .contains("- Status: READY")
                .contains("- Narration audio ready: true")
                .contains("- Narrated video ready: true");
        assertThat(packageBo.filename()).isEqualTo("linguaframe-job-job-delivery-narration-delivery-package.zip");
        assertThat(packageBo.contentType()).isEqualTo("application/zip");
        assertThat(entries.keySet()).containsAll(delivery.packageEntries());
        assertThat(entries.get("manifest.json")).contains("\"embedsMediaBytes\":false");
        assertThat(entries.get("README.md")).contains("metadata-only");
        assertThat(combined)
                .doesNotContain("Explain the first scene")
                .doesNotContain("Do not leak reviewer note body")
                .doesNotContain("raw transcript text")
                .doesNotContain("raw subtitle text")
                .doesNotContain("corrected draft text")
                .doesNotContain("objectKey")
                .doesNotContain("job-artifacts/")
                .doesNotContain("/Users/example")
                .doesNotContain("provider payload")
                .doesNotContain("sk-test")
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("private-demo-token");
    }

    @Test
    void blocksDeliveryWhenPlaybackResolutionHasUnresolvedRows() {
        NarrationDeliveryPackageVo delivery = service(
                List.of(artifact("audio-artifact", "job-blocked", JobArtifactType.NARRATION_AUDIO, "narration-audio.mp3", "audio/mpeg")),
                evidence("job-blocked", "ATTENTION", true, false),
                scriptPackage("job-blocked", "READY"),
                renderReview("job-blocked", "ATTENTION", true, false),
                playbackReview("job-blocked", "ATTENTION", true, false),
                resolution("job-blocked", "ATTENTION", 2, true, false),
                handoff("job-blocked", "BLOCKED")
        ).getPackage("job-blocked");

        assertThat(delivery.status()).isEqualTo("BLOCKED");
        assertThat(delivery.phase()).isEqualTo("NARRATION_DELIVERY_BLOCKED");
        assertThat(delivery.audioReady()).isTrue();
        assertThat(delivery.videoReady()).isFalse();
        assertThat(delivery.unresolvedPlaybackCount()).isEqualTo(2);
        assertThat(delivery.recommendedNextAction()).contains("Resolve narration playback");
        assertThat(delivery.checks())
                .filteredOn(check -> "NARRATION_PLAYBACK_RESOLUTION".equals(check.key()))
                .singleElement()
                .extracting("status")
                .isEqualTo("BLOCKED");
    }

    private static NarrationDeliveryPackageService service(
            List<JobArtifactVo> artifacts,
            NarrationEvidenceVo evidence,
            NarrationScriptPackageVo scriptPackage,
            NarrationRenderReviewVo renderReview,
            NarrationPlaybackReviewVo playbackReview,
            NarrationPlaybackReviewResolutionVo resolution,
            NarrationRecoveryHandoffVo handoff
    ) {
        StaticNarrationEvidenceService evidenceService = new StaticNarrationEvidenceService(evidence);
        StaticNarrationRecoveryHandoffService recoveryHandoffService = new StaticNarrationRecoveryHandoffService(handoff);
        return new NarrationDeliveryPackageServiceImpl(
                new StaticJobArtifactService(artifacts),
                evidenceService,
                new StaticNarrationScriptPackageService(scriptPackage),
                new StaticNarrationRenderReviewService(renderReview),
                new StaticNarrationPlaybackReviewService(playbackReview),
                new StaticNarrationPlaybackReviewResolutionService(resolution),
                recoveryHandoffService
        );
    }

    private static JobArtifactVo artifact(String artifactId, String jobId, JobArtifactType type, String filename, String contentType) {
        return new JobArtifactVo(artifactId, jobId, type, filename, contentType, 1024, "sha256", false, null, NOW);
    }

    private static NarrationEvidenceVo evidence(String jobId, String status, boolean audioReady, boolean videoReady) {
        return new NarrationEvidenceVo(
                jobId,
                status,
                2,
                128,
                new BigDecimal("28.000"),
                0,
                new BigDecimal("0.000"),
                false,
                1,
                "alloy=2",
                "alloy",
                audioReady,
                audioReady ? 1 : 0,
                audioReady ? "TIMED_AUDIO_BED" : "MISSING",
                audioReady,
                videoReady,
                videoReady ? 1 : 0,
                videoReady ? "DUCKED_ORIGINAL_AUDIO" : "MISSING",
                videoReady ? new BigDecimal("0.350") : null,
                videoReady ? new BigDecimal("1.000") : null,
                videoReady ? 250 : 0,
                videoReady ? "SAVED" : null,
                0,
                "none",
                0,
                "none",
                List.of(new NarrationEvidenceCheckVo("SAFE", "Safety", "READY", "Metadata-only evidence.")),
                List.of(new NarrationEvidenceLinkVo("NARRATION_EVIDENCE", "Narration evidence", "/api/jobs/" + jobId + "/narration-evidence", "application/json")),
                List.of("manifest.json", "narration-evidence.md", "narration-summary.json", "README.md"),
                List.of("Narration evidence is metadata-only.")
        );
    }

    private static NarrationScriptPackageVo scriptPackage(String jobId, String status) {
        return new NarrationScriptPackageVo(
                jobId,
                "zh-CN",
                new BigDecimal("120.000"),
                status,
                2,
                128,
                new BigDecimal("28.000"),
                0,
                new BigDecimal("0.000"),
                false,
                "alloy=2",
                "alloy",
                new NarrationMixSettingsVo(new BigDecimal("0.350"), new BigDecimal("1.000"), 250, NOW),
                new NarrationVoiceCatalogVo("openai", "alloy", List.of(), List.of()),
                List.of(),
                List.of(),
                List.of(),
                List.of("manifest.json", "narration-script-package.json", "narration-script-package.md", "README.md"),
                List.of("Script package may include narration text only inside explicit package exports.")
        );
    }

    private static NarrationRenderReviewVo renderReview(String jobId, String status, boolean audioReady, boolean videoReady) {
        return new NarrationRenderReviewVo(
                jobId,
                status,
                status.equals("READY") ? "Continue with playback review." : "Generate narrated video before final delivery.",
                2,
                new BigDecimal("28.000"),
                new BigDecimal("55.000"),
                0,
                new BigDecimal("0.000"),
                false,
                "alloy=2",
                0,
                "none",
                0,
                "none",
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
                List.of("Render review is metadata-only.")
        );
    }

    private static NarrationPlaybackReviewVo playbackReview(String jobId, String status, boolean audioReady, boolean videoReady) {
        return new NarrationPlaybackReviewVo(
                jobId,
                NOW,
                status,
                status.equals("READY") ? "Playback review is ready." : "Review narration playback rows.",
                2,
                status.equals("READY") ? 2 : 1,
                status.equals("READY") ? 2 : 1,
                status.equals("READY") ? 0 : 1,
                0,
                status.equals("READY") ? 0 : 1,
                audioReady,
                audioReady ? 1 : 0,
                videoReady,
                videoReady ? 1 : 0,
                List.of(new NarrationPlaybackReviewCategoryCountVo("ACCEPTED", status.equals("READY") ? 2 : 1)),
                List.of(),
                List.of(),
                List.of(),
                List.of("Playback review summary excludes reviewer note bodies.")
        );
    }

    private static NarrationPlaybackReviewResolutionVo resolution(String jobId, String status, int unresolved, boolean audioReady, boolean videoReady) {
        return new NarrationPlaybackReviewResolutionVo(
                jobId,
                NOW,
                status,
                unresolved == 0 ? "Playback review is resolved." : "Resolve playback review issues, save narration edits, and regenerate narration media before handoff.",
                2,
                2 - unresolved,
                unresolved,
                unresolved > 0 ? 1 : 0,
                unresolved > 1 ? 1 : 0,
                0,
                audioReady,
                audioReady ? 1 : 0,
                videoReady,
                videoReady ? 1 : 0,
                List.of(),
                List.of(),
                List.of("Resolution is metadata-only.")
        );
    }

    private static NarrationRecoveryHandoffVo handoff(String jobId, String status) {
        return new NarrationRecoveryHandoffVo(
                jobId,
                "video-delivery",
                NOW,
                status,
                status.equals("READY") ? "NARRATION_RECOVERY_READY" : "NARRATION_RECOVERY_BLOCKED",
                status.equals("READY") ? "Narration recovery is clear." : "Narration recovery is blocked.",
                status.equals("READY") ? "Continue with final handoff." : "Open playback resolution and resolve narration rows.",
                status.equals("READY") ? "READY" : "BLOCKED",
                status.equals("READY") ? "READY" : "ATTENTION",
                status.equals("READY") ? 0 : 2,
                status.equals("READY") ? 0 : 1,
                status.equals("READY") ? 0 : 1,
                0,
                true,
                status.equals("READY"),
                List.of(),
                List.of(),
                List.of(),
                List.of("narration-recovery-handoff.json", "narration-recovery-handoff.md", "acceptance-gate.json", "playback-resolution.json", "README.md", "manifest.json"),
                List.of("Recovery handoff excludes reviewer note bodies.")
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

    private record StaticJobArtifactService(List<JobArtifactVo> artifacts) implements JobArtifactService {
        @Override
        public JobArtifactVo createArtifact(com.linguaframe.job.domain.bo.CreateJobArtifactCommand command) {
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
        public com.linguaframe.job.domain.bo.StoredObjectResourceBo openArtifact(String jobId, String artifactId) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticNarrationEvidenceService(NarrationEvidenceVo evidence) implements NarrationEvidenceService {
        @Override
        public NarrationEvidenceVo getEvidence(String jobId) {
            return evidence;
        }

        @Override
        public String renderMarkdown(String jobId) {
            return "# Narration Evidence\n\n- Status: " + evidence.status() + "\n";
        }

        @Override
        public StoredNarrationEvidencePackageBo openPackage(String jobId) {
            return new StoredNarrationEvidencePackageBo("unused.zip", "application/zip", 0, new ByteArrayInputStream(new byte[0]));
        }
    }

    private record StaticNarrationScriptPackageService(NarrationScriptPackageVo scriptPackage) implements NarrationScriptPackageService {
        @Override
        public NarrationScriptPackageVo getPackage(String jobId) {
            return scriptPackage;
        }

        @Override
        public String renderMarkdown(String jobId) {
            return "# Narration Script Package\n\n- Status: " + scriptPackage.status() + "\n";
        }

        @Override
        public StoredNarrationScriptPackageBo openPackage(String jobId) {
            return new StoredNarrationScriptPackageBo("unused.zip", "application/zip", 0, new ByteArrayInputStream(new byte[0]));
        }

        @Override
        public com.linguaframe.job.domain.vo.NarrationScriptPackageImportVo importPackage(String jobId, com.linguaframe.job.domain.dto.ImportNarrationScriptPackageDto request) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticNarrationRenderReviewService(NarrationRenderReviewVo review) implements NarrationRenderReviewService {
        @Override
        public NarrationRenderReviewVo getReview(String jobId) {
            return review;
        }

        @Override
        public String renderMarkdown(String jobId) {
            return "# Narration Render Review\n\n- Status: " + review.status() + "\n";
        }
    }

    private record StaticNarrationPlaybackReviewService(NarrationPlaybackReviewVo review) implements NarrationPlaybackReviewService {
        @Override
        public NarrationPlaybackReviewVo getReview(String jobId) {
            return review;
        }

        @Override
        public NarrationPlaybackReviewVo updateSegmentReview(String jobId, int segmentIndex, com.linguaframe.job.domain.dto.UpdateNarrationPlaybackReviewSegmentDto request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String renderMarkdown(String jobId) {
            return "# Narration Playback Review\n\n- Status: " + review.status() + "\n";
        }
    }

    private record StaticNarrationPlaybackReviewResolutionService(NarrationPlaybackReviewResolutionVo resolution) implements NarrationPlaybackReviewResolutionService {
        @Override
        public NarrationPlaybackReviewResolutionVo getResolution(String jobId) {
            return resolution;
        }

        @Override
        public String renderMarkdown(String jobId) {
            return "# Narration Playback Resolution\n\n- Status: " + resolution.status() + "\n";
        }
    }

    private record StaticNarrationRecoveryHandoffService(NarrationRecoveryHandoffVo handoff) implements NarrationRecoveryHandoffService {
        @Override
        public NarrationRecoveryHandoffVo getHandoff(String jobId) {
            return handoff;
        }

        @Override
        public String renderMarkdown(String jobId) {
            return "# LinguaFrame Narration Recovery Handoff\n\n- Status: " + handoff.status() + "\n";
        }

        @Override
        public StoredNarrationRecoveryHandoffPackageBo openPackage(String jobId) {
            return new StoredNarrationRecoveryHandoffPackageBo("unused.zip", "application/zip", 0, new ByteArrayInputStream(new byte[0]));
        }
    }
}
