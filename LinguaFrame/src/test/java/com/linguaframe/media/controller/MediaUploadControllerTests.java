package com.linguaframe.media.controller;

import com.linguaframe.media.domain.bo.MediaDurationProbeResult;
import com.linguaframe.media.service.MediaDurationProbeService;
import com.linguaframe.storage.domain.bo.StoreObjectCommand;
import com.linguaframe.storage.domain.bo.StoredObjectBo;
import com.linguaframe.storage.service.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaUploadControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ObjectStorageService objectStorageService;

    @MockitoBean
    private MediaDurationProbeService mediaDurationProbeService;

    @Test
    void validatesSupportedMultipartVideo() throws Exception {
        when(mediaDurationProbeService.probeDuration(any())).thenReturn(new MediaDurationProbeResult(42.0));
        MockMultipartFile file = new MockMultipartFile("file", "sample.mp4", "video/mp4", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/media/uploads/validate").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.code").value("READY"))
                .andExpect(jsonPath("$.filename").value("sample.mp4"))
                .andExpect(jsonPath("$.contentType").value("video/mp4"))
                .andExpect(jsonPath("$.fileSizeBytes").value(3))
                .andExpect(jsonPath("$.maxFileSizeBytes").value(104857600))
                .andExpect(jsonPath("$.durationSeconds").value(42))
                .andExpect(jsonPath("$.maxDurationSeconds").value(300));
    }

    @Test
    void returnsBadRequestForInvalidValidationFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {1});

        mockMvc.perform(multipart("/api/media/uploads/validate").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CONTENT_TYPE"));
    }

    @Test
    void returnsBadRequestForTooLongValidationFile() throws Exception {
        when(mediaDurationProbeService.probeDuration(any())).thenReturn(new MediaDurationProbeResult(300.001));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "long.mp4",
                "video/mp4",
                new byte[] {1, 2, 3, 4}
        );

        mockMvc.perform(multipart("/api/media/uploads/validate").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.code").value("DURATION_TOO_LONG"));
    }

    @Test
    void createsUploadAndQueuedJob() throws Exception {
        when(mediaDurationProbeService.probeDuration(any())).thenReturn(new MediaDurationProbeResult(42.0));
        when(objectStorageService.store(any(StoreObjectCommand.class))).thenAnswer(invocation -> {
            StoreObjectCommand command = invocation.getArgument(0);
            return new StoredObjectBo("linguaframe-artifacts", command.objectKey(), command.sizeBytes());
        });
        MockMultipartFile file = new MockMultipartFile("file", "sample.mp4", "video/mp4", new byte[] {1, 2, 3});

        String response = mockMvc.perform(multipart("/api/media/uploads")
                        .file(file)
                        .param("targetLanguage", "zh-CN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.videoId", not(isEmptyOrNullString())))
                .andExpect(jsonPath("$.jobId", not(isEmptyOrNullString())))
                .andExpect(jsonPath("$.filename").value("sample.mp4"))
                .andExpect(jsonPath("$.durationSeconds").value(42))
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.jobStatus").value("QUEUED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String videoId = response.replaceAll(".*\"videoId\":\"([^\"]+)\".*", "$1");
        mockMvc.perform(get("/api/media/uploads/{videoId}", videoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoId").value(videoId))
                .andExpect(jsonPath("$.durationSeconds").value(42))
                .andExpect(jsonPath("$.status").value("UPLOADED"));
    }

    @Test
    void returnsBadRequestForInvalidUploadFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {1});

        mockMvc.perform(multipart("/api/media/uploads")
                        .file(file)
                        .param("targetLanguage", "zh-CN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UPLOAD_VALIDATION_FAILED"));
    }

    @Test
    void returnsNotFoundForUnknownVideo() throws Exception {
        mockMvc.perform(get("/api/media/uploads/{videoId}", "missing-video"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
