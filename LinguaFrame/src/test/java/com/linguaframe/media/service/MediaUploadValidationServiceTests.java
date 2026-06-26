package com.linguaframe.media.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.media.domain.bo.MediaDurationProbeCommand;
import com.linguaframe.media.domain.bo.MediaDurationProbeResult;
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
    private RecordingMediaDurationProbeService durationProbeService;
    private MediaUploadValidationService service;

    @BeforeEach
    void setUp() {
        properties = new LinguaFrameProperties();
        durationProbeService = new RecordingMediaDurationProbeService(42.0);
        service = new MediaUploadValidationServiceImpl(properties, durationProbeService);
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
        assertThat(result.durationSeconds()).isEqualTo(42);
        assertThat(result.maxDurationSeconds()).isEqualTo(300);
        assertThat(durationProbeService.probeCalls).isEqualTo(1);
    }

    @Test
    void rejectsMissingFile() {
        MediaUploadValidationVo result = service.validate(null);

        assertThat(result.valid()).isFalse();
        assertThat(result.code()).isEqualTo(MediaUploadValidationCode.MISSING_FILE);
        assertThat(result.message()).isEqualTo("A video file is required.");
        assertThat(durationProbeService.probeCalls).isZero();
    }

    @Test
    void rejectsEmptyFile() {
        MultipartFile file = new MockMultipartFile("file", "empty.mp4", "video/mp4", new byte[0]);

        MediaUploadValidationVo result = service.validate(file);

        assertThat(result.valid()).isFalse();
        assertThat(result.code()).isEqualTo(MediaUploadValidationCode.EMPTY_FILE);
        assertThat(durationProbeService.probeCalls).isZero();
    }

    @Test
    void rejectsUnsupportedContentType() {
        MultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {1});

        MediaUploadValidationVo result = service.validate(file);

        assertThat(result.valid()).isFalse();
        assertThat(result.code()).isEqualTo(MediaUploadValidationCode.UNSUPPORTED_CONTENT_TYPE);
        assertThat(result.supportedContentTypes())
                .containsExactly("video/mp4", "video/quicktime", "video/webm", "video/x-matroska");
        assertThat(durationProbeService.probeCalls).isZero();
    }

    @Test
    void rejectsFileLargerThanConfiguredLimit() {
        properties.getMedia().setMaxFileSizeMb(1);
        MultipartFile file = new MockMultipartFile("file", "large.mp4", "video/mp4", new byte[(1024 * 1024) + 1]);

        MediaUploadValidationVo result = service.validate(file);

        assertThat(result.valid()).isFalse();
        assertThat(result.code()).isEqualTo(MediaUploadValidationCode.FILE_TOO_LARGE);
        assertThat(result.maxFileSizeBytes()).isEqualTo(1024L * 1024L);
        assertThat(durationProbeService.probeCalls).isZero();
    }

    @Test
    void acceptsVideoAtConfiguredDurationLimit() {
        properties.getMedia().setMaxDurationSeconds(300);
        durationProbeService.durationSeconds = 300.0;
        MultipartFile file = new MockMultipartFile("file", "limit.mp4", "video/mp4", new byte[] {1});

        MediaUploadValidationVo result = service.validate(file);

        assertThat(result.valid()).isTrue();
        assertThat(result.code()).isEqualTo(MediaUploadValidationCode.READY);
        assertThat(result.durationSeconds()).isEqualTo(300);
        assertThat(result.maxDurationSeconds()).isEqualTo(300);
    }

    @Test
    void rejectsVideoOverConfiguredDurationLimit() {
        properties.getMedia().setMaxDurationSeconds(300);
        durationProbeService.durationSeconds = 300.001;
        MultipartFile file = new MockMultipartFile("file", "long.mp4", "video/mp4", new byte[] {1});

        MediaUploadValidationVo result = service.validate(file);

        assertThat(result.valid()).isFalse();
        assertThat(result.code()).isEqualTo(MediaUploadValidationCode.DURATION_TOO_LONG);
        assertThat(result.message()).isEqualTo("The uploaded video exceeds the 300 second duration limit.");
        assertThat(result.durationSeconds()).isEqualTo(301);
        assertThat(durationProbeService.probeCalls).isEqualTo(1);
    }

    private static class RecordingMediaDurationProbeService implements MediaDurationProbeService {

        private double durationSeconds;
        private int probeCalls;

        private RecordingMediaDurationProbeService(double durationSeconds) {
            this.durationSeconds = durationSeconds;
        }

        @Override
        public MediaDurationProbeResult probeDuration(MediaDurationProbeCommand command) {
            probeCalls++;
            return new MediaDurationProbeResult(durationSeconds);
        }
    }
}
