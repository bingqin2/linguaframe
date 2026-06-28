package com.linguaframe.media.service;

import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.media.domain.vo.MediaUploadVo;
import com.linguaframe.media.domain.vo.MediaUploadDetailVo;
import org.springframework.web.multipart.MultipartFile;

public interface MediaUploadService {

    default MediaUploadVo createUpload(MultipartFile file, String targetLanguage) {
        return createUpload(file, targetLanguage, null);
    }

    default MediaUploadVo createUpload(MultipartFile file, String targetLanguage, String ttsVoice) {
        return createUpload(file, targetLanguage, ttsVoice, null);
    }

    default MediaUploadVo createUpload(
            MultipartFile file,
            String targetLanguage,
            String ttsVoice,
            String translationStyle
    ) {
        return createUpload(file, targetLanguage, ttsVoice, translationStyle, null);
    }

    MediaUploadVo createUpload(
            MultipartFile file,
            String targetLanguage,
            String ttsVoice,
            String translationStyle,
            String subtitleStylePreset
    );

    MediaUploadDetailVo getUpload(String videoId);

    StoredObjectResourceBo openSourceMedia(String videoId);
}
