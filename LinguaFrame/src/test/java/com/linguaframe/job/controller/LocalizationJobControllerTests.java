package com.linguaframe.job.controller;

import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LocalizationJobControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private LocalizationJobRepository jobRepository;

    @Test
    void returnsQueuedLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-25T15:00:00Z");
        videoRepository.save(new VideoRecord(
                "job-controller-video",
                "sample.mp4",
                "video/mp4",
                123L,
                "source-videos/job-controller-video/sample.mp4",
                MediaUploadStatus.UPLOADED,
                createdAt
        ));
        jobRepository.save(new LocalizationJobRecord(
                "job-controller-job",
                "job-controller-video",
                "zh-CN",
                LocalizationJobStatus.QUEUED,
                createdAt
        ));

        mockMvc.perform(get("/api/jobs/{jobId}", "job-controller-job"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-job"))
                .andExpect(jsonPath("$.videoId").value("job-controller-video"))
                .andExpect(jsonPath("$.targetLanguage").value("zh-CN"))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void returnsNotFoundForUnknownJob() throws Exception {
        mockMvc.perform(get("/api/jobs/{jobId}", "missing-job"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
