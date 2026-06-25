package com.linguaframe.media.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.media.domain.enums.MediaUploadValidationCode;
import com.linguaframe.media.domain.vo.MediaUploadValidationVo;
import com.linguaframe.media.service.impl.MediaUploadValidationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

class MediaUploadValidationServiceTests {

    private LinguaFrameProperties properties;
    private MediaUploadValidationService service;

    @BeforeEach
    void setUp() {
        properties = new LinguaFrameProperties();
        service = new MediaUploadValidationServiceImpl(properties);
    }

    @Test
    void acceptsSupportedVideoWithinConfiguredLimit() {
        MultipartFile file = new MockMultipartFile(
                "file",
                "C:\\tmp\\sample.mp4",
                "video/mp4",
                new byte[] {1, 2, 3}
        );

        MediaUploadValidationVo result = service.validate(file);

        assertThat(result.valid()).isTrue();
        assertThat(result.code()).isEqualTo(MediaUploadValidationCode.READY);
        assertThat(result.filename()).isEqualTo("sample.mp4");
        assertThat(result.contentType()).isEqualTo("video/mp4");
        assertThat(result.fileSizeBytes()).isEqualTo(3);
        assertThat(result.maxFileSizeBytes()).isEqualTo(100L * 1024L * 1024L);
        assertThat(result.maxDurationSeconds()).isEqualTo(120);
    }

    @Test
    void rejectsMissingFile() {
        MediaUploadValidationVo result = service.validate(null);

        assertThat(result.valid()).isFalse();
        assertThat(result.code()).isEqualTo(MediaUploadValidationCode.MISSING_FILE);
        assertThat(result.message()).isEqualTo("A video file is required.");
    }

    @Test
    void rejectsEmptyFile() {
        MultipartFile file = new MockMultipartFile("file", "empty.mp4", "video/mp4", new byte[0]);

        MediaUploadValidationVo result = service.validate(file);

        assertThat(result.valid()).isFalse();
        assertThat(result.code()).isEqualTo(MediaUploadValidationCode.EMPTY_FILE);
    }

    @Test
    void rejectsUnsupportedContentType() {
        MultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {1});

        MediaUploadValidationVo result = service.validate(file);

        assertThat(result.valid()).isFalse();
        assertThat(result.code()).isEqualTo(MediaUploadValidationCode.UNSUPPORTED_CONTENT_TYPE);
        assertThat(result.supportedContentTypes())
                .containsExactly("video/mp4", "video/quicktime", "video/webm", "video/x-matroska");
    }

    @Test
    void rejectsFileLargerThanConfiguredLimit() {
        properties.getMedia().setMaxFileSizeMb(1);
        MultipartFile file = new MockMultipartFile("file", "large.mp4", "video/mp4", new byte[(1024 * 1024) + 1]);

        MediaUploadValidationVo result = service.validate(file);

        assertThat(result.valid()).isFalse();
        assertThat(result.code()).isEqualTo(MediaUploadValidationCode.FILE_TOO_LARGE);
        assertThat(result.maxFileSizeBytes()).isEqualTo(1024L * 1024L);
    }
}
