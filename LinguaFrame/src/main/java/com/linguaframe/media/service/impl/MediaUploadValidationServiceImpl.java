package com.linguaframe.media.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.media.domain.bo.MediaDurationProbeCommand;
import com.linguaframe.media.domain.bo.MediaDurationProbeResult;
import com.linguaframe.media.domain.enums.MediaUploadValidationCode;
import com.linguaframe.media.domain.exception.UnreadableMediaException;
import com.linguaframe.media.domain.vo.MediaUploadValidationVo;
import com.linguaframe.media.service.MediaDurationProbeService;
import com.linguaframe.media.service.MediaUploadValidationService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    private final MediaDurationProbeService durationProbeService;

    public MediaUploadValidationServiceImpl(
            LinguaFrameProperties properties,
            MediaDurationProbeService durationProbeService
    ) {
        this.properties = properties;
        this.durationProbeService = durationProbeService;
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

        int durationSeconds;
        try {
            durationSeconds = probeDurationSeconds(file, filename);
        } catch (UnreadableMediaException ex) {
            return invalid(
                    MediaUploadValidationCode.UNREADABLE_MEDIA,
                    ex.getMessage(),
                    filename,
                    contentType,
                    fileSizeBytes
            );
        }
        if (durationSeconds > properties.getMedia().getMaxDurationSeconds()) {
            return invalid(
                    MediaUploadValidationCode.DURATION_TOO_LONG,
                    "The uploaded video exceeds the " + properties.getMedia().getMaxDurationSeconds()
                            + " second duration limit.",
                    filename,
                    contentType,
                    fileSizeBytes,
                    durationSeconds
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
                durationSeconds,
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
        return invalid(code, message, filename, contentType, fileSizeBytes, null);
    }

    private MediaUploadValidationVo invalid(
            MediaUploadValidationCode code,
            String message,
            String filename,
            String contentType,
            long fileSizeBytes,
            Integer durationSeconds
    ) {
        return new MediaUploadValidationVo(
                false,
                code,
                message,
                filename,
                contentType,
                fileSizeBytes,
                maxFileSizeBytes(),
                durationSeconds,
                properties.getMedia().getMaxDurationSeconds(),
                SUPPORTED_CONTENT_TYPES
        );
    }

    private int probeDurationSeconds(MultipartFile file, String filename) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("linguaframe-upload-", ".media");
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            MediaDurationProbeResult result = durationProbeService.probeDuration(
                    new MediaDurationProbeCommand(filename, tempFile)
            );
            return result.durationSecondsRoundedUp();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to inspect uploaded video duration.", ex);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Best-effort cleanup for temporary upload inspection files.
                }
            }
        }
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
