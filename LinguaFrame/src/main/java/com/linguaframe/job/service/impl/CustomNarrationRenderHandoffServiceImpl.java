package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.dto.CustomNarrationRenderPreflightDto;
import com.linguaframe.job.domain.vo.CustomNarrationRenderHandoffVo;
import com.linguaframe.job.domain.vo.CustomNarrationRenderPreflightVo;
import com.linguaframe.job.service.CustomNarrationRenderConsoleService;
import com.linguaframe.job.service.CustomNarrationRenderHandoffService;
import org.springframework.stereotype.Service;

@Service
public class CustomNarrationRenderHandoffServiceImpl implements CustomNarrationRenderHandoffService {

    private static final String READY = "READY";
    private static final String ATTENTION = "ATTENTION";
    private static final String BLOCKED = "BLOCKED";
    private static final String NOT_APPLICABLE = "NOT_APPLICABLE";

    private final CustomNarrationRenderConsoleService customNarrationRenderConsoleService;

    public CustomNarrationRenderHandoffServiceImpl(
            CustomNarrationRenderConsoleService customNarrationRenderConsoleService
    ) {
        this.customNarrationRenderConsoleService = customNarrationRenderConsoleService;
    }

    @Override
    public CustomNarrationRenderHandoffVo summarize(String jobId) {
        CustomNarrationRenderPreflightVo preflight = customNarrationRenderConsoleService.preflight(
                jobId,
                new CustomNarrationRenderPreflightDto(false, false, false)
        );
        return new CustomNarrationRenderHandoffVo(
                jobId,
                status(preflight),
                outputPlan(preflight),
                preflight.segmentCount(),
                preflight.characterCount(),
                preflight.audioReady(),
                preflight.videoReady(),
                "/api/jobs/" + jobId + "/custom-narration-render/markdown/download",
                "/api/jobs/" + jobId + "/custom-narration-render",
                "/api/jobs/" + jobId + "/narration-evidence",
                "/api/jobs/" + jobId + "/narration-delivery-package",
                nextAction(preflight)
        );
    }

    private String status(CustomNarrationRenderPreflightVo preflight) {
        if (preflight.segmentCount() == 0) {
            return NOT_APPLICABLE;
        }
        if (preflight.audioReady()) {
            return READY;
        }
        if (BLOCKED.equals(preflight.status())) {
            return BLOCKED;
        }
        return ATTENTION;
    }

    private String outputPlan(CustomNarrationRenderPreflightVo preflight) {
        if (preflight.segmentCount() == 0) {
            return "No saved custom narration rows";
        }
        if (preflight.videoReady()) {
            return "Audio + narrated video";
        }
        if (preflight.audioReady()) {
            return "Audio only";
        }
        return "Not rendered";
    }

    private String nextAction(CustomNarrationRenderPreflightVo preflight) {
        if (preflight.segmentCount() == 0) {
            return "Add or import custom narration rows before rendering.";
        }
        if (preflight.audioReady()) {
            return "Open the custom narration render report and delivery package with the final handoff.";
        }
        if (BLOCKED.equals(preflight.status())) {
            return "Resolve custom narration preflight blockers before rendering.";
        }
        return "Run the custom narration render console before final handoff.";
    }
}
