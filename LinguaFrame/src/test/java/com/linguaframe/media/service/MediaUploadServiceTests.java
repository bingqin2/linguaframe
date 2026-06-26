package com.linguaframe.media.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.JobDispatchEventType;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.repository.JobDispatchEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.impl.JobDispatchOutboxServiceImpl;
import com.linguaframe.media.domain.bo.MediaDurationProbeCommand;
import com.linguaframe.media.domain.bo.MediaDurationProbeResult;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.domain.vo.MediaUploadVo;
import com.linguaframe.media.repository.VideoRepository;
import com.linguaframe.media.service.impl.MediaUploadServiceImpl;
import com.linguaframe.media.service.impl.MediaUploadValidationServiceImpl;
import com.linguaframe.storage.domain.bo.StoreObjectCommand;
import com.linguaframe.storage.domain.bo.StoredObjectBo;
import com.linguaframe.storage.service.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class MediaUploadServiceTests {

    @Autowired
    private LinguaFrameProperties properties;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private LocalizationJobRepository jobRepository;

    @Autowired
    private JobDispatchEventRepository dispatchEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsDurableVideoAndQueuedJob() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper)
        );
        MockMultipartFile file = new MockMultipartFile("file", "C:\\tmp\\demo.mp4", "video/mp4", new byte[] {1, 2, 3});

        MediaUploadVo result = service.createUpload(file, "zh-CN");

        assertThat(result.videoId()).isNotBlank();
        assertThat(result.jobId()).isNotBlank();
        assertThat(result.filename()).isEqualTo("demo.mp4");
        assertThat(result.contentType()).isEqualTo("video/mp4");
        assertThat(result.fileSizeBytes()).isEqualTo(3);
        assertThat(result.durationSeconds()).isEqualTo(42);
        assertThat(result.status()).isEqualTo(MediaUploadStatus.UPLOADED);
        assertThat(result.jobStatus()).isEqualTo(LocalizationJobStatus.QUEUED);
        assertThat(result.sourceObjectKey()).isEqualTo("source-videos/" + result.videoId() + "/demo.mp4");
        assertThat(storageService.lastCommand.objectKey()).isEqualTo(result.sourceObjectKey());
        assertThat(videoRepository.findById(result.videoId()))
                .isPresent()
                .get()
                .satisfies(video -> assertThat(video.durationSeconds()).isEqualTo(42));
        assertThat(jobRepository.findById(result.jobId())).isPresent();
        assertThat(dispatchEventRepository.findLatestByJobId(result.jobId()))
                .isPresent()
                .get()
                .satisfies(event -> {
                    assertThat(event.status()).isEqualTo(JobDispatchEventStatus.PENDING);
                    assertThat(event.eventType()).isEqualTo(JobDispatchEventType.LOCALIZATION_JOB_QUEUED);
                    assertThat(event.payloadJson())
                            .contains(result.jobId())
                            .contains(result.videoId())
                            .contains(result.sourceObjectKey());
                });
    }

    @Test
    void rejectsInvalidUploadBeforeStorage() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper)
        );
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {1});

        assertThatThrownBy(() -> service.createUpload(file, "zh-CN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNSUPPORTED_CONTENT_TYPE");
        assertThat(storageService.lastCommand).isNull();
    }

    @Test
    void rejectsTooLongVideoBeforeStorage() {
        properties.getMedia().setMaxDurationSeconds(300);
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(300.001)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper)
        );
        MockMultipartFile file = new MockMultipartFile("file", "long.mp4", "video/mp4", new byte[] {1});

        assertThatThrownBy(() -> service.createUpload(file, "zh-CN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DURATION_TOO_LONG");
        assertThat(storageService.lastCommand).isNull();
    }

    @Test
    void returnsSafeStorageFailure() {
        RecordingObjectStorageService storageService = new RecordingObjectStorageService(true);
        MediaUploadService service = new MediaUploadServiceImpl(
                new MediaUploadValidationServiceImpl(properties, new RecordingMediaDurationProbeService(42.0)),
                storageService,
                videoRepository,
                jobRepository,
                new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper)
        );
        MockMultipartFile file = new MockMultipartFile("file", "demo.mp4", "video/mp4", new byte[] {1});

        assertThatThrownBy(() -> service.createUpload(file, "zh-CN"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to store source video.");
    }

    private static class RecordingObjectStorageService implements ObjectStorageService {

        private final boolean fail;
        private StoreObjectCommand lastCommand;

        private RecordingObjectStorageService(boolean fail) {
            this.fail = fail;
        }

        @Override
        public StoredObjectBo store(StoreObjectCommand command) {
            this.lastCommand = command;
            if (fail) {
                throw new IllegalStateException("raw storage password stack trace");
            }
            return new StoredObjectBo("linguaframe-artifacts", command.objectKey(), command.sizeBytes());
        }

        @Override
        public InputStream open(String objectKey) {
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    private static class RecordingMediaDurationProbeService implements MediaDurationProbeService {

        private final double durationSeconds;

        private RecordingMediaDurationProbeService(double durationSeconds) {
            this.durationSeconds = durationSeconds;
        }

        @Override
        public MediaDurationProbeResult probeDuration(MediaDurationProbeCommand command) {
            return new MediaDurationProbeResult(durationSeconds);
        }
    }
}
