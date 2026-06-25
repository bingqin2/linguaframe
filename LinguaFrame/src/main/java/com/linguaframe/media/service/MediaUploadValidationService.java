package com.linguaframe.media.service;

import com.linguaframe.media.domain.vo.MediaUploadValidationVo;
import org.springframework.web.multipart.MultipartFile;

public interface MediaUploadValidationService {

    MediaUploadValidationVo validate(MultipartFile file);
}
