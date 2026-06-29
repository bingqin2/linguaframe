package com.linguaframe.media.service;

import com.linguaframe.media.domain.bo.UploadCostEstimateOptionsBo;
import com.linguaframe.media.domain.vo.UploadDecisionPackageVo;
import org.springframework.web.multipart.MultipartFile;

public interface UploadDecisionPackageService {

    UploadDecisionPackageVo build(MultipartFile file, UploadCostEstimateOptionsBo options);

    String renderMarkdown(UploadDecisionPackageVo value);
}
