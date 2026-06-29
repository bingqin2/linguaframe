package com.linguaframe.media.service;

import com.linguaframe.media.domain.bo.UploadCostEstimateOptionsBo;
import com.linguaframe.media.domain.vo.UploadCostEstimateVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseVo;
import org.springframework.web.multipart.MultipartFile;

public interface UploadSourceReuseService {

    UploadSourceReuseVo evaluate(MultipartFile file, UploadCostEstimateVo estimate, UploadCostEstimateOptionsBo options);
}
