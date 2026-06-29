package com.linguaframe.job.service;

import com.linguaframe.job.domain.enums.FailureTriageCategory;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.SubtitleReviewSegmentStatus;
import com.linguaframe.job.domain.entity.NarrationMixSettingsRecord;
import com.linguaframe.job.domain.vo.FailureTriageVo;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsArtifactVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.JobPipelineProgressVo;
import com.linguaframe.job.domain.vo.JobStageProgressVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.SubtitleDraftSegmentVo;
import com.linguaframe.job.domain.vo.SubtitleDraftSummaryVo;
import com.linguaframe.job.domain.vo.SubtitleReviewSegmentVo;
import com.linguaframe.job.domain.vo.SubtitleReviewSummaryVo;
import com.linguaframe.job.repository.NarrationMixSettingsRepository;
import com.linguaframe.job.service.impl.JobEvidenceReportServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobEvidenceReportServiceTests {

    private final LocalizationJobQueryService queryService = mock(LocalizationJobQueryService.class);
    private final SubtitleReviewService subtitleReviewService = mock(SubtitleReviewService.class);
    private final SubtitleDraftService subtitleDraftService = mock(SubtitleDraftService.class);
    private final JobEvidenceReportServiceImpl service = new JobEvidenceReportServiceImpl(
            queryService,
            subtitleReviewService,
            subtitleDraftService,
            new StaticNarrationMixSettingsRepository(new NarrationMixSettingsRecord(
                    "job-evidence-triage",
                    new BigDecimal("0.125"),
                    new BigDecimal("1.750"),
                    400,
                    Instant.parse("2026-06-29T11:00:00Z")
            ))
    );

    @Test
    void markdownReportIncludesFailureTriageWhenPresent() {
        when(queryService.getDiagnosticsReport("job-evidence-triage")).thenReturn(report(new FailureTriageVo(
                FailureTriageCategory.OPENAI_AUTH_OR_MODEL,
                "OpenAI rejected the configured credentials or model.",
                "Run the OpenAI preflight, then fix OPENAI_API_KEY, OPENAI_BASE_URL, or the enabled OpenAI model values before retrying.",
                false,
                "scripts/demo/openai-demo-preflight.sh",
                List.of("failureStage=TARGET_SUBTITLE_EXPORT")
        )));
        when(subtitleReviewService.buildReview("job-evidence-triage", "zh-CN"))
                .thenReturn(subtitleReview());
        when(subtitleDraftService.getDraft("job-evidence-triage", "zh-CN"))
                .thenReturn(subtitleDraft());

        String markdown = service.buildMarkdownReport("job-evidence-triage");

        assertThat(markdown).contains("- Failure triage: OPENAI_AUTH_OR_MODEL, retryable=false");
        assertThat(markdown).contains("Action: Run the OpenAI preflight");
        assertThat(markdown).contains("- Failure runbook: scripts/demo/openai-demo-preflight.sh");
        assertThat(markdown).contains("- Pipeline current stage: TARGET_SUBTITLE_EXPORT");
        assertThat(markdown).contains("- Pipeline completed: 2 / 10");
        assertThat(markdown).contains("- Pipeline measured time: 1700 ms");
        assertThat(markdown).contains("- Pipeline slowest stage: TARGET_SUBTITLE_EXPORT / 1500 ms");
        assertThat(markdown).contains("- Subtitle review segments: 2");
        assertThat(markdown).contains("- Subtitle review missing targets: 1");
        assertThat(markdown).contains("- Subtitle review timing mismatches: 1");
        assertThat(markdown).contains("- Subtitle review quality: 88 / 100, NEEDS_REVIEW");
        assertThat(markdown).contains("- Subtitle review downloadable subtitle artifacts: 3");
        assertThat(markdown).contains("- Subtitle draft segments: 2");
        assertThat(markdown).contains("- Subtitle draft edited segments: 1");
        assertThat(markdown).contains("- Subtitle draft last updated: 2026-06-28T09:30:00Z");
        assertThat(markdown).contains("- Reviewed subtitle artifacts: 3");
        assertThat(markdown).contains("- Reviewed burned video: Available");
        assertThat(markdown).contains("- Narration evidence: /api/jobs/job-evidence-triage/narration-evidence");
        assertThat(markdown).contains("- Narration evidence package: /api/jobs/job-evidence-triage/narration-evidence/download");
        assertThat(markdown).contains("- Narration audio artifacts: 1");
        assertThat(markdown).contains("- Narration audio layout: TIMED_AUDIO_BED");
        assertThat(markdown).contains("- Narration time aligned: true");
        assertThat(markdown).contains("- Narrated video artifacts: 1");
        assertThat(markdown).contains("- Narrated video mix mode: DUCKED_ORIGINAL_AUDIO");
        assertThat(markdown).contains("- Narrated video ducking volume: 0.125");
        assertThat(markdown).contains("- Narrated video narration volume: 1.750");
        assertThat(markdown).contains("- Narrated video fade duration ms: 400");
        assertThat(markdown).contains("- Narrated video mix settings source: SAVED");
        assertThat(markdown).doesNotContain("raw source text");
        assertThat(markdown).doesNotContain("raw target text");
        assertThat(markdown).doesNotContain("raw draft text");
        assertThat(markdown).doesNotContain("sk-");
        assertThat(markdown).doesNotContain("/Users/");
        assertThat(markdown).doesNotContain("provider request payload");
    }

    @Test
    void markdownReportOmitsFailureTriageWhenAbsent() {
        when(queryService.getDiagnosticsReport("job-evidence-complete")).thenReturn(report(null));
        when(subtitleReviewService.buildReview("job-evidence-triage", "zh-CN"))
                .thenReturn(subtitleReview());
        when(subtitleDraftService.getDraft("job-evidence-triage", "zh-CN"))
                .thenReturn(subtitleDraft());

        String markdown = service.buildMarkdownReport("job-evidence-complete");

        assertThat(markdown).doesNotContain("Failure triage");
        assertThat(markdown).doesNotContain("Failure runbook");
    }

    private SubtitleReviewSummaryVo subtitleReview() {
        return new SubtitleReviewSummaryVo(
                "job-evidence-triage",
                "zh-CN",
                2,
                1,
                1,
                1000,
                1000,
                88,
                "NEEDS_REVIEW",
                1,
                1,
                3,
                List.of(new SubtitleReviewSegmentVo(
                        0,
                        0,
                        1000,
                        "raw source text",
                        "raw target text",
                        1000,
                        0,
                        SubtitleReviewSegmentStatus.ALIGNED
                ))
        );
    }

    private SubtitleDraftSummaryVo subtitleDraft() {
        return new SubtitleDraftSummaryVo(
                "job-evidence-triage",
                "zh-CN",
                2,
                1,
                Instant.parse("2026-06-28T09:30:00Z"),
                List.of(new SubtitleDraftSegmentVo(
                        0,
                        0,
                        1000,
                        "raw source text",
                        "raw target text",
                        "raw draft text",
                        true,
                        Instant.parse("2026-06-28T09:30:00Z")
                ))
        );
    }

    private JobDiagnosticsReportVo report(FailureTriageVo triage) {
        LocalizationJobVo job = new LocalizationJobVo(
                "job-evidence-triage",
                "video-evidence-triage",
                "zh-CN",
                null,
                triage == null ? LocalizationJobStatus.COMPLETED : LocalizationJobStatus.FAILED,
                Instant.parse("2026-06-28T08:00:00Z"),
                null,
                null,
                triage == null ? null : Instant.parse("2026-06-28T08:05:00Z"),
                triage == null ? null : LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                triage == null ? null : "OpenAI request failed with status 401",
                0,
                null,
                0,
                null,
                List.of(),
                new JobUsageSummaryVo(0, 0, 0, BigDecimal.ZERO, null, null, null, null),
                new JobCacheSummaryVo(0, 0, 0),
                List.of(),
                null,
                triage,
                pipelineProgress()
        );
        List<JobDiagnosticsArtifactVo> artifacts = List.of(
                artifact(JobArtifactType.REVIEWED_SUBTITLE_JSON, "reviewed-subtitles.zh-CN.json"),
                artifact(JobArtifactType.REVIEWED_SUBTITLE_SRT, "reviewed-subtitles.zh-CN.srt"),
                artifact(JobArtifactType.REVIEWED_SUBTITLE_VTT, "reviewed-subtitles.zh-CN.vtt"),
                artifact(JobArtifactType.REVIEWED_BURNED_VIDEO, "reviewed-burned-video.mp4"),
                artifact(JobArtifactType.NARRATION_AUDIO, "narration-audio.mp3"),
                artifact(JobArtifactType.NARRATED_VIDEO, "narrated-video.mp4")
        );
        return new JobDiagnosticsReportVo(Instant.parse("2026-06-28T08:06:00Z"), job, artifacts, artifacts.size());
    }

    private JobDiagnosticsArtifactVo artifact(JobArtifactType type, String filename) {
        return new JobDiagnosticsArtifactVo(
                "artifact-" + type.name(),
                type,
                filename,
                type == JobArtifactType.REVIEWED_BURNED_VIDEO ? "video/mp4" : "text/plain",
                123,
                "1234567890abcdef",
                false,
                null,
                Instant.parse("2026-06-28T09:45:00Z")
        );
    }

    private JobPipelineProgressVo pipelineProgress() {
        return new JobPipelineProgressVo(
                10,
                2,
                1,
                0,
                0,
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                true,
                1700,
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                1500L,
                List.of(
                        new JobStageProgressVo(
                                LocalizationJobStage.WORKER_RECEIVED,
                                JobTimelineEventStatus.SUCCEEDED,
                                Instant.parse("2026-06-28T08:00:00Z"),
                                Instant.parse("2026-06-28T08:00:01Z"),
                                200L,
                                "Worker received localization job."
                        ),
                        new JobStageProgressVo(
                                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                                JobTimelineEventStatus.FAILED,
                                Instant.parse("2026-06-28T08:00:02Z"),
                                Instant.parse("2026-06-28T08:00:04Z"),
                                1500L,
                                "TARGET_SUBTITLE_EXPORT failed"
                        )
                )
        );
    }

    private record StaticNarrationMixSettingsRepository(NarrationMixSettingsRecord settings)
            implements NarrationMixSettingsRepository {

        @Override
        public Optional<NarrationMixSettingsRecord> findByJobId(String jobId) {
            return Optional.ofNullable(settings)
                    .filter(record -> record.jobId().equals(jobId));
        }

        @Override
        public NarrationMixSettingsRecord upsert(NarrationMixSettingsRecord settings) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteByJobId(String jobId) {
        }
    }
}
