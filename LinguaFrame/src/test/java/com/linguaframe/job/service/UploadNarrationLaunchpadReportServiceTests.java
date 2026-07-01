package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.NarrationSceneBoardLinkVo;
import com.linguaframe.job.domain.vo.UploadNarrationLaunchpadActionVo;
import com.linguaframe.job.domain.vo.UploadNarrationLaunchpadVo;
import com.linguaframe.job.service.impl.UploadNarrationLaunchpadReportServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UploadNarrationLaunchpadReportServiceTests {

    @Test
    void rendersMetadataOnlyMarkdownWithoutNarrationTextOrStorageDetails() {
        UploadNarrationLaunchpadReportService service = new UploadNarrationLaunchpadReportServiceImpl(
                jobId -> new UploadNarrationLaunchpadVo(
                        jobId,
                        Instant.parse("2026-07-01T00:00:00Z"),
                        "READY",
                        "Preview selected-row TTS explicitly, then run render preflight when ready.",
                        2,
                        54,
                        new BigDecimal("28.000"),
                        0,
                        "demo",
                        "demo-voice",
                        "demo-voice: 1, inherited: 1",
                        "READY",
                        0,
                        0,
                        false,
                        false,
                        List.of(
                                new UploadNarrationLaunchpadActionVo(
                                        "preview-tts",
                                        "Preview selected-row TTS",
                                        "Explicit provider-cost action.",
                                        "/api/jobs/job-launchpad/narration-workspace/segment-preview",
                                        "Preview from the browser."
                                )
                        ),
                        List.of(new NarrationSceneBoardLinkVo(
                                "workspace",
                                "/api/jobs/job-launchpad/narration-workspace",
                                "Narration workspace"
                        )),
                        List.of("No raw narration text is included.")
                )
        );

        String markdown = service.renderMarkdown("job-launchpad");

        assertThat(markdown)
                .contains("# Upload Narration Launchpad")
                .contains("- Job: job-launchpad")
                .contains("- Status: READY")
                .contains("- Segments: 2")
                .contains("- Characters: 54")
                .contains("- Voice provider: demo")
                .contains("- Default voice: demo-voice")
                .contains("- Voice summary: demo-voice: 1, inherited: 1")
                .contains("/api/jobs/job-launchpad/narration-workspace")
                .doesNotContain("Private narration line")
                .doesNotContain("hidden/object/key")
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("sk-test");
    }
}
