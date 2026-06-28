package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.DeliveryManifestVo;

public interface DeliveryManifestService {

    DeliveryManifestVo buildManifest(String jobId);

    String buildMarkdownManifest(String jobId);
}
