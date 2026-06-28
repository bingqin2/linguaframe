package com.linguaframe.job.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.SubtitleReviewSegmentStatus;
import com.linguaframe.job.domain.vo.DeliveryManifestArtifactVo;
import com.linguaframe.job.domain.vo.DeliveryManifestLinkVo;
import com.linguaframe.job.domain.vo.DeliveryManifestVo;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.ReviewedSubtitleWorkflowVo;
import com.linguaframe.job.domain.vo.SubtitleDraftSegmentVo;
import com.linguaframe.job.domain.vo.SubtitleDraftSummaryVo;
import com.linguaframe.job.domain.vo.SubtitleReviewSegmentVo;
import com.linguaframe.job.domain.vo.SubtitleReviewSummaryVo;
import com.linguaframe.job.service.impl.ReviewedSubtitleWorkflowServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewedSubtitleWorkflowServiceTests {

    private static final Instant NOW = Instant.parse("2026-06-29T14:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void blocksWorkflowForIncompleteJob() {
        ReviewedSubtitleWorkflowService service = service(
                job("job-running", LocalizationJobStatus.PROCESSING),
                review(0, 0, 3),
                draft(0),
                List.of(),
                manifest(false, 0, false)
        );

        ReviewedSubtitleWorkflowVo workflow = service.workflow("job-running");

        assertThat(workflow.overallStatus()).isEqualTo("BLOCKED");
        assertThat(workflow.phase()).isEqualTo("WAITING_FOR_JOB");
        assertThat(workflow.recommendedNextAction()).contains("Wait for the job to complete");
        assertThat(workflow.checks())
                .anySatisfy(check -> {
                    assertThat(check.key()).isEqualTo("JOB_COMPLETED");
                    assertThat(check.status()).isEqualTo("BLOCKED");
                    assertThat(check.blocking()).isTrue();
                });
        assertThat(workflow.links()).extracting("kind").contains("JOB_DETAIL");
    }

    @Test
    void marksReviewNeededWhenGeneratedSubtitlesHaveAlignmentIssues() {
        ReviewedSubtitleWorkflowService service = service(
                job("job-review", LocalizationJobStatus.COMPLETED),
                review(1, 1, 3),
                draft(0),
                generatedArtifacts(),
                manifest(false, 0, false)
        );

        ReviewedSubtitleWorkflowVo workflow = service.workflow("job-review");

        assertThat(workflow.overallStatus()).isEqualTo("ATTENTION");
        assertThat(workflow.phase()).isEqualTo("REVIEW_NEEDED");
        assertThat(workflow.segmentCount()).isEqualTo(3);
        assertThat(workflow.missingTargetCount()).isEqualTo(1);
        assertThat(workflow.timingMismatchCount()).isEqualTo(1);
        assertThat(workflow.editedSegmentCount()).isZero();
        assertThat(workflow.recommendedNextAction()).contains("Fix missing or mismatched subtitles");
        assertThat(workflow.links()).extracting("kind")
                .contains("SUBTITLE_REVIEW", "DRAFT_EXPORT_SRT", "PUBLISH_REVIEWED_SUBTITLES");
    }

    @Test
    void marksPublishReadyWhenDraftEditsExistButReviewedArtifactsAreMissing() {
        ReviewedSubtitleWorkflowService service = service(
                job("job-draft", LocalizationJobStatus.COMPLETED),
                review(0, 0, 3),
                draft(2),
                generatedArtifacts(),
                manifest(false, 0, false)
        );

        ReviewedSubtitleWorkflowVo workflow = service.workflow("job-draft");

        assertThat(workflow.overallStatus()).isEqualTo("ATTENTION");
        assertThat(workflow.phase()).isEqualTo("PUBLISH_READY");
        assertThat(workflow.editedSegmentCount()).isEqualTo(2);
        assertThat(workflow.reviewedSubtitleArtifactCount()).isZero();
        assertThat(workflow.recommendedNextAction()).contains("Publish reviewed subtitles");
        assertThat(workflow.checks())
                .anySatisfy(check -> {
                    assertThat(check.key()).isEqualTo("REVIEWED_SUBTITLE_ARTIFACTS");
                    assertThat(check.status()).isEqualTo("ATTENTION");
                    assertThat(check.nextAction()).contains("Publish reviewed subtitles");
                });
    }

    @Test
    void marksHandoffReadyWhenReviewedArtifactsAndManifestAreReady() {
        ReviewedSubtitleWorkflowService service = service(
                job("job-handoff", LocalizationJobStatus.COMPLETED),
                review(0, 0, 3),
                draft(1),
                reviewedArtifacts(true),
                manifest(true, 3, true)
        );

        ReviewedSubtitleWorkflowVo workflow = service.workflow("job-handoff");

        assertThat(workflow.overallStatus()).isEqualTo("READY");
        assertThat(workflow.phase()).isEqualTo("HANDOFF_READY");
        assertThat(workflow.reviewedSubtitleArtifactCount()).isEqualTo(3);
        assertThat(workflow.reviewedBurnedVideoAvailable()).isTrue();
        assertThat(workflow.handoffReady()).isTrue();
        assertThat(workflow.recommendedNextAction()).contains("Download the handoff package");
        assertThat(workflow.links()).extracting("kind")
                .contains("DELIVERY_MANIFEST", "HANDOFF_PACKAGE", "REVIEWED_BURNED_VIDEO");
    }

    @Test
    void workflowJsonIsMetadataOnly() throws JsonProcessingException {
        ReviewedSubtitleWorkflowService service = service(
                job("job-safe", LocalizationJobStatus.COMPLETED),
                review(0, 0, 3),
                draft(1),
                reviewedArtifacts(true),
                manifest(true, 3, true)
        );

        String json = JSON.writeValueAsString(service.workflow("job-safe"));

        assertThat(json)
                .contains("job-safe")
                .doesNotContain("raw transcript text")
                .doesNotContain("raw subtitle text")
                .doesNotContain("corrected subtitle text")
                .doesNotContain("/Users/example")
                .doesNotContain("sk-test")
                .doesNotContain("private-demo-token")
                .doesNotContain("provider payload")
                .doesNotContain("object-key");
    }

    private static ReviewedSubtitleWorkflowService service(
            LocalizationJobVo job,
            SubtitleReviewSummaryVo review,
            SubtitleDraftSummaryVo draft,
            List<JobArtifactVo> artifacts,
            DeliveryManifestVo manifest
    ) {
        return new ReviewedSubtitleWorkflowServiceImpl(
                new StaticQueryService(job),
                new StaticSubtitleReviewService(review),
                new StaticSubtitleDraftService(draft),
                new StaticArtifactService(artifacts),
                new StaticDeliveryManifestService(manifest),
                CLOCK
        );
    }

    private static LocalizationJobVo job(String jobId, LocalizationJobStatus status) {
        return new LocalizationJobVo(
                jobId,
                "video-" + jobId,
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
                status == LocalizationJobStatus.FAILED ? NOW.minusSeconds(10) : null,
                null,
                status == LocalizationJobStatus.FAILED ? "Provider unavailable" : null,
                0,
                JobDispatchEventStatus.DISPATCHED,
                0,
                NOW.minusSeconds(110),
                List.of(),
                new JobUsageSummaryVo(3, 0, 4200, new BigDecimal("0.00007800"), 100, 80, new BigDecimal("45.0"), 1200),
                new JobCacheSummaryVo(1, 6, 2),
                List.of(),
                null,
                null,
                null
        );
    }

    private static SubtitleReviewSummaryVo review(int missingTargetCount, int timingMismatchCount, int segmentCount) {
        return new SubtitleReviewSummaryVo(
                "job",
                "zh-CN",
                segmentCount,
                missingTargetCount,
                timingMismatchCount,
                1600,
                2200,
                91,
                "GOOD",
                1,
                1,
                3,
                List.of(new SubtitleReviewSegmentVo(
                        0,
                        0,
                        1200,
                        "raw transcript text",
                        "raw subtitle text",
                        1200,
                        0,
                        SubtitleReviewSegmentStatus.ALIGNED
                ))
        );
    }

    private static SubtitleDraftSummaryVo draft(int editedSegmentCount) {
        List<SubtitleDraftSegmentVo> segments = editedSegmentCount == 0
                ? List.of()
                : List.of(new SubtitleDraftSegmentVo(
                0,
                0,
                1200,
                "raw transcript text",
                "raw subtitle text",
                "corrected subtitle text",
                true,
                NOW.minusSeconds(30)
        ));
        return new SubtitleDraftSummaryVo("job", "zh-CN", 3, editedSegmentCount, editedSegmentCount == 0 ? null : NOW.minusSeconds(30), segments);
    }

    private static List<JobArtifactVo> generatedArtifacts() {
        return List.of(
                artifact("target-json", JobArtifactType.TARGET_SUBTITLE_JSON),
                artifact("target-srt", JobArtifactType.TARGET_SUBTITLE_SRT),
                artifact("target-vtt", JobArtifactType.TARGET_SUBTITLE_VTT)
        );
    }

    private static List<JobArtifactVo> reviewedArtifacts(boolean includeBurnedVideo) {
        List<JobArtifactVo> artifacts = new java.util.ArrayList<>(List.of(
                artifact("reviewed-json", JobArtifactType.REVIEWED_SUBTITLE_JSON),
                artifact("reviewed-srt", JobArtifactType.REVIEWED_SUBTITLE_SRT),
                artifact("reviewed-vtt", JobArtifactType.REVIEWED_SUBTITLE_VTT)
        ));
        if (includeBurnedVideo) {
            artifacts.add(artifact("reviewed-video", JobArtifactType.REVIEWED_BURNED_VIDEO));
        }
        return artifacts;
    }

    private static JobArtifactVo artifact(String artifactId, JobArtifactType type) {
        return new JobArtifactVo(
                artifactId,
                "job",
                type,
                artifactId + ".dat",
                "application/octet-stream",
                123,
                "abc123abc123abc123",
                false,
                null,
                NOW
        );
    }

    private static DeliveryManifestVo manifest(boolean handoffReady, int reviewedSubtitleArtifactCount, boolean reviewedBurnedVideoAvailable) {
        return new DeliveryManifestVo(
                "job",
                "video",
                "zh-CN",
                "HIGH_CONTRAST",
                3,
                "abc123",
                "BALANCED",
                "tears-showcase",
                LocalizationJobStatus.COMPLETED,
                NOW,
                handoffReady,
                reviewedSubtitleArtifactCount,
                reviewedBurnedVideoAvailable,
                6,
                reviewedSubtitleArtifactCount == 0 ? List.of() : List.of(new DeliveryManifestArtifactVo(
                        "reviewed-srt",
                        JobArtifactType.REVIEWED_SUBTITLE_SRT,
                        "reviewed-subtitles.zh-CN.srt",
                        "application/x-subrip;charset=UTF-8",
                        123,
                        "abc123abc123",
                        "Generated",
                        "REVIEWED_HANDOFF",
                        "/api/jobs/job/artifacts/reviewed-srt/download"
                )),
                List.of(),
                List.of(new DeliveryManifestLinkVo("Backend evidence", "EVIDENCE_MARKDOWN", "/api/jobs/job/evidence/markdown/download"))
        );
    }

    private record StaticQueryService(LocalizationJobVo job) implements LocalizationJobQueryService {
        @Override
        public LocalizationJobListVo listJobs(LocalizationJobStatus status, Integer limit, Integer offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LocalizationJobVo getJob(String jobId) {
            return job;
        }

        @Override
        public JobDiagnosticsReportVo getDiagnosticsReport(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticSubtitleReviewService(SubtitleReviewSummaryVo review) implements SubtitleReviewService {
        @Override
        public SubtitleReviewSummaryVo buildReview(String jobId, String language) {
            return review;
        }
    }

    private record StaticSubtitleDraftService(SubtitleDraftSummaryVo draft) implements SubtitleDraftService {
        @Override
        public SubtitleDraftSummaryVo getDraft(String jobId, String language) {
            return draft;
        }

        @Override
        public SubtitleDraftSummaryVo updateDraft(String jobId, String language, com.linguaframe.job.domain.dto.UpdateSubtitleDraftRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SubtitleDraftSummaryVo clearDraft(String jobId, String language) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] exportDraft(String jobId, String language, com.linguaframe.job.domain.enums.SubtitleDraftExportFormat format) {
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
}
