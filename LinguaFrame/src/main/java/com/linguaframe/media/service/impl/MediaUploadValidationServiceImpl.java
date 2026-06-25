package com.linguaframe.media.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.media.domain.enums.MediaUploadValidationCode;
import com.linguaframe.media.domain.vo.MediaUploadValidationVo;
import com.linguaframe.media.service.MediaUploadValidationService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;

@Service
public class MediaUploadValidationServiceImpl implements MediaUploadValidationService {

    private static final List<String> SUPPORTED_CONTENT_TYPES = List.of(
            "video/mp4",
            "video/quicktime",
            "video/webm",
            "video/x-matroska"
    );

    private final LinguaFrameProperties properties;

    public MediaUploadValidationServiceImpl(LinguaFrameProperties properties) {
        this.properties = properties;
    }

    @Override
    public MediaUploadValidationVo validate(MultipartFile file) {
        if (file == null) {
            return invalid(MediaUploadValidationCode.MISSING_FILE, "A video file is required.", null, null, 0);
        }

        String filename = sanitizeFilename(file.getOriginalFilename());
        String contentType = normalizeContentType(file.getContentType());
        long fileSizeBytes = file.getSize();

        if (file.isEmpty()) {
            return invalid(
                    MediaUploadValidationCode.EMPTY_FILE,
                    "The uploaded video file is empty.",
                    filename,
                    contentType,
                    fileSizeBytes
            );
        }

        if (!SUPPORTED_CONTENT_TYPES.contains(contentType)) {
            return invalid(
                    MediaUploadValidationCode.UNSUPPORTED_CONTENT_TYPE,
                    "Supported video types are video/mp4, video/quicktime, video/webm, and video/x-matroska.",
                    filename,
                    contentType,
                    fileSizeBytes
            );
        }

        if (fileSizeBytes > maxFileSizeBytes()) {
            return invalid(
                    MediaUploadValidationCode.FILE_TOO_LARGE,
                    "The uploaded video exceeds the " + properties.getMedia().getMaxFileSizeMb() + " MB limit.",
                    filename,
                    contentType,
                    fileSizeBytes
            );
        }

        return new MediaUploadValidationVo(
                true,
                MediaUploadValidationCode.READY,
                "File is ready for upload.",
                filename,
                contentType,
                fileSizeBytes,
                maxFileSizeBytes(),
                properties.getMedia().getMaxDurationSeconds(),
                SUPPORTED_CONTENT_TYPES
        );
    }

    private MediaUploadValidationVo invalid(
            MediaUploadValidationCode code,
            String message,
            String filename,
            String contentType,
            long fileSizeBytes
    ) {
        return new MediaUploadValidationVo(
                false,
                code,
                message,
                filename,
                contentType,
                fileSizeBytes,
                maxFileSizeBytes(),
                properties.getMedia().getMaxDurationSeconds(),
                SUPPORTED_CONTENT_TYPES
        );
    }

    private long maxFileSizeBytes() {
        return properties.getMedia().getMaxFileSizeMb() * 1024L * 1024L;
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "";
        }
        return contentType.toLowerCase(Locale.ROOT);
    }

    private String sanitizeFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "";
        }
        String normalized = originalFilename.replace('\\', '/');
        String filename = StringUtils.getFilename(normalized);
        return filename == null ? "" : filename;
    }
}
