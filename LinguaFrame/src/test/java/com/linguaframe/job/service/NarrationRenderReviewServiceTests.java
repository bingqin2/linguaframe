package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.StoredArtifactArchiveBo;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.dto.SaveNarrationSegmentsRequest;
import com.linguaframe.job.domain.dto.UpdateNarrationMixSettingsDto;
import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.NarrationEvidenceCheckVo;
import com.linguaframe.job.domain.vo.NarrationEvidenceLinkVo;
import com.linguaframe.job.domain.vo.NarrationEvidenceVo;
import com.linguaframe.job.domain.vo.NarrationMixAutomationVo;
import com.linguaframe.job.domain.vo.NarrationMixSettingsVo;
import com.linguaframe.job.domain.vo.NarrationRenderReviewVo;
import com.linguaframe.job.domain.vo.NarrationTimelineSummaryVo;
import com.linguaframe.job.domain.vo.NarrationVoiceCatalogVo;
import com.linguaframe.job.domain.vo.NarrationWorkspaceVo;
import com.linguaframe.job.service.impl.NarrationRenderReviewServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NarrationRenderReviewServiceTests {

    @Test
    void blocksWhenWorkspaceHasNoSegments() {
        NarrationRenderReviewVo review = service(
                workspace("job-review", 0, false, 0, 0),
                evidence("job-review", "BLOCKED", false, false),
                List.of()
        ).getReview("job-review");

        assertThat(review.status()).isEqualTo("BLOCKED");
        assertThat(review.nextAction()).isEqualTo("Add narration segments before rendering.");
        assertThat(review.segmentCount()).isZero();
        assertThat(review.checks())
                .extracting(check -> check.key() + ":" + check.status())
                .contains("SEGMENTS:BLOCK", "NARRATION_AUDIO:BLOCK");
        assertThat(review.safetyNotes()).anyMatch(note -> note.contains("metadata-only"));
    }

    @Test
    void returnsAttentionForAudioOnlyRenderAndRecommendsNarratedVideo() {
        NarrationRenderReviewVo review = service(
                workspace("job-review", 2, false, 1, 3),
                evidence("job-review", "ATTENTION", true, false),
                List.of(
                        artifact("audio-1", JobArtifactType.NARRATION_AUDIO),
                        artifact("waveform-1", JobArtifactType.NARRATION_WAVEFORM)
                )
        ).getReview("job-review");

        assertThat(review.status()).isEqualTo("ATTENTION");
        assertThat(review.nextAction()).isEqualTo("Generate narrated video or keep audio-only review.");
        assertThat(review.audioReady()).isTrue();
        assertThat(review.videoReady()).isFalse();
        assertThat(review.waveformReady()).isTrue();
        assertThat(review.waveformArtifactId()).isEqualTo("waveform-1");
        assertThat(review.voiceSummary()).isEqualTo("alloy:2");
        assertThat(review.segmentMixOverrideCount()).isEqualTo(1);
        assertThat(review.mixKeyframeCount()).isEqualTo(3);
        assertThat(review.safeLinks())
                .extracting(link -> link.kind())
                .contains("NARRATION_EVIDENCE", "NARRATION_RENDER_REVIEW_MARKDOWN");
    }

    @Test
    void returnsReadyForCompleteRenderReview() {
        NarrationRenderReviewVo review = service(
                workspace("job-review", 3, false, 2, 3),
                evidence("job-review", "READY", true, true),
                List.of(
                        artifact("audio-1", JobArtifactType.NARRATION_AUDIO),
                        artifact("video-1", JobArtifactType.NARRATED_VIDEO),
                        artifact("waveform-1", JobArtifactType.NARRATION_WAVEFORM)
                )
        ).getReview("job-review");

        assertThat(review.status()).isEqualTo("READY");
        assertThat(review.nextAction()).isEqualTo("Review narrated video and export handoff evidence.");
        assertThat(review.videoReady()).isTrue();
        assertThat(review.metrics())
                .extracting(metric -> metric.key() + "=" + metric.value())
                .contains("segments=3", "audioArtifacts=1", "narratedVideoArtifacts=1");
    }

    @Test
    void blocksWhenTimelineHasOverlapEvenIfArtifactsExist() {
        NarrationRenderReviewVo review = service(
                workspace("job-review", 2, true, 0, 0),
                evidence("job-review", "READY", true, true),
                List.of(
                        artifact("audio-1", JobArtifactType.NARRATION_AUDIO),
                        artifact("video-1", JobArtifactType.NARRATED_VIDEO),
                        artifact("waveform-1", JobArtifactType.NARRATION_WAVEFORM)
                )
        ).getReview("job-review");

        assertThat(review.status()).isEqualTo("BLOCKED");
        assertThat(review.nextAction()).isEqualTo("Resolve overlapping narration windows before review.");
        assertThat(review.checks())
                .extracting(check -> check.key() + ":" + check.status())
                .contains("TIMELINE_OVERLAP:BLOCK");
    }

    @Test
    void rendersSafeMarkdownWithoutNarrationTextOrStoragePaths() {
        NarrationRenderReviewService service = service(
                workspace("job-review", 2, false, 1, 3),
                evidence("job-review", "ATTENTION", true, false),
                List.of(
                        artifact("audio-1", JobArtifactType.NARRATION_AUDIO),
                        artifact("waveform-1", JobArtifactType.NARRATION_WAVEFORM)
                )
        );

        String markdown = service.renderMarkdown("job-review");

        assertThat(markdown).contains("# Narration Render Review");
        assertThat(markdown).contains("- Status: ATTENTION");
        assertThat(markdown).contains("- Next action: Generate narrated video or keep audio-only review.");
        assertThat(markdown).contains("/api/jobs/job-review/narration-render-review/markdown/download");
        assertThat(markdown).doesNotContain("Narration text");
        assertThat(markdown).doesNotContain("s3://");
        assertThat(markdown).doesNotContain("/tmp/");
    }

    private static NarrationRenderReviewService service(
            NarrationWorkspaceVo workspace,
            NarrationEvidenceVo evidence,
            List<JobArtifactVo> artifacts
    ) {
        return new NarrationRenderReviewServiceImpl(
                new StaticWorkspaceService(workspace),
                new StaticEvidenceService(evidence),
                new StaticArtifactService(artifacts)
        );
    }

    private static NarrationWorkspaceVo workspace(String jobId, int segmentCount, boolean overlap, int overrideCount, int keyframeCount) {
        return new NarrationWorkspaceVo(
                jobId,
                segmentCount > 0 ? "READY" : "EMPTY",
                segmentCount,
                new BigDecimal("28.000"),
                segmentCount * 40,
                segmentCount > 0 && !overlap,
                new NarrationMixSettingsVo(new BigDecimal("0.350"), new BigDecimal("1.000"), 250, Instant.parse("2026-06-30T01:00:00Z")),
                new NarrationMixAutomationVo(keyframeCount, keyframeCount, 0, 0, List.of(), List.of()),
                new NarrationVoiceCatalogVo("demo", "alloy", List.of(), List.of()),
                new NarrationTimelineSummaryVo(
                        new BigDecimal("15.000"),
                        new BigDecimal("43.000"),
                        new BigDecimal("28.000"),
                        new BigDecimal("25.000"),
                        new BigDecimal("3.000"),
                        1,
                        overlap,
                        segmentCount > 0 && !overlap,
                        List.of()
                ),
                List.of(),
                List.of("Workspace summaries are metadata-only.")
        );
    }

    private static NarrationEvidenceVo evidence(String jobId, String status, boolean audioReady, boolean videoReady) {
        return new NarrationEvidenceVo(
                jobId,
                status,
                audioReady ? 2 : 0,
                audioReady ? 80 : 0,
                audioReady ? new BigDecimal("25.000") : BigDecimal.ZERO,
                audioReady ? 1 : 0,
                audioReady ? new BigDecimal("3.000") : BigDecimal.ZERO,
                false,
                audioReady ? 1 : 0,
                audioReady ? "alloy:2" : "none",
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
                videoReady ? "JOB_DEFAULT" : null,
                1,
                "1 segment override",
                3,
                "DUCKING_VOLUME:3",
                List.of(new NarrationEvidenceCheckVo("SAFE", "Safety", "PASS", "Metadata-only review.")),
                List.of(new NarrationEvidenceLinkVo("NARRATION_EVIDENCE", "Narration evidence", "/api/jobs/" + jobId + "/narration-evidence", "application/json")),
                List.of("manifest.json"),
                List.of("No narration text bodies are included.")
        );
    }

    private static JobArtifactVo artifact(String id, JobArtifactType type) {
        return new JobArtifactVo(
                id,
                "job-review",
                type,
                id + ".bin",
                type == JobArtifactType.NARRATION_WAVEFORM ? "application/json" : "application/octet-stream",
                32L,
                id + "-hash",
                false,
                null,
                Instant.parse("2026-06-30T01:00:00Z")
        );
    }

    private record StaticWorkspaceService(NarrationWorkspaceVo workspace) implements NarrationWorkspaceService {
        @Override
        public NarrationWorkspaceVo getWorkspace(String jobId) {
            return workspace;
        }

        @Override
        public NarrationWorkspaceVo saveWorkspace(String jobId, SaveNarrationSegmentsRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public NarrationWorkspaceVo updateMixSettings(String jobId, UpdateNarrationMixSettingsDto request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public NarrationWorkspaceVo clearWorkspace(String jobId) {
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
            return "# Narration Evidence";
        }

        @Override
        public com.linguaframe.job.domain.bo.StoredNarrationEvidencePackageBo openPackage(String jobId) {
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
            return new StoredObjectResourceBo(artifactId, "application/octet-stream", 0, InputStream.nullInputStream());
        }

        @Override
        public StoredArtifactArchiveBo openArtifactArchive(String jobId) {
            throw new UnsupportedOperationException();
        }
    }
}
