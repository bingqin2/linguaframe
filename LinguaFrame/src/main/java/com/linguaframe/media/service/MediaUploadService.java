package com.linguaframe.media.service;

import com.linguaframe.media.domain.vo.MediaUploadVo;
import com.linguaframe.media.domain.vo.MediaUploadDetailVo;
import org.springframework.web.multipart.MultipartFile;

public interface MediaUploadService {

    MediaUploadVo createUpload(MultipartFile file, String targetLanguage);

    MediaUploadDetailVo getUpload(String videoId);
}
