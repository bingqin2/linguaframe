package com.linguaframe.job.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.DeliveryManifestArtifactVo;
import com.linguaframe.job.domain.vo.DeliveryManifestVo;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.service.impl.DeliveryManifestServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryManifestServiceTests {

    @Test
    void reviewedSubtitleArtifactsMakeCompletedJobReadyForHandoff() {
        RecordingJobArtifactService artifactService = new RecordingJobArtifactService(List.of(
                artifact("reviewed-json", JobArtifactType.REVIEWED_SUBTITLE_JSON, "reviewed-subtitles.zh-CN.json"),
                artifact("reviewed-srt", JobArtifactType.REVIEWED_SUBTITLE_SRT, "reviewed-subtitles.zh-CN.srt"),
                artifact("reviewed-vtt", JobArtifactType.REVIEWED_SUBTITLE_VTT, "reviewed-subtitles.zh-CN.vtt"),
                artifact("reviewed-video", JobArtifactType.REVIEWED_BURNED_VIDEO, "reviewed-burned-video.mp4"),
                artifact("worker-summary", JobArtifactType.WORKER_SUMMARY, "worker-summary.json")
        ));
        DeliveryManifestService service = new DeliveryManifestServiceImpl(
                new RecordingLocalizationJobQueryService(job(LocalizationJobStatus.COMPLETED)),
                artifactService
        );

        DeliveryManifestVo manifest = service.buildManifest("job-handoff");

        assertThat(manifest.jobId()).isEqualTo("job-handoff");
        assertThat(manifest.videoId()).isEqualTo("video-handoff");
        assertThat(manifest.targetLanguage()).isEqualTo("zh-CN");
        assertThat(manifest.status()).isEqualTo(LocalizationJobStatus.COMPLETED);
        assertThat(manifest.handoffReady()).isTrue();
        assertThat(manifest.reviewedSubtitleArtifactCount()).isEqualTo(3);
        assertThat(manifest.reviewedBurnedVideoAvailable()).isTrue();
        assertThat(manifest.generatedArtifactCount()).isEqualTo(1);
        assertThat(manifest.reviewedArtifacts()).extracting(DeliveryManifestArtifactVo::type)
                .containsExactly(
                        JobArtifactType.REVIEWED_SUBTITLE_JSON,
                        JobArtifactType.REVIEWED_SUBTITLE_SRT,
                        JobArtifactType.REVIEWED_SUBTITLE_VTT,
                        JobArtifactType.REVIEWED_BURNED_VIDEO
                );
        assertThat(manifest.auditArtifacts()).extracting(DeliveryManifestArtifactVo::type)
                .containsExactly(JobArtifactType.WORKER_SUMMARY);
        assertThat(manifest.reviewedArtifacts()).extracting(DeliveryManifestArtifactVo::downloadUrl)
                .contains("/api/jobs/job-handoff/artifacts/reviewed-srt/download");
        assertThat(manifest.links()).extracting(link -> link.kind())
                .containsExactly("RESULT_BUNDLE", "DIAGNOSTICS_JSON", "EVIDENCE_MARKDOWN", "EVIDENCE_BUNDLE");
    }

    @Test
    void missingReviewedArtifactsKeepCompletedJobIncompleteButPreserveAuditArtifacts() {
        DeliveryManifestService service = new DeliveryManifestServiceImpl(
                new RecordingLocalizationJobQueryService(job(LocalizationJobStatus.COMPLETED)),
                new RecordingJobArtifactService(List.of(
                        artifact("target-srt", JobArtifactType.TARGET_SUBTITLE_SRT, "target-subtitles.srt"),
                        artifact("burned-video", JobArtifactType.BURNED_VIDEO, "burned-video.mp4")
                ))
        );

        DeliveryManifestVo manifest = service.buildManifest("job-handoff");

        assertThat(manifest.handoffReady()).isFalse();
        assertThat(manifest.reviewedSubtitleArtifactCount()).isZero();
        assertThat(manifest.reviewedBurnedVideoAvailable()).isFalse();
        assertThat(manifest.reviewedArtifacts()).isEmpty();
        assertThat(manifest.auditArtifacts()).extracting(DeliveryManifestArtifactVo::filename)
                .containsExactly("target-subtitles.srt", "burned-video.mp4");
    }

    @Test
    void serializedManifestContainsOnlySafeMetadata() throws Exception {
        DeliveryManifestService service = new DeliveryManifestServiceImpl(
                new RecordingLocalizationJobQueryService(job(LocalizationJobStatus.COMPLETED)),
                new RecordingJobArtifactService(List.of(
                        artifact("reviewed-json", JobArtifactType.REVIEWED_SUBTITLE_JSON, "reviewed-subtitles.zh-CN.json"),
                        artifact("reviewed-srt", JobArtifactType.REVIEWED_SUBTITLE_SRT, "reviewed-subtitles.zh-CN.srt"),
                        artifact("reviewed-vtt", JobArtifactType.REVIEWED_SUBTITLE_VTT, "reviewed-subtitles.zh-CN.vtt")
                ))
        );

        String json = JsonMapper.builder().findAndAddModules().build()
                .writeValueAsString(service.buildManifest("job-handoff"));

        assertThat(json).contains("reviewed-subtitles.zh-CN.srt");
        assertThat(json).doesNotContain("objectKey");
        assertThat(json).doesNotContain("source-videos/");
        assertThat(json).doesNotContain("raw transcript text");
        assertThat(json).doesNotContain("raw generated subtitle");
        assertThat(json).doesNotContain("raw corrected subtitle");
        assertThat(json).doesNotContain("OPENAI_API_KEY");
        assertThat(json).doesNotContain("provider payload");
    }

    private static LocalizationJobVo job(LocalizationJobStatus status) {
        return new LocalizationJobVo(
                "job-handoff",
                "video-handoff",
                "zh-CN",
                "alloy",
                status,
                Instant.parse("2026-06-28T00:00:00Z"),
                Instant.parse("2026-06-28T00:00:01Z"),
                Instant.parse("2026-06-28T00:00:10Z"),
                null,
                null,
                null,
                0,
                null,
                0,
                null,
                List.of(),
                null,
                new JobCacheSummaryVo(0, 0, 0),
                List.of(),
                null,
                null,
                null
        );
    }

    private static JobArtifactVo artifact(String artifactId, JobArtifactType type, String filename) {
        return new JobArtifactVo(
                artifactId,
                "job-handoff",
                type,
                filename,
                filename.endsWith(".mp4") ? "video/mp4" : "application/json",
                128,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                false,
                null,
                Instant.parse("2026-06-28T00:00:10Z")
        );
    }

    private record RecordingLocalizationJobQueryService(LocalizationJobVo job) implements LocalizationJobQueryService {

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

    private static final class RecordingJobArtifactService implements JobArtifactService {
        private final List<JobArtifactVo> artifacts;

        private RecordingJobArtifactService(List<JobArtifactVo> artifacts) {
            this.artifacts = new ArrayList<>(artifacts);
        }

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
}
