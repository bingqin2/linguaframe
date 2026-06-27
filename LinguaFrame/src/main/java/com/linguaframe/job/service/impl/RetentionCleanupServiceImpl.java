package com.linguaframe.job.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.RetentionCleanupResultVo;
import com.linguaframe.job.domain.vo.RetentionJobCandidateVo;
import com.linguaframe.job.repository.JobArtifactRepository;
import com.linguaframe.job.repository.JobDispatchEventRepository;
import com.linguaframe.job.repository.JobTimelineEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.RetentionCleanupService;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.repository.VideoRepository;
import com.linguaframe.storage.service.ObjectStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;

@Service
public class RetentionCleanupServiceImpl implements RetentionCleanupService {

    private final LinguaFrameProperties.Retention retention;
    private final LocalizationJobRepository jobRepository;
    private final JobArtifactRepository artifactRepository;
    private final JobDispatchEventRepository dispatchEventRepository;
    private final JobTimelineEventRepository timelineEventRepository;
    private final VideoRepository videoRepository;
    private final ObjectStorageService objectStorageService;
    private final Clock clock;

    @Autowired
    public RetentionCleanupServiceImpl(
            LinguaFrameProperties properties,
            LocalizationJobRepository jobRepository,
            JobArtifactRepository artifactRepository,
            JobDispatchEventRepository dispatchEventRepository,
            JobTimelineEventRepository timelineEventRepository,
            VideoRepository videoRepository,
            ObjectStorageService objectStorageService
    ) {
        this(
                properties,
                jobRepository,
                artifactRepository,
                dispatchEventRepository,
                timelineEventRepository,
                videoRepository,
                objectStorageService,
                Clock.systemUTC()
        );
    }

    public RetentionCleanupServiceImpl(
            LinguaFrameProperties properties,
            LocalizationJobRepository jobRepository,
            JobArtifactRepository artifactRepository,
            JobDispatchEventRepository dispatchEventRepository,
            JobTimelineEventRepository timelineEventRepository,
            VideoRepository videoRepository,
            ObjectStorageService objectStorageService,
            Clock clock
    ) {
        this.retention = properties.getRetention();
        this.jobRepository = jobRepository;
        this.artifactRepository = artifactRepository;
        this.dispatchEventRepository = dispatchEventRepository;
        this.timelineEventRepository = timelineEventRepository;
        this.videoRepository = videoRepository;
        this.objectStorageService = objectStorageService;
        this.clock = clock;
    }

    @Override
    public RetentionCleanupResultVo previewCleanup() {
        return cleanup(true);
    }

    @Override
    public RetentionCleanupResultVo runCleanup() {
        if (!retention.isEnabled()) {
            return RetentionCleanupResultVo.emptyDryRun();
        }
        return cleanup(retention.isDryRun());
    }

    private RetentionCleanupResultVo cleanup(boolean dryRun) {
        List<RetentionJobCandidateVo> candidates = candidates();
        int deletedJobs = 0;
        int deletedVideos = 0;
        int deletedObjects = 0;
        int skippedObjects = 0;
        int failures = 0;

        for (RetentionJobCandidateVo candidate : candidates) {
            List<JobArtifactRecord> artifacts = artifactRepository.findByJobId(candidate.jobId());
            VideoRecord video = videoRepository.findById(candidate.videoId()).orElse(null);
            boolean deleteSourceVideo = video != null && jobRepository.countByVideoId(candidate.videoId()) == 1;
            int objectCount = artifacts.size() + (deleteSourceVideo ? 1 : 0);

            if (dryRun) {
                skippedObjects += objectCount;
                continue;
            }

            DeleteObjectsResult deleteObjectsResult = deleteObjects(video, deleteSourceVideo, artifacts);
            deletedObjects += deleteObjectsResult.deletedObjects();
            if (deleteObjectsResult.failed()) {
                failures++;
                continue;
            }

            dispatchEventRepository.deleteByJobId(candidate.jobId());
            timelineEventRepository.deleteByJobId(candidate.jobId());
            jobRepository.deleteById(candidate.jobId());
            deletedJobs++;

            if (video != null && jobRepository.countByVideoId(candidate.videoId()) == 0) {
                videoRepository.deleteById(candidate.videoId());
                deletedVideos++;
            }
        }

        return new RetentionCleanupResultVo(
                dryRun,
                candidates.size(),
                deletedJobs,
                deletedVideos,
                deletedObjects,
                skippedObjects,
                failures
        );
    }

    private List<RetentionJobCandidateVo> candidates() {
        Instant now = Instant.now(clock);
        return EnumSet.of(
                        LocalizationJobStatus.COMPLETED,
                        LocalizationJobStatus.FAILED,
                        LocalizationJobStatus.CANCELLED
                )
                .stream()
                .flatMap(status -> jobRepository.findRetentionCandidates(
                                EnumSet.of(status),
                                cutoffFor(status, now),
                                retention.getCleanupBatchSize()
                        )
                        .stream())
                .sorted((left, right) -> {
                    int byUpdatedAt = left.updatedAt().compareTo(right.updatedAt());
                    return byUpdatedAt != 0 ? byUpdatedAt : left.jobId().compareTo(right.jobId());
                })
                .limit(retention.getCleanupBatchSize())
                .toList();
    }

    private Instant cutoffFor(LocalizationJobStatus status, Instant now) {
        int ttlDays = switch (status) {
            case COMPLETED -> retention.getCompletedJobTtlDays();
            case FAILED -> retention.getFailedJobTtlDays();
            case CANCELLED -> retention.getCancelledJobTtlDays();
            default -> throw new IllegalArgumentException("Status is not terminal for retention: " + status);
        };
        return now.minus(ttlDays, ChronoUnit.DAYS);
    }

    private DeleteObjectsResult deleteObjects(
            VideoRecord video,
            boolean deleteSourceVideo,
            List<JobArtifactRecord> artifacts
    ) {
        int deletedObjects = 0;
        if (video != null && deleteSourceVideo) {
            try {
                objectStorageService.delete(video.sourceObjectKey());
                deletedObjects++;
            } catch (RuntimeException ex) {
                return new DeleteObjectsResult(deletedObjects, true);
            }
        }

        for (JobArtifactRecord artifact : artifacts) {
            try {
                objectStorageService.delete(artifact.objectKey());
                deletedObjects++;
            } catch (RuntimeException ex) {
                return new DeleteObjectsResult(deletedObjects, true);
            }
        }
        return new DeleteObjectsResult(deletedObjects, false);
    }

    private record DeleteObjectsResult(int deletedObjects, boolean failed) {
    }
}
