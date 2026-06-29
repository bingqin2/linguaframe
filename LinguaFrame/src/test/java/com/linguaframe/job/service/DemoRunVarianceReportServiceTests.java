package com.linguaframe.job.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.DemoRunVarianceMetricVo;
import com.linguaframe.job.domain.vo.DemoRunVarianceReportVo;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.service.impl.DemoRunVarianceReportServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DemoRunVarianceReportServiceTests {

    private final LocalizationJobQueryService localizationJobQueryService = mock(LocalizationJobQueryService.class);
    private final DemoRunVarianceReportService service = new DemoRunVarianceReportServiceImpl(
            localizationJobQueryService,
            JsonMapper.builder().addModule(new JavaTimeModule()).build()
    );

    @Test
    void buildsCompletedVarianceWithExecutionPlanBaseline() {
        when(localizationJobQueryService.getJob("job-1")).thenReturn(completedJob());

        DemoRunVarianceReportVo report = service.build("job-1", """
                {
                  "overallStatus": "READY",
                  "estimatedCostUsd": "0.02000000",
                  "estimatedDurationSecondsLower": 20,
                  "estimatedDurationSecondsUpper": 60,
                  "sourceReuseDecision": { "status": "UPLOAD_NEW_SOURCE" },
                  "stages": [
                    { "executionType": "PAID" },
                    { "executionType": "PAID" }
                  ]
                }
                """);
        String markdown = service.renderMarkdown(report);

        assertThat(report.baselineMode()).isEqualTo("EXECUTION_PLAN");
        assertThat(report.overallStatus()).isEqualTo("READY");
        assertThat(metric(report, "estimatedCostUsd").status()).isEqualTo("LOWER_THAN_ESTIMATE");
        assertThat(metric(report, "modelCallCount").status()).isEqualTo("MATCH");
        assertThat(metric(report, "runtimeSeconds").status()).isEqualTo("LOWER_THAN_ESTIMATE");
        assertThat(metric(report, "sourceReuseDecision").estimatedValue()).isEqualTo("UPLOAD_NEW_SOURCE");
        assertThat(markdown).contains(
                "# Demo Run Variance Report",
                "## Summary",
                "## Baseline",
                "## Actual Run",
                "## Variance Metrics",
                "## Delivery Evidence",
                "## Safety Notes",
                "/api/jobs/job-1/demo-run-package/download"
        );
        assertThat(markdown).doesNotContain("/Users/", "source-videos/", "sk-");
    }

    @Test
    void buildsActualOnlyWhenBaselineMissing() {
        when(localizationJobQueryService.getJob("job-1")).thenReturn(completedJob());

        DemoRunVarianceReportVo report = service.build("job-1", " ");

        assertThat(report.baselineMode()).isEqualTo("MISSING");
        assertThat(report.notes()).contains("No pre-upload baseline was supplied; report is actual-only.");
        assertThat(metric(report, "estimatedCostUsd").status()).isEqualTo("BASELINE_MISSING");
        assertThat(service.renderMarkdown(report)).contains("No pre-upload baseline was supplied");
    }

    @Test
    void keepsInvalidBaselineAsAttention() {
        when(localizationJobQueryService.getJob("job-1")).thenReturn(completedJob());

        DemoRunVarianceReportVo report = service.build("job-1", "{bad");

        assertThat(report.baselineMode()).isEqualTo("INVALID");
        assertThat(report.overallStatus()).isEqualTo("ATTENTION");
        assertThat(report.notes()).contains("Pre-upload baseline JSON could not be parsed; report is actual-only.");
    }

    private DemoRunVarianceMetricVo metric(DemoRunVarianceReportVo report, String id) {
        return report.metrics().stream()
                .filter(metric -> metric.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private LocalizationJobVo completedJob() {
        Instant startedAt = Instant.parse("2026-06-29T10:00:00Z");
        return new LocalizationJobVo(
                "job-1",
                "video-1",
                "zh-CN",
                "alloy",
                "NATURAL",
                "STANDARD",
                0,
                "",
                "OFF",
                "demo-fast",
                LocalizationJobStatus.COMPLETED,
                Instant.parse("2026-06-29T09:59:00Z"),
                startedAt,
                startedAt.plusSeconds(45),
                null,
                null,
                null,
                0,
                JobDispatchEventStatus.DISPATCHED,
                1,
                startedAt.minusSeconds(10),
                List.of(),
                new JobUsageSummaryVo(
                        2,
                        0,
                        1800,
                        new BigDecimal("0.01000000"),
                        100,
                        40,
                        null,
                        2000
                ),
                new JobCacheSummaryVo(1, 2, 1),
                List.of(),
                null,
                null,
                null
        );
    }
}
