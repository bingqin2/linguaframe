package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.bo.StoredQualityEvidenceBo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.job.service.QualityEvaluationEvidenceService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class QualityEvaluationEvidenceServiceImpl implements QualityEvaluationEvidenceService {

    private static final String CONTENT_TYPE = "text/markdown;charset=UTF-8";

    private final LocalizationJobQueryService queryService;

    public QualityEvaluationEvidenceServiceImpl(LocalizationJobQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    public StoredQualityEvidenceBo openMarkdownEvidence(String jobId) {
        LocalizationJobVo job = queryService.getJob(jobId);
        byte[] body = buildMarkdown(job).getBytes(StandardCharsets.UTF_8);
        return new StoredQualityEvidenceBo(
                "linguaframe-job-" + safeFilenamePart(job.jobId()) + "-quality-evidence.md",
                CONTENT_TYPE,
                body.length,
                new ByteArrayInputStream(body)
        );
    }

    private String buildMarkdown(LocalizationJobVo job) {
        List<String> lines = new ArrayList<>();
        lines.add("# LinguaFrame Quality Evaluation Evidence");
        lines.add("");
        lines.add("## Job");
        lines.add("- Job: " + value(job.jobId()));
        lines.add("- Video: " + value(job.videoId()));
        lines.add("- Target language: " + value(job.targetLanguage()));
        lines.add("- Job status: " + value(job.status()));
        lines.add("- Created at: " + value(job.createdAt()));
        lines.add("");

        QualityEvaluationVo evaluation = job.qualityEvaluation();
        if (evaluation == null) {
            lines.add("## Evaluation");
            lines.add("- Status: NOT_RECORDED");
            lines.add("- Quality evaluation has not been recorded for this job.");
            lines.add("");
        } else {
            lines.add("## Evaluation");
            lines.add("- Status: " + value(evaluation.status()));
            lines.add("- Score: " + evaluation.score() + " / 100");
            lines.add("- Verdict: " + value(evaluation.verdict()));
            lines.add("- Evaluation language: " + value(evaluation.language()));
            lines.add("- Created at: " + value(evaluation.createdAt()));
            if (evaluation.safeErrorSummary() != null && !evaluation.safeErrorSummary().isBlank()) {
                lines.add("- Safe error summary: " + evaluation.safeErrorSummary());
            }
            lines.add("");
            lines.add("## Dimensions");
            lines.add("- Completeness: " + evaluation.completeness() + " / 100");
            lines.add("- Readability: " + evaluation.readability() + " / 100");
            lines.add("- Timing preservation: " + evaluation.timingPreservation() + " / 100");
            lines.add("- Naturalness: " + evaluation.naturalness() + " / 100");
            lines.add("");
            appendList(lines, "Issues", "Issue count", evaluation.issues());
            appendList(lines, "Suggested Fixes", "Suggested fix count", evaluation.suggestedFixes());
        }

        lines.add("## Related Safe Routes");
        lines.add("- Job detail: /api/jobs/" + job.jobId());
        lines.add("- Diagnostics: /api/jobs/" + job.jobId() + "/diagnostics/download");
        lines.add("- Backend evidence: /api/jobs/" + job.jobId() + "/evidence/markdown/download");
        lines.add("- Subtitle review: /api/jobs/" + job.jobId() + "/subtitle-review?language=" + value(job.targetLanguage()));
        lines.add("");
        return String.join("\n", lines);
    }

    private static void appendList(List<String> lines, String title, String countLabel, List<String> values) {
        List<String> safeValues = values == null ? List.of() : values;
        lines.add("## " + title);
        lines.add("- " + countLabel + ": " + safeValues.size());
        if (safeValues.isEmpty()) {
            lines.add("- None recorded.");
        } else {
            for (String value : safeValues) {
                lines.add("- " + value);
            }
        }
        lines.add("");
    }

    private static String value(Object value) {
        return value == null ? "N/A" : value.toString();
    }

    private static String safeFilenamePart(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "-");
    }
}
