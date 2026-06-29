package com.linguaframe.media.service.impl;

import com.linguaframe.common.security.DemoOwnerIdentityService;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.LocalizationJobSummaryVo;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.media.domain.bo.UploadCostEstimateOptionsBo;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.vo.UploadCostEstimateVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseCandidateVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseVo;
import com.linguaframe.media.repository.VideoRepository;
import com.linguaframe.media.service.SourceMediaFingerprintService;
import com.linguaframe.media.service.UploadSourceReuseService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class UploadSourceReuseServiceImpl implements UploadSourceReuseService {

    private static final int MAX_MATCHED_VIDEOS = 10;
    private static final int MAX_JOBS_PER_VIDEO = 3;

    private final SourceMediaFingerprintService fingerprintService;
    private final VideoRepository videoRepository;
    private final LocalizationJobRepository jobRepository;
    private final DemoOwnerIdentityService ownerIdentityService;

    public UploadSourceReuseServiceImpl(
            SourceMediaFingerprintService fingerprintService,
            VideoRepository videoRepository,
            LocalizationJobRepository jobRepository,
            DemoOwnerIdentityService ownerIdentityService
    ) {
        this.fingerprintService = fingerprintService;
        this.videoRepository = videoRepository;
        this.jobRepository = jobRepository;
        this.ownerIdentityService = ownerIdentityService;
    }

    @Override
    public UploadSourceReuseVo evaluate(MultipartFile file, UploadCostEstimateVo estimate, UploadCostEstimateOptionsBo options) {
        if (estimate == null || !estimate.valid()) {
            return UploadSourceReuseVo.empty();
        }
        String fingerprint = fingerprintService.sha256(file);
        String ownerId = ownerIdentityService.currentOwnerId();
        List<UploadSourceReuseCandidateVo> candidates = videoRepository
                .findRecentByOwnerIdAndSourceContentSha256(ownerId, fingerprint, MAX_MATCHED_VIDEOS)
                .stream()
                .flatMap(video -> jobRepository
                        .findSummariesByVideoIdAndOwnerId(video.id(), ownerId, MAX_JOBS_PER_VIDEO)
                        .stream()
                        .map(job -> candidate(video, job)))
                .sorted(Comparator.comparing(UploadSourceReuseCandidateVo::createdAt).reversed())
                .toList();
        Optional<UploadSourceReuseCandidateVo> recommended = recommendedCandidate(candidates, estimate);
        return new UploadSourceReuseVo(
                fingerprint,
                candidates.size(),
                recommendedAction(candidates, recommended),
                recommended.map(UploadSourceReuseCandidateVo::jobId).orElse(null),
                candidates
        );
    }

    private UploadSourceReuseCandidateVo candidate(VideoRecord video, LocalizationJobSummaryVo job) {
        return new UploadSourceReuseCandidateVo(
                video.id(),
                job.jobId(),
                video.originalFilename(),
                video.durationSeconds(),
                job.status(),
                job.demoProfileId(),
                job.translationStyle(),
                job.subtitleStylePreset(),
                job.subtitlePolishingMode(),
                job.createdAt(),
                "/api/jobs/" + job.jobId(),
                "/api/jobs/" + job.jobId() + "/demo-share-sheet",
                "/api/jobs/" + job.jobId() + "/evidence/markdown/download",
                "/api/jobs/" + job.jobId() + "/demo-run-package/download",
                "/api/jobs/" + job.jobId() + "/demo-acceptance-gate"
        );
    }

    private Optional<UploadSourceReuseCandidateVo> recommendedCandidate(
            List<UploadSourceReuseCandidateVo> candidates,
            UploadCostEstimateVo estimate
    ) {
        return candidates.stream()
                .filter(candidate -> candidate.jobStatus() == LocalizationJobStatus.COMPLETED)
                .filter(candidate -> matchesOptions(candidate, estimate))
                .findFirst()
                .or(() -> candidates.stream()
                        .filter(candidate -> candidate.jobStatus() == LocalizationJobStatus.COMPLETED)
                        .findFirst())
                .or(() -> candidates.stream()
                        .filter(candidate -> isActive(candidate.jobStatus()))
                        .findFirst());
    }

    private boolean matchesOptions(UploadSourceReuseCandidateVo candidate, UploadCostEstimateVo estimate) {
        return same(candidate.demoProfileId(), estimate.demoProfileId())
                && same(candidate.translationStyle(), estimate.translationStyle())
                && same(candidate.subtitleStylePreset(), estimate.subtitleStylePreset())
                && same(candidate.subtitlePolishingMode(), estimate.subtitlePolishingMode());
    }

    private boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private String recommendedAction(
            List<UploadSourceReuseCandidateVo> candidates,
            Optional<UploadSourceReuseCandidateVo> recommended
    ) {
        if (candidates.isEmpty() || recommended.isEmpty()) {
            return "UPLOAD_NEW_SOURCE";
        }
        if (recommended.get().jobStatus() == LocalizationJobStatus.COMPLETED) {
            return "REVIEW_EXISTING_COMPLETED_RUN";
        }
        if (isActive(recommended.get().jobStatus())) {
            return "WAIT_FOR_ACTIVE_RUN";
        }
        return "UPLOAD_NEW_SOURCE";
    }

    private boolean isActive(LocalizationJobStatus status) {
        return status == LocalizationJobStatus.QUEUED
                || status == LocalizationJobStatus.PROCESSING
                || status == LocalizationJobStatus.RETRYING;
    }
}
