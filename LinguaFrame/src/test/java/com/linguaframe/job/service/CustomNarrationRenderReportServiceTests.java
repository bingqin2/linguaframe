package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.CustomNarrationRenderPreflightVo;
import com.linguaframe.job.domain.vo.CustomNarrationRenderStepVo;
import com.linguaframe.job.domain.vo.CustomNarrationRenderVo;
import com.linguaframe.job.service.impl.CustomNarrationRenderReportServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CustomNarrationRenderReportServiceTests {

    @Test
    void rendersMetadataOnlyMarkdownWithoutNarrationTextOrPaths() {
        CustomNarrationRenderReportService service = new CustomNarrationRenderReportServiceImpl();
        CustomNarrationRenderVo render = new CustomNarrationRenderVo(
                "job-report",
                "PARTIAL",
                true,
                preflight(),
                List.of(
                        new CustomNarrationRenderStepVo("PREFLIGHT", "Run preflight", "SUCCEEDED", "Preflight returned ATTENTION."),
                        new CustomNarrationRenderStepVo("NARRATION_AUDIO", "Generate narration audio", "SUCCEEDED", "Generated narration-audio.mp3."),
                        new CustomNarrationRenderStepVo("NARRATED_VIDEO", "Generate narrated video", "FAILED", "FFmpeg exited with code 1.")
                ),
                null,
                null,
                null,
                null,
                null,
                null,
                1,
                "Review video render failure and retry after fixing the base media."
        );

        String markdown = service.renderMarkdown(render);

        assertThat(markdown).contains("# Custom Narration Render");
        assertThat(markdown).contains("- Job id: `job-report`");
        assertThat(markdown).contains("- Status: `PARTIAL`");
        assertThat(markdown).contains("- Generated artifact count: `1`");
        assertThat(markdown).contains("NARRATED_VIDEO");
        assertThat(markdown).doesNotContain("Sensitive narration text");
        assertThat(markdown).doesNotContain("/Users/");
        assertThat(markdown).doesNotContain("job-artifacts/");
        assertThat(markdown).doesNotContain("sk-");
    }

    private static CustomNarrationRenderPreflightVo preflight() {
        return new CustomNarrationRenderPreflightVo(
                "job-report",
                "ATTENTION",
                List.of(),
                2,
                64,
                new BigDecimal("12.500"),
                "PRESET:demo-voice",
                "ATTENTION",
                "ATTENTION",
                "ATTENTION",
                "demo",
                false,
                true,
                true,
                false,
                List.of("VIDEO_RENDER"),
                "LINGUAFRAME_DEMO_JOB_ID=job-report scripts/demo/custom-narration-render.sh",
                List.of("/api/jobs/job-report/custom-narration-render"),
                List.of("Metadata-only report.")
        );
    }
}
