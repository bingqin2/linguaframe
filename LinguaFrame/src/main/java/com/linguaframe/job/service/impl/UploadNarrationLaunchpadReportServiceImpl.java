package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.vo.NarrationSceneBoardLinkVo;
import com.linguaframe.job.domain.vo.UploadNarrationLaunchpadActionVo;
import com.linguaframe.job.domain.vo.UploadNarrationLaunchpadVo;
import com.linguaframe.job.service.UploadNarrationLaunchpadReportService;
import com.linguaframe.job.service.UploadNarrationLaunchpadService;
import org.springframework.stereotype.Service;

@Service
public class UploadNarrationLaunchpadReportServiceImpl implements UploadNarrationLaunchpadReportService {

    private final UploadNarrationLaunchpadService uploadNarrationLaunchpadService;

    public UploadNarrationLaunchpadReportServiceImpl(UploadNarrationLaunchpadService uploadNarrationLaunchpadService) {
        this.uploadNarrationLaunchpadService = uploadNarrationLaunchpadService;
    }

    @Override
    public String renderMarkdown(String jobId) {
        UploadNarrationLaunchpadVo launchpad = uploadNarrationLaunchpadService.getLaunchpad(jobId);
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Upload Narration Launchpad\n\n");
        markdown.append("- Job: ").append(launchpad.jobId()).append('\n');
        markdown.append("- Status: ").append(launchpad.status()).append('\n');
        markdown.append("- Next action: ").append(launchpad.nextAction()).append('\n');
        markdown.append("- Segments: ").append(launchpad.segmentCount()).append('\n');
        markdown.append("- Characters: ").append(launchpad.characterCount()).append('\n');
        markdown.append("- Total narration seconds: ").append(launchpad.totalNarrationSeconds()).append('\n');
        markdown.append("- Voice provider: ").append(launchpad.voiceProvider()).append('\n');
        markdown.append("- Default voice: ").append(launchpad.defaultVoice()).append('\n');
        markdown.append("- Voice summary: ").append(launchpad.voiceSummary()).append('\n');
        markdown.append("- Scene-board status: ").append(launchpad.sceneBoardStatus()).append('\n');
        markdown.append("- Blocking issues: ").append(launchpad.blockingIssueCount()).append('\n');
        markdown.append("- Attention issues: ").append(launchpad.attentionIssueCount()).append('\n');
        markdown.append("- Narration audio ready: ").append(launchpad.audioReady()).append('\n');
        markdown.append("- Narrated video ready: ").append(launchpad.videoReady()).append("\n\n");
        markdown.append("## Actions\n\n");
        for (UploadNarrationLaunchpadActionVo action : launchpad.actions()) {
            markdown.append("- ").append(action.label()).append(": ").append(action.description())
                    .append(" (").append(action.href()).append(")");
            if (action.command() != null && !action.command().isBlank()) {
                markdown.append(" `").append(action.command()).append('`');
            }
            markdown.append('\n');
        }
        markdown.append("\n## Safe Links\n\n");
        for (NarrationSceneBoardLinkVo link : launchpad.safeLinks()) {
            markdown.append("- ").append(link.label()).append(": ").append(link.href()).append('\n');
        }
        markdown.append("\n## Safety Notes\n\n");
        for (String note : launchpad.safetyNotes()) {
            markdown.append("- ").append(note).append('\n');
        }
        return markdown.toString();
    }
}
