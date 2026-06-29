package com.linguaframe.job.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.bo.StoredSubtitleReviewEvidencePackageBo;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.SubtitleReviewDecision;
import com.linguaframe.job.domain.enums.SubtitleReviewIssueCategory;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsArtifactVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.SubtitleDraftSegmentVo;
import com.linguaframe.job.domain.vo.SubtitleDraftSummaryVo;
import com.linguaframe.job.domain.vo.SubtitleReviewEvidenceVo;
import com.linguaframe.job.service.impl.SubtitleReviewEvidenceServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubtitleReviewEvidenceServiceTests {

    private final LocalizationJobQueryService queryService = mock(LocalizationJobQueryService.class);
    private final SubtitleDraftService subtitleDraftService = mock(SubtitleDraftService.class);
    private final SubtitleReviewEvidenceService service = new SubtitleReviewEvidenceServiceImpl(
            queryService,
            subtitleDraftService,
            new ObjectMapper().findAndRegisterModules()
    );

    @Test
    void buildsReadyMetadataOnlyEvidenceAndPackage() throws Exception {
        when(queryService.getDiagnosticsReport("job-review-evidence")).thenReturn(report());
        when(subtitleDraftService.getDraft("job-review-evidence", "zh-CN")).thenReturn(readyDraft());

        SubtitleReviewEvidenceVo evidence = service.buildEvidence("job-review-evidence");
        String markdown = service.buildMarkdownEvidence("job-review-evidence");
        StoredSubtitleReviewEvidencePackageBo packageBo = service.openEvidencePackage("job-review-evidence");

        assertThat(evidence.status()).isEqualTo("READY");
        assertThat(evidence.segmentCount()).isEqualTo(3);
        assertThat(evidence.reviewedSegmentCount()).isEqualTo(3);
        assertThat(evidence.acceptedSegmentCount()).isEqualTo(1);
        assertThat(evidence.editedDecisionCount()).isEqualTo(2);
        assertThat(evidence.followupSegmentCount()).isZero();
        assertThat(evidence.annotationCount()).isEqualTo(3);
        assertThat(evidence.reviewerNoteCount()).isEqualTo(2);
        assertThat(evidence.reviewedSubtitleArtifactCount()).isEqualTo(3);
        assertThat(evidence.decisionCounts())
                .extracting(count -> count.category() + ":" + count.count())
                .contains("ACCEPTED:1", "EDITED:2", "NEEDS_FOLLOWUP:0");
        assertThat(evidence.issueCategoryCounts())
                .extracting(count -> count.category() + ":" + count.count())
                .contains("TERM:1", "READABILITY:1", "TIMING:1");
        assertThat(evidence.packageEntries())
                .containsExactly("manifest.json", "subtitle-review-evidence.md", "review-summary.json", "release-notes.md", "README.md");

        assertThat(markdown).contains("# Subtitle Review Evidence");
        assertThat(markdown).contains("- Status: READY");
        assertThat(markdown).contains("- Reviewer notes: 2");
        assertThat(markdown).contains("- TERM: 1");
        assertThat(markdown).doesNotContain("raw source text");
        assertThat(markdown).doesNotContain("raw target text");
        assertThat(markdown).doesNotContain("raw draft text");
        assertThat(markdown).doesNotContain("do not leak this reviewer note");
        assertThat(markdown).doesNotContain("sk-");
        assertThat(markdown).doesNotContain("/Users/");

        assertThat(packageBo.filename()).isEqualTo("linguaframe-job-job-review-evidence-subtitle-review-evidence.zip");
        assertThat(packageEntries(packageBo)).containsExactly(
                "manifest.json",
                "subtitle-review-evidence.md",
                "review-summary.json",
                "release-notes.md",
                "README.md"
        );
    }

    @Test
    void marksAttentionWhenReviewIsPartialOrFollowupRemains() {
        when(queryService.getDiagnosticsReport("job-review-attention")).thenReturn(report("job-review-attention"));
        when(subtitleDraftService.getDraft("job-review-attention", "zh-CN")).thenReturn(attentionDraft());

        SubtitleReviewEvidenceVo evidence = service.buildEvidence("job-review-attention");

        assertThat(evidence.status()).isEqualTo("ATTENTION");
        assertThat(evidence.reviewedSegmentCount()).isEqualTo(2);
        assertThat(evidence.followupSegmentCount()).isEqualTo(1);
        assertThat(evidence.checks())
                .extracting(check -> check.key() + ":" + check.status())
                .contains("review-coverage:WARN", "follow-up:WARN");
    }

    @Test
    void marksBlockedWhenTargetSubtitlesAreMissing() {
        when(queryService.getDiagnosticsReport("job-review-blocked")).thenReturn(report("job-review-blocked"));
        when(subtitleDraftService.getDraft("job-review-blocked", "zh-CN"))
                .thenThrow(new NoSuchElementException("Target subtitles not found"));

        SubtitleReviewEvidenceVo evidence = service.buildEvidence("job-review-blocked");

        assertThat(evidence.status()).isEqualTo("BLOCKED");
        assertThat(evidence.segmentCount()).isZero();
        assertThat(evidence.checks())
                .extracting(check -> check.key() + ":" + check.status())
                .containsExactly("target-subtitles:FAIL");
    }

    private List<String> packageEntries(StoredSubtitleReviewEvidencePackageBo packageBo) throws Exception {
        try (ZipInputStream zipInputStream = new ZipInputStream(packageBo.inputStream())) {
            java.util.ArrayList<String> entries = new java.util.ArrayList<>();
            java.util.zip.ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
            return entries;
        }
    }

    private SubtitleDraftSummaryVo readyDraft() {
        Instant updatedAt = Instant.parse("2026-06-29T09:00:00Z");
        return new SubtitleDraftSummaryVo(
                "job-review-evidence",
                "zh-CN",
                3,
                3,
                3,
                1,
                2,
                0,
                3,
                2,
                updatedAt,
                List.of(
                        segment(0, SubtitleReviewDecision.ACCEPTED, List.of(), null),
                        segment(1, SubtitleReviewDecision.EDITED, List.of(SubtitleReviewIssueCategory.TERM, SubtitleReviewIssueCategory.READABILITY), "do not leak this reviewer note"),
                        segment(2, SubtitleReviewDecision.EDITED, List.of(SubtitleReviewIssueCategory.TIMING), "another hidden note")
                )
        );
    }

    private SubtitleDraftSummaryVo attentionDraft() {
        Instant updatedAt = Instant.parse("2026-06-29T09:00:00Z");
        return new SubtitleDraftSummaryVo(
                "job-review-attention",
                "zh-CN",
                3,
                2,
                2,
                1,
                0,
                1,
                1,
                1,
                updatedAt,
                List.of(
                        segment(0, SubtitleReviewDecision.ACCEPTED, List.of(), null),
                        segment(1, SubtitleReviewDecision.NEEDS_FOLLOWUP, List.of(SubtitleReviewIssueCategory.TIMING), "hidden follow-up note"),
                        segment(2, SubtitleReviewDecision.UNREVIEWED, List.of(), null)
                )
        );
    }

    private SubtitleDraftSegmentVo segment(
            int index,
            SubtitleReviewDecision decision,
            List<SubtitleReviewIssueCategory> categories,
            String note
    ) {
        return new SubtitleDraftSegmentVo(
                index,
                index * 1000L,
                index * 1000L + 900L,
                "raw source text " + index,
                "raw target text " + index,
                "raw draft text " + index,
                decision != SubtitleReviewDecision.UNREVIEWED,
                decision == SubtitleReviewDecision.UNREVIEWED ? null : Instant.parse("2026-06-29T09:00:00Z"),
                decision,
                categories,
                note,
                note == null ? 0 : note.length()
        );
    }

    private JobDiagnosticsReportVo report() {
        return report("job-review-evidence");
    }

    private JobDiagnosticsReportVo report(String jobId) {
        LocalizationJobVo job = new LocalizationJobVo(
                jobId,
                "video-review-evidence",
                "zh-CN",
                null,
                LocalizationJobStatus.COMPLETED,
                Instant.parse("2026-06-29T08:00:00Z"),
                null,
                Instant.parse("2026-06-29T08:10:00Z"),
                null,
                null,
                null,
                0,
                null,
                0,
                null,
                List.of(),
                new JobUsageSummaryVo(0, 0, 0, BigDecimal.ZERO, null, null, null, null),
                new JobCacheSummaryVo(0, 0, 0),
                List.of(),
                null,
                null,
                null
        );
        List<JobDiagnosticsArtifactVo> artifacts = List.of(
                artifact(JobArtifactType.REVIEWED_SUBTITLE_JSON),
                artifact(JobArtifactType.REVIEWED_SUBTITLE_SRT),
                artifact(JobArtifactType.REVIEWED_SUBTITLE_VTT),
                artifact(JobArtifactType.REVIEWED_BURNED_VIDEO)
        );
        return new JobDiagnosticsReportVo(Instant.parse("2026-06-29T08:11:00Z"), job, artifacts, artifacts.size());
    }

    private JobDiagnosticsArtifactVo artifact(JobArtifactType type) {
        return new JobDiagnosticsArtifactVo(
                "artifact-" + type.name(),
                type,
                type.name().toLowerCase() + ".txt",
                "text/plain",
                12,
                "abcdef1234567890",
                false,
                null,
                Instant.parse("2026-06-29T08:10:00Z")
        );
    }
}
