package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.vo.CustomNarrationRenderStepVo;
import com.linguaframe.job.domain.vo.CustomNarrationRenderPreflightVo;
import com.linguaframe.job.domain.vo.CustomNarrationRenderVo;
import com.linguaframe.job.service.CustomNarrationRenderReportService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomNarrationRenderReportServiceImpl implements CustomNarrationRenderReportService {

    @Override
    public String renderMarkdown(CustomNarrationRenderVo render) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Custom Narration Render\n\n");
        markdown.append("- Job id: `").append(render.jobId()).append("`\n");
        markdown.append("- Status: `").append(render.status()).append("`\n");
        markdown.append("- Generate narrated video: `").append(render.generateNarratedVideo()).append("`\n");
        markdown.append("- Generated artifact count: `").append(render.generatedArtifactCount()).append("`\n");
        markdown.append("- Next action: ").append(render.nextAction()).append("\n\n");
        if (render.preflight() != null) {
            markdown.append("## Preflight\n\n");
            markdown.append("- Status: `").append(render.preflight().status()).append("`\n");
            markdown.append("- Segment count: `").append(render.preflight().segmentCount()).append("`\n");
            markdown.append("- Character count: `").append(render.preflight().characterCount()).append("`\n");
            markdown.append("- Provider mode: `").append(render.preflight().providerMode()).append("`\n");
            markdown.append("- Voice summary: `").append(render.preflight().voiceSummary()).append("`\n\n");
        }
        markdown.append("## Steps\n\n");
        for (CustomNarrationRenderStepVo step : render.steps()) {
            markdown.append("- `")
                    .append(step.key())
                    .append("` ")
                    .append(step.status())
                    .append(": ")
                    .append(step.message())
                    .append("\n");
        }
        markdown.append("\n## Safe Routes\n\n");
        if (render.preflight() != null) {
            render.preflight().safeRoutes().forEach(route -> markdown.append("- `").append(route).append("`\n"));
        }
        markdown.append("\nMetadata-only report. Narration text, reviewer notes, local paths, object keys, provider payloads, secrets, and media bytes are omitted.\n");
        return markdown.toString();
    }

    @Override
    public CustomNarrationRenderVo reportOnly(String jobId, CustomNarrationRenderPreflightVo preflight) {
        return new CustomNarrationRenderVo(
                jobId,
                preflight.status(),
                preflight.generateNarratedVideo(),
                preflight,
                List.of(
                        new CustomNarrationRenderStepVo("PREFLIGHT", "Run preflight", "SUCCEEDED", "Preflight returned " + preflight.status() + "."),
                        new CustomNarrationRenderStepVo("NARRATION_AUDIO", "Generate narration audio", "SKIPPED", "Report-only download does not call TTS providers."),
                        new CustomNarrationRenderStepVo("NARRATED_VIDEO", "Generate narrated video", "SKIPPED", "Report-only download does not run FFmpeg.")
                ),
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                "Run POST /api/jobs/" + jobId + "/custom-narration-render after confirming provider cost and video render settings."
        );
    }
}
