package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.RetentionCleanupResultVo;

public interface RetentionCleanupService {

    RetentionCleanupResultVo previewCleanup();

    RetentionCleanupResultVo runCleanup();
}
