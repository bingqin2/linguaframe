package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.bo.StoredSubtitleReviewEvidencePackageBo;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.SubtitleReviewDecision;
import com.linguaframe.job.domain.enums.SubtitleReviewIssueCategory;
import com.linguaframe.job.domain.vo.JobDiagnosticsArtifactVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.ReviewedSubtitleWorkflowLinkVo;
import com.linguaframe.job.domain.vo.SubtitleDraftSegmentVo;
import com.linguaframe.job.domain.vo.SubtitleDraftSummaryVo;
import com.linguaframe.job.domain.vo.SubtitleReviewEvidenceCategoryVo;
import com.linguaframe.job.domain.vo.SubtitleReviewEvidenceCheckVo;
import com.linguaframe.job.domain.vo.SubtitleReviewEvidenceVo;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.job.service.SubtitleDraftService;
import com.linguaframe.job.service.SubtitleReviewEvidenceService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class SubtitleReviewEvidenceServiceImpl implements SubtitleReviewEvidenceService {

    private static final String CONTENT_TYPE = "application/zip";
    private static final List<String> PACKAGE_ENTRIES = List.of(
            "manifest.json",
            "subtitle-review-evidence.md",
            "review-summary.json",
            "release-notes.md",
            "README.md"
    );

    private final LocalizationJobQueryService queryService;
    private final SubtitleDraftService subtitleDraftService;
    private final ObjectMapper objectMapper;

    public SubtitleReviewEvidenceServiceImpl(
            LocalizationJobQueryService queryService,
            SubtitleDraftService subtitleDraftService,
            ObjectMapper objectMapper
    ) {
        this.queryService = queryService;
        this.subtitleDraftService = subtitleDraftService;
        this.objectMapper = objectMapper;
    }

    @Override
    public SubtitleReviewEvidenceVo buildEvidence(String jobId) {
        JobDiagnosticsReportVo diagnostics = queryService.getDiagnosticsReport(jobId);
        LocalizationJobVo job = diagnostics.job();
        SubtitleDraftSummaryVo draft;
        try {
            draft = subtitleDraftService.getDraft(job.jobId(), job.targetLanguage());
        } catch (NoSuchElementException ex) {
            return blockedEvidence(job, diagnostics);
        }

        int reviewedSubtitleArtifacts = reviewedSubtitleArtifactCount(diagnostics.artifacts());
        boolean reviewedBurnedVideoAvailable = diagnostics.artifacts().stream()
                .anyMatch(artifact -> artifact.type() == JobArtifactType.REVIEWED_BURNED_VIDEO);
        List<SubtitleReviewEvidenceCategoryVo> decisionCounts = decisionCounts(draft);
        List<SubtitleReviewEvidenceCategoryVo> issueCategoryCounts = issueCategoryCounts(draft);
        List<SubtitleReviewEvidenceCheckVo> checks = checks(draft, reviewedSubtitleArtifacts);
        String status = status(draft);

        return new SubtitleReviewEvidenceVo(
                job.jobId(),
                job.videoId(),
                job.targetLanguage(),
                Instant.now(),
                status,
                summary(status),
                draft.segmentCount(),
                draft.reviewedSegmentCount(),
                draft.acceptedSegmentCount(),
                draft.editedDecisionCount(),
                draft.followupSegmentCount(),
                draft.annotationCount(),
                draft.reviewerNoteCount(),
                reviewedSubtitleArtifacts,
                reviewedBurnedVideoAvailable,
                0,
                decisionCounts,
                issueCategoryCounts,
                checks,
                links(job.jobId()),
                PACKAGE_ENTRIES,
                safetyNotes()
        );
    }

    @Override
    public String buildMarkdownEvidence(String jobId) {
        return markdown(buildEvidence(jobId));
    }

    @Override
    public StoredSubtitleReviewEvidencePackageBo openEvidencePackage(String jobId) {
        SubtitleReviewEvidenceVo evidence = buildEvidence(jobId);
        byte[] manifestJson = writeJson(manifest(evidence));
        byte[] reviewSummaryJson = writeJson(evidence);
        byte[] markdown = markdown(evidence).getBytes(StandardCharsets.UTF_8);
        byte[] releaseNotes = releaseNotes(evidence).getBytes(StandardCharsets.UTF_8);
        byte[] readme = readme(evidence).getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream archiveBytes = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(archiveBytes)) {
            writeEntry(zipOutputStream, "manifest.json", manifestJson);
            writeEntry(zipOutputStream, "subtitle-review-evidence.md", markdown);
            writeEntry(zipOutputStream, "review-summary.json", reviewSummaryJson);
            writeEntry(zipOutputStream, "release-notes.md", releaseNotes);
            writeEntry(zipOutputStream, "README.md", readme);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create subtitle review evidence package.", ex);
        }

        byte[] content = archiveBytes.toByteArray();
        return new StoredSubtitleReviewEvidencePackageBo(
                "linguaframe-job-%s-subtitle-review-evidence.zip".formatted(jobId),
                CONTENT_TYPE,
                content.length,
                new ByteArrayInputStream(content)
        );
    }

    private SubtitleReviewEvidenceVo blockedEvidence(LocalizationJobVo job, JobDiagnosticsReportVo diagnostics) {
        int reviewedSubtitleArtifacts = reviewedSubtitleArtifactCount(diagnostics.artifacts());
        boolean reviewedBurnedVideoAvailable = diagnostics.artifacts().stream()
                .anyMatch(artifact -> artifact.type() == JobArtifactType.REVIEWED_BURNED_VIDEO);
        return new SubtitleReviewEvidenceVo(
                job.jobId(),
                job.videoId(),
                job.targetLanguage(),
                Instant.now(),
                "BLOCKED",
                "Target subtitles are missing, so subtitle review evidence cannot be produced.",
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                reviewedSubtitleArtifacts,
                reviewedBurnedVideoAvailable,
                0,
                List.of(),
                List.of(),
                List.of(new SubtitleReviewEvidenceCheckVo(
                        "target-subtitles",
                        "Target subtitles available",
                        "FAIL",
                        "Generated target subtitles are required before review evidence can be produced."
                )),
                links(job.jobId()),
                PACKAGE_ENTRIES,
                safetyNotes()
        );
    }

    private List<SubtitleReviewEvidenceCategoryVo> decisionCounts(SubtitleDraftSummaryVo draft) {
        Map<SubtitleReviewDecision, Integer> counts = new EnumMap<>(SubtitleReviewDecision.class);
        for (SubtitleReviewDecision decision : SubtitleReviewDecision.values()) {
            counts.put(decision, 0);
        }
        for (SubtitleDraftSegmentVo segment : draft.segments()) {
            counts.merge(segment.decision(), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .map(entry -> new SubtitleReviewEvidenceCategoryVo(entry.getKey().name(), entry.getValue()))
                .toList();
    }

    private List<SubtitleReviewEvidenceCategoryVo> issueCategoryCounts(SubtitleDraftSummaryVo draft) {
        Map<SubtitleReviewIssueCategory, Integer> counts = new EnumMap<>(SubtitleReviewIssueCategory.class);
        for (SubtitleDraftSegmentVo segment : draft.segments()) {
            for (SubtitleReviewIssueCategory category : segment.issueCategories()) {
                counts.merge(category, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .map(entry -> new SubtitleReviewEvidenceCategoryVo(entry.getKey().name(), entry.getValue()))
                .toList();
    }

    private List<SubtitleReviewEvidenceCheckVo> checks(SubtitleDraftSummaryVo draft, int reviewedSubtitleArtifacts) {
        List<SubtitleReviewEvidenceCheckVo> checks = new ArrayList<>();
        checks.add(new SubtitleReviewEvidenceCheckVo(
                "review-coverage",
                "All subtitle segments reviewed",
                draft.reviewedSegmentCount() == draft.segmentCount() ? "PASS" : "WARN",
                draft.reviewedSegmentCount() + " of " + draft.segmentCount() + " subtitle segments have review decisions."
        ));
        checks.add(new SubtitleReviewEvidenceCheckVo(
                "follow-up",
                "No follow-up segments remain",
                draft.followupSegmentCount() == 0 ? "PASS" : "WARN",
                draft.followupSegmentCount() + " subtitle segments are marked NEEDS_FOLLOWUP."
        ));
        checks.add(new SubtitleReviewEvidenceCheckVo(
                "reviewed-artifacts",
                "Reviewed subtitle artifacts published",
                reviewedSubtitleArtifacts >= 3 ? "PASS" : "WARN",
                reviewedSubtitleArtifacts + " reviewed subtitle artifacts are available."
        ));
        checks.add(new SubtitleReviewEvidenceCheckVo(
                "metadata-only",
                "Evidence is metadata-only",
                "PASS",
                "Evidence excludes transcript text, subtitle text, annotation note bodies, media bytes, object keys, and provider payloads."
        ));
        return checks;
    }

    private String status(SubtitleDraftSummaryVo draft) {
        if (draft.reviewedSegmentCount() == draft.segmentCount() && draft.followupSegmentCount() == 0) {
            return "READY";
        }
        return "ATTENTION";
    }

    private String summary(String status) {
        return switch (status) {
            case "READY" -> "All subtitle segments are reviewed with no follow-up remaining.";
            case "BLOCKED" -> "Target subtitles are missing.";
            default -> "Subtitle review evidence exists but still needs reviewer attention.";
        };
    }

    private int reviewedSubtitleArtifactCount(List<JobDiagnosticsArtifactVo> artifacts) {
        return (int) artifacts.stream()
                .filter(artifact -> artifact.type() == JobArtifactType.REVIEWED_SUBTITLE_JSON
                        || artifact.type() == JobArtifactType.REVIEWED_SUBTITLE_SRT
                        || artifact.type() == JobArtifactType.REVIEWED_SUBTITLE_VTT)
                .count();
    }

    private List<ReviewedSubtitleWorkflowLinkVo> links(String jobId) {
        return List.of(
                link("SUBTITLE_DRAFT", "Subtitle draft", "/api/jobs/" + jobId + "/subtitle-draft"),
                link("SUBTITLE_REVIEW_EVIDENCE", "Subtitle review evidence", "/api/jobs/" + jobId + "/subtitle-review-evidence"),
                link("SUBTITLE_REVIEW_EVIDENCE_MARKDOWN", "Subtitle review evidence Markdown", "/api/jobs/" + jobId + "/subtitle-review-evidence/markdown/download"),
                link("SUBTITLE_REVIEW_EVIDENCE_PACKAGE", "Subtitle review evidence package", "/api/jobs/" + jobId + "/subtitle-review-evidence/download"),
                link("HANDOFF_PACKAGE", "Handoff package", "/api/jobs/" + jobId + "/handoff-package/download")
        );
    }

    private ReviewedSubtitleWorkflowLinkVo link(String key, String label, String href) {
        return new ReviewedSubtitleWorkflowLinkVo(key, label, href);
    }

    private List<String> safetyNotes() {
        return List.of(
                "Metadata-only evidence: raw transcript text, generated subtitle text, corrected subtitle text, and reviewer note bodies are excluded.",
                "No API keys, bearer tokens, object storage keys, provider payloads, local filesystem paths, or media bytes are included.",
                "Reviewer note bodies remain visible only in the interactive subtitle draft editor."
        );
    }

    private String markdown(SubtitleReviewEvidenceVo evidence) {
        List<String> lines = new ArrayList<>();
        lines.add("# Subtitle Review Evidence");
        lines.add("");
        lines.add("- Job: " + evidence.jobId());
        lines.add("- Video: " + evidence.videoId());
        lines.add("- Target language: " + evidence.targetLanguage());
        lines.add("- Status: " + evidence.status());
        lines.add("- Summary: " + evidence.summary());
        lines.add("- Segments: " + evidence.segmentCount());
        lines.add("- Reviewed segments: " + evidence.reviewedSegmentCount());
        lines.add("- Accepted: " + evidence.acceptedSegmentCount());
        lines.add("- Edited: " + evidence.editedDecisionCount());
        lines.add("- Needs follow-up: " + evidence.followupSegmentCount());
        lines.add("- Issue annotations: " + evidence.annotationCount());
        lines.add("- Reviewer notes: " + evidence.reviewerNoteCount());
        lines.add("- Reviewed subtitle artifacts: " + evidence.reviewedSubtitleArtifactCount());
        lines.add("- Reviewed burned video: " + (evidence.reviewedBurnedVideoAvailable() ? "Available" : "Not available"));
        lines.add("");
        addCategorySection(lines, "Decision Counts", evidence.decisionCounts());
        addCategorySection(lines, "Issue Category Counts", evidence.issueCategoryCounts());
        lines.add("## Checks");
        for (SubtitleReviewEvidenceCheckVo check : evidence.checks()) {
            lines.add("- " + check.status() + " " + check.label() + ": " + check.detail());
        }
        lines.add("");
        lines.add("## Package Entries");
        for (String entry : evidence.packageEntries()) {
            lines.add("- " + entry);
        }
        lines.add("");
        lines.add("## Safety Notes");
        for (String note : evidence.safetyNotes()) {
            lines.add("- " + note);
        }
        return String.join("\n", lines);
    }

    private void addCategorySection(
            List<String> lines,
            String title,
            List<SubtitleReviewEvidenceCategoryVo> counts
    ) {
        lines.add("## " + title);
        if (counts.isEmpty()) {
            lines.add("- None recorded.");
        } else {
            for (SubtitleReviewEvidenceCategoryVo count : counts) {
                lines.add("- " + count.category() + ": " + count.count());
            }
        }
        lines.add("");
    }

    private String releaseNotes(SubtitleReviewEvidenceVo evidence) {
        return String.join("\n", List.of(
                "# Release Notes",
                "",
                "- Status: " + evidence.status(),
                "- Reviewed subtitle artifacts: " + evidence.reviewedSubtitleArtifactCount(),
                "- Reviewed segments: " + evidence.reviewedSegmentCount() + " / " + evidence.segmentCount(),
                "- Follow-up segments: " + evidence.followupSegmentCount(),
                "",
                "This generated release-note stub is metadata-only and excludes subtitle text and reviewer note bodies."
        ));
    }

    private String readme(SubtitleReviewEvidenceVo evidence) {
        return String.join("\n", List.of(
                "# Subtitle Review Evidence Package",
                "",
                "This package proves subtitle review coverage for job " + evidence.jobId() + ".",
                "It is safe to share because it excludes media bytes, transcript text, subtitle text, object keys, provider payloads, and reviewer note bodies.",
                "",
                "Entries:",
                "- manifest.json",
                "- subtitle-review-evidence.md",
                "- review-summary.json",
                "- release-notes.md",
                "- README.md"
        ));
    }

    private Map<String, Object> manifest(SubtitleReviewEvidenceVo evidence) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("generatedAt", evidence.generatedAt());
        manifest.put("jobId", evidence.jobId());
        manifest.put("videoId", evidence.videoId());
        manifest.put("targetLanguage", evidence.targetLanguage());
        manifest.put("status", evidence.status());
        manifest.put("entries", evidence.packageEntries());
        manifest.put("safety", Map.of(
                "includesMediaBytes", false,
                "includesRawTranscriptText", false,
                "includesRawSubtitleText", false,
                "includesReviewerNoteBodies", false,
                "includesObjectKeys", false,
                "includesProviderPayloads", false
        ));
        return manifest;
    }

    private byte[] writeJson(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to write subtitle review evidence JSON.", ex);
        }
    }

    private void writeEntry(ZipOutputStream zipOutputStream, String name, byte[] content) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(content);
        zipOutputStream.closeEntry();
    }
}
