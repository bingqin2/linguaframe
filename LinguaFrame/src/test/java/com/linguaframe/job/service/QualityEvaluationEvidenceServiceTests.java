package com.linguaframe.job.service;

import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.QualityEvaluationStatus;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.service.impl.QualityEvaluationEvidenceServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QualityEvaluationEvidenceServiceTests {

    @Test
    void opensSafeMarkdownEvidenceForRecordedEvaluation() throws IOException {
        QualityEvaluationEvidenceService service = new QualityEvaluationEvidenceServiceImpl(
                new RecordingLocalizationJobQueryService(job(qualityEvaluation()))
        );

        var result = service.openMarkdownEvidence("job-quality-evidence");

        assertThat(result.filename()).isEqualTo("linguaframe-job-job-quality-evidence-quality-evidence.md");
        assertThat(result.contentType()).isEqualTo("text/markdown;charset=UTF-8");
        assertThat(result.sizeBytes()).isPositive();
        String markdown = new String(result.inputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(markdown)
                .contains("# LinguaFrame Quality Evaluation Evidence")
                .contains("- Job: job-quality-evidence")
                .contains("- Video: video-quality-evidence")
                .contains("- Target language: zh-CN")
                .contains("- Status: SUCCEEDED")
                .contains("- Score: 92 / 100")
                .contains("- Verdict: GOOD")
                .contains("- Evaluation language: zh-CN")
                .contains("- Completeness: 95 / 100")
                .contains("- Readability: 92 / 100")
                .contains("- Timing preservation: 94 / 100")
                .contains("- Naturalness: 88 / 100")
                .contains("- Issue count: 1")
                .contains("- No blocking issue.")
                .contains("- Suggested fix count: 1")
                .contains("- Review terminology.")
                .contains("/api/jobs/job-quality-evidence")
                .contains("/api/jobs/job-quality-evidence/diagnostics/download")
                .contains("/api/jobs/job-quality-evidence/evidence/markdown/download")
                .contains("/api/jobs/job-quality-evidence/subtitle-review?language=zh-CN");
        assertThat(markdown)
                .doesNotContain("raw transcript text")
                .doesNotContain("raw target text")
                .doesNotContain("provider request payload")
                .doesNotContain("job-artifacts/")
                .doesNotContain("/Users/")
                .doesNotContain("sk-");
    }

    @Test
    void opensNotRecordedMarkdownWhenEvaluationIsMissing() throws IOException {
        QualityEvaluationEvidenceService service = new QualityEvaluationEvidenceServiceImpl(
                new RecordingLocalizationJobQueryService(job(null))
        );

        var result = service.openMarkdownEvidence("job-quality-evidence");

        String markdown = new String(result.inputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(markdown)
                .contains("- Job: job-quality-evidence")
                .contains("- Status: NOT_RECORDED")
                .contains("- Quality evaluation has not been recorded for this job.");
        assertThat(markdown)
                .doesNotContain("raw transcript text")
                .doesNotContain("provider request payload");
    }

    private static QualityEvaluationVo qualityEvaluation() {
        return new QualityEvaluationVo(
                "quality-evaluation-id",
                "job-quality-evidence",
                "zh-CN",
                92,
                "GOOD",
                95,
                92,
                94,
                88,
                List.of("No blocking issue."),
                List.of("Review terminology."),
                QualityEvaluationStatus.SUCCEEDED,
                null,
                Instant.parse("2026-06-28T10:00:00Z")
        );
    }

    private static LocalizationJobVo job(QualityEvaluationVo qualityEvaluation) {
        return new LocalizationJobVo(
                "job-quality-evidence",
                "video-quality-evidence",
                "zh-CN",
                "verse",
                LocalizationJobStatus.COMPLETED,
                Instant.parse("2026-06-28T09:00:00Z"),
                Instant.parse("2026-06-28T09:01:00Z"),
                Instant.parse("2026-06-28T09:05:00Z"),
                null,
                null,
                "provider request payload raw transcript text raw target text sk-test /Users/example job-artifacts/raw.json",
                0,
                JobDispatchEventStatus.DISPATCHED,
                1,
                Instant.parse("2026-06-28T09:00:30Z"),
                List.of(),
                new JobUsageSummaryVo(1, 0, 1200, BigDecimal.valueOf(0.012), null, null, null, null),
                new JobCacheSummaryVo(0, 0, 0),
                List.of(),
                qualityEvaluation,
                null,
                null
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
}
