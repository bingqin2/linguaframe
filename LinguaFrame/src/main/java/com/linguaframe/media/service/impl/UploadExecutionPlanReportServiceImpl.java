package com.linguaframe.media.service.impl;

import com.linguaframe.media.domain.vo.UploadExecutionPlanCommandVo;
import com.linguaframe.media.domain.vo.UploadExecutionPlanGateVo;
import com.linguaframe.media.domain.vo.UploadExecutionPlanStageVo;
import com.linguaframe.media.domain.vo.UploadExecutionPlanVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseDecisionLinkVo;
import com.linguaframe.media.service.UploadExecutionPlanReportService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class UploadExecutionPlanReportServiceImpl implements UploadExecutionPlanReportService {

    @Override
    public String renderMarkdown(UploadExecutionPlanVo plan) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Upload Execution Plan\n\n");
        markdown.append("## Summary\n\n");
        markdown.append("- Status: ").append(value(plan.overallStatus())).append('\n');
        markdown.append("- Recommended next action: ").append(value(plan.recommendedNextAction())).append('\n');
        markdown.append("- Demo profile: ").append(value(plan.demoProfileId())).append('\n');
        markdown.append("- Target language: ").append(value(plan.targetLanguage())).append("\n\n");

        markdown.append("## Source Metadata\n\n");
        markdown.append("- Filename: ").append(value(plan.filename())).append('\n');
        markdown.append("- Content type: ").append(value(plan.contentType())).append('\n');
        markdown.append("- Size bytes: ").append(plan.fileSizeBytes()).append(" / ").append(plan.maxFileSizeBytes()).append('\n');
        markdown.append("- Duration seconds: ").append(plan.durationSeconds() == null ? "unknown" : plan.durationSeconds()).append(" / ").append(plan.maxDurationSeconds()).append('\n');
        if (plan.sourceReuse() != null && plan.sourceReuse().sourceContentSha256() != null) {
            markdown.append("- Source SHA-256: ").append(plan.sourceReuse().sourceContentSha256()).append('\n');
        }
        markdown.append('\n');

        markdown.append("## Validation\n\n");
        markdown.append("- Valid: ").append(plan.valid()).append('\n');
        markdown.append("- Code: ").append(value(plan.validationCode())).append('\n');
        markdown.append("- Message: ").append(value(plan.validationMessage())).append("\n\n");

        markdown.append("## Cost And Time Estimate\n\n");
        markdown.append("- Estimated cost: ").append(money(plan.estimatedCostUsdLower())).append(" - ")
                .append(money(plan.estimatedCostUsdUpper())).append(" (expected ")
                .append(money(plan.estimatedCostUsd())).append(")\n");
        markdown.append("- Estimated duration seconds: ").append(plan.estimatedDurationSecondsLower())
                .append(" - ").append(plan.estimatedDurationSecondsUpper()).append("\n\n");

        markdown.append("## Source Reuse Decision\n\n");
        if (plan.sourceReuseDecision() == null) {
            markdown.append("- Status: unavailable\n\n");
        } else {
            markdown.append("- Status: ").append(value(plan.sourceReuseDecision().status())).append('\n');
            markdown.append("- Headline: ").append(value(plan.sourceReuseDecision().headline())).append('\n');
            markdown.append("- Summary: ").append(value(plan.sourceReuseDecision().summary())).append('\n');
            markdown.append("- Recommended existing job: ").append(value(plan.sourceReuseDecision().recommendedExistingJobId())).append('\n');
            markdown.append("- Candidate count: ").append(plan.sourceReuseDecision().candidateCount()).append('\n');
            for (UploadSourceReuseDecisionLinkVo link : plan.sourceReuseDecision().links()) {
                markdown.append("- ").append(value(link.label())).append(": ").append(value(link.href())).append('\n');
            }
            markdown.append('\n');
        }

        markdown.append("## Gates\n\n");
        if (plan.gates().isEmpty()) {
            markdown.append("- No gates reported.\n");
        }
        for (UploadExecutionPlanGateVo gate : plan.gates()) {
            markdown.append("- ").append(value(gate.label())).append(": ").append(value(gate.status()))
                    .append(" - ").append(value(gate.detail()))
                    .append(" Next: ").append(value(gate.nextAction())).append('\n');
        }
        markdown.append('\n');

        markdown.append("## Stages\n\n");
        if (plan.stages().isEmpty()) {
            markdown.append("- No runnable stages are planned until upload validation passes.\n");
        }
        for (UploadExecutionPlanStageVo stage : plan.stages()) {
            markdown.append("- ").append(value(stage.label())).append(": ").append(value(stage.executionType()))
                    .append(" / ").append(value(stage.provider())).append(" / ").append(value(stage.model()))
                    .append(" / ").append(money(stage.estimatedCostUsd()))
                    .append(" / ").append(stage.estimatedDurationSecondsLower()).append('-')
                    .append(stage.estimatedDurationSecondsUpper()).append(" seconds")
                    .append(" - ").append(value(stage.detail())).append('\n');
        }
        markdown.append('\n');

        markdown.append("## Commands\n\n");
        if (plan.commands().isEmpty()) {
            markdown.append("- No commands reported.\n");
        }
        for (UploadExecutionPlanCommandVo command : plan.commands()) {
            markdown.append("- ").append(value(command.label())).append(": `")
                    .append(value(command.command())).append("` - ")
                    .append(value(command.description())).append('\n');
        }
        markdown.append('\n');

        markdown.append("## Safety Notes\n\n");
        for (String note : plan.safetyNotes()) {
            markdown.append("- ").append(value(note)).append('\n');
        }
        if (plan.safetyNotes().isEmpty()) {
            markdown.append("- Report is metadata-only and read-only.\n");
        }
        return markdown.toString();
    }

    private String value(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        return value.replace("\r", " ").replace("\n", " ").trim();
    }

    private String money(BigDecimal value) {
        if (value == null) {
            return "$0.00000000";
        }
        return "$" + value.toPlainString();
    }
}
