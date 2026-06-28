package com.linguaframe.job.service;

import com.linguaframe.job.domain.enums.FailureTriageCategory;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.FailureTriageVo;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.JobPipelineProgressVo;
import com.linguaframe.job.domain.vo.JobStageProgressVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.service.impl.JobEvidenceReportServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobEvidenceReportServiceTests {

    private final LocalizationJobQueryService queryService = mock(LocalizationJobQueryService.class);
    private final JobEvidenceReportServiceImpl service = new JobEvidenceReportServiceImpl(queryService);

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

        String markdown = service.buildMarkdownReport("job-evidence-triage");

        assertThat(markdown).contains("- Failure triage: OPENAI_AUTH_OR_MODEL, retryable=false");
        assertThat(markdown).contains("Action: Run the OpenAI preflight");
        assertThat(markdown).contains("- Failure runbook: scripts/demo/openai-demo-preflight.sh");
        assertThat(markdown).contains("- Pipeline current stage: TARGET_SUBTITLE_EXPORT");
        assertThat(markdown).contains("- Pipeline completed: 2 / 10");
        assertThat(markdown).contains("- Pipeline measured time: 1700 ms");
        assertThat(markdown).contains("- Pipeline slowest stage: TARGET_SUBTITLE_EXPORT / 1500 ms");
        assertThat(markdown).doesNotContain("sk-");
        assertThat(markdown).doesNotContain("/Users/");
        assertThat(markdown).doesNotContain("provider request payload");
    }

    @Test
    void markdownReportOmitsFailureTriageWhenAbsent() {
        when(queryService.getDiagnosticsReport("job-evidence-complete")).thenReturn(report(null));

        String markdown = service.buildMarkdownReport("job-evidence-complete");

        assertThat(markdown).doesNotContain("Failure triage");
        assertThat(markdown).doesNotContain("Failure runbook");
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
        return new JobDiagnosticsReportVo(Instant.parse("2026-06-28T08:06:00Z"), job, List.of(), 0);
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
}
