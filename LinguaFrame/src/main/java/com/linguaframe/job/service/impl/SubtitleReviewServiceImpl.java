package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.SubtitleReviewSegmentStatus;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.domain.vo.SubtitleReviewSegmentVo;
import com.linguaframe.job.domain.vo.SubtitleReviewSummaryVo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.QualityEvaluationService;
import com.linguaframe.job.service.SubtitleReviewService;
import com.linguaframe.job.service.SubtitleService;
import com.linguaframe.job.service.TranscriptService;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SubtitleReviewServiceImpl implements SubtitleReviewService {

    private static final long TIMING_MISMATCH_THRESHOLD_MS = 250L;
    private static final EnumSet<JobArtifactType> REVIEW_SUBTITLE_ARTIFACT_TYPES = EnumSet.of(
            JobArtifactType.TARGET_SUBTITLE_JSON,
            JobArtifactType.TARGET_SUBTITLE_SRT,
            JobArtifactType.TARGET_SUBTITLE_VTT
    );

    private final TranscriptService transcriptService;
    private final SubtitleService subtitleService;
    private final QualityEvaluationService qualityEvaluationService;
    private final JobArtifactService artifactService;

    public SubtitleReviewServiceImpl(
            TranscriptService transcriptService,
            SubtitleService subtitleService,
            QualityEvaluationService qualityEvaluationService,
            JobArtifactService artifactService
    ) {
        this.transcriptService = transcriptService;
        this.subtitleService = subtitleService;
        this.qualityEvaluationService = qualityEvaluationService;
        this.artifactService = artifactService;
    }

    @Override
    public SubtitleReviewSummaryVo buildReview(String jobId, String language) {
        List<TranscriptSegmentVo> transcriptSegments = transcriptService.listTranscript(jobId);
        Map<Integer, SubtitleSegmentVo> targetByIndex = subtitleService.listSubtitles(jobId, language)
                .stream()
                .collect(Collectors.toMap(SubtitleSegmentVo::index, Function.identity(), (first, second) -> second));
        List<SubtitleReviewSegmentVo> reviewSegments = transcriptSegments.stream()
                .map(source -> reviewSegment(source, targetByIndex.get(source.index())))
                .toList();

        int missingTargetCount = countStatus(reviewSegments, SubtitleReviewSegmentStatus.MISSING_TARGET);
        int timingMismatchCount = countStatus(reviewSegments, SubtitleReviewSegmentStatus.TIMING_MISMATCH);
        long maxDurationMs = reviewSegments.stream()
                .mapToLong(SubtitleReviewSegmentVo::durationMs)
                .max()
                .orElse(0L);
        long averageDurationMs = reviewSegments.isEmpty()
                ? 0L
                : Math.round(reviewSegments.stream()
                .mapToLong(SubtitleReviewSegmentVo::durationMs)
                .average()
                .orElse(0));
        QualityEvaluationVo quality = qualityEvaluationService.latestForJob(jobId).orElse(null);

        return new SubtitleReviewSummaryVo(
                jobId,
                language,
                reviewSegments.size(),
                missingTargetCount,
                timingMismatchCount,
                averageDurationMs,
                maxDurationMs,
                quality == null ? null : quality.score(),
                quality == null ? null : quality.verdict(),
                quality == null ? 0 : quality.issues().size(),
                quality == null ? 0 : quality.suggestedFixes().size(),
                downloadableSubtitleArtifactCount(jobId),
                reviewSegments
        );
    }

    private SubtitleReviewSegmentVo reviewSegment(TranscriptSegmentVo source, SubtitleSegmentVo target) {
        long durationMs = Math.max(0L, source.endMs() - source.startMs());
        if (target == null) {
            return new SubtitleReviewSegmentVo(
                    source.index(),
                    source.startMs(),
                    source.endMs(),
                    source.text(),
                    null,
                    durationMs,
                    0L,
                    SubtitleReviewSegmentStatus.MISSING_TARGET
            );
        }
        long timingDeltaMs = Math.max(
                Math.abs(source.startMs() - target.startMs()),
                Math.abs(source.endMs() - target.endMs())
        );
        SubtitleReviewSegmentStatus status = timingDeltaMs > TIMING_MISMATCH_THRESHOLD_MS
                ? SubtitleReviewSegmentStatus.TIMING_MISMATCH
                : SubtitleReviewSegmentStatus.ALIGNED;
        return new SubtitleReviewSegmentVo(
                source.index(),
                source.startMs(),
                source.endMs(),
                source.text(),
                target.text(),
                durationMs,
                timingDeltaMs,
                status
        );
    }

    private int countStatus(List<SubtitleReviewSegmentVo> segments, SubtitleReviewSegmentStatus status) {
        return (int) segments.stream()
                .filter(segment -> segment.status() == status)
                .count();
    }

    private int downloadableSubtitleArtifactCount(String jobId) {
        return (int) artifactService.listArtifacts(jobId).stream()
                .map(JobArtifactVo::type)
                .filter(REVIEW_SUBTITLE_ARTIFACT_TYPES::contains)
                .count();
    }
}
