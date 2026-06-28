package com.linguaframe.media.service;

import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.media.domain.vo.MediaUploadVo;
import com.linguaframe.media.domain.vo.MediaUploadDetailVo;
import org.springframework.web.multipart.MultipartFile;

public interface MediaUploadService {

    default MediaUploadVo createUpload(MultipartFile file, String targetLanguage) {
        return createUpload(file, targetLanguage, null);
    }

    MediaUploadVo createUpload(MultipartFile file, String targetLanguage, String ttsVoice);

    MediaUploadDetailVo getUpload(String videoId);

    StoredObjectResourceBo openSourceMedia(String videoId);
}
