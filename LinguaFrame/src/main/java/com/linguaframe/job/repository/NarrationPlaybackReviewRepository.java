package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.NarrationPlaybackReviewRecord;

import java.util.List;
import java.util.Optional;

public interface NarrationPlaybackReviewRepository {

    List<NarrationPlaybackReviewRecord> findByJobId(String jobId);

    Optional<NarrationPlaybackReviewRecord> findByJobIdAndSegmentIndex(String jobId, int segmentIndex);

    NarrationPlaybackReviewRecord upsert(NarrationPlaybackReviewRecord record);

    void deleteByJobId(String jobId);
}
