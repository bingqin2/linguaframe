package com.linguaframe.media.controller;

import com.linguaframe.common.ratelimit.RateLimitDecision;
import com.linguaframe.common.ratelimit.UploadRateLimitService;
import com.linguaframe.media.service.MediaDurationProbeService;
import com.linguaframe.storage.service.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "linguaframe.rate-limit.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaUploadRateLimitControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ObjectStorageService objectStorageService;

    @MockitoBean
    private MediaDurationProbeService mediaDurationProbeService;

    @MockitoBean
    private UploadRateLimitService uploadRateLimitService;

    @Test
    void rejectsUploadWhenRateLimitIsExceeded() throws Exception {
        when(uploadRateLimitService.checkUploadAllowed(any()))
                .thenReturn(RateLimitDecision.denied(20, Instant.parse("2026-06-27T03:01:00Z"), 55));
        MockMultipartFile file = new MockMultipartFile("file", "sample.mp4", "video/mp4", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/media/uploads")
                        .file(file)
                        .param("targetLanguage", "zh-CN"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(header().string("X-RateLimit-Limit", "20"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().string("X-RateLimit-Reset", "2026-06-27T03:01:00Z"))
                .andExpect(header().string("Retry-After", "55"));
    }

    @Test
    void rejectsUploadValidationWhenRateLimitIsExceeded() throws Exception {
        when(uploadRateLimitService.checkUploadAllowed(any()))
                .thenReturn(RateLimitDecision.denied(20, Instant.parse("2026-06-27T03:01:00Z"), 55));
        MockMultipartFile file = new MockMultipartFile("file", "sample.mp4", "video/mp4", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/media/uploads/validate").file(file))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(header().string("Retry-After", "55"));
    }
}
