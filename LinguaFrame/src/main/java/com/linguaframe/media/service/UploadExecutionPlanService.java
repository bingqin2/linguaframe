package com.linguaframe.media.service;

import com.linguaframe.media.domain.bo.UploadCostEstimateOptionsBo;
import com.linguaframe.media.domain.vo.UploadExecutionPlanVo;
import org.springframework.web.multipart.MultipartFile;

public interface UploadExecutionPlanService {

    UploadExecutionPlanVo plan(MultipartFile file, UploadCostEstimateOptionsBo options);
}
