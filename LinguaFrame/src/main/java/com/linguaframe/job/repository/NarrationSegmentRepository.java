package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.NarrationSegmentRecord;

import java.util.List;

public interface NarrationSegmentRepository {

    void replaceSegments(String jobId, List<NarrationSegmentRecord> segments);

    List<NarrationSegmentRecord> findByJobId(String jobId);

    void deleteByJobId(String jobId);
}
