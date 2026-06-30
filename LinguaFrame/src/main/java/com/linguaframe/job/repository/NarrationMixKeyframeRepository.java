package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.NarrationMixKeyframeRecord;

import java.util.List;

public interface NarrationMixKeyframeRepository {

    void replaceKeyframes(String jobId, List<NarrationMixKeyframeRecord> keyframes);

    List<NarrationMixKeyframeRecord> findByJobId(String jobId);

    void deleteByJobId(String jobId);
}
