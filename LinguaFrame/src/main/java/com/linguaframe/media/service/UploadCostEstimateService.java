package com.linguaframe.media.service;

import com.linguaframe.media.domain.bo.UploadCostEstimateOptionsBo;
import com.linguaframe.media.domain.vo.UploadCostEstimateVo;
import org.springframework.web.multipart.MultipartFile;

public interface UploadCostEstimateService {

    UploadCostEstimateVo estimate(MultipartFile file, UploadCostEstimateOptionsBo options);
}
