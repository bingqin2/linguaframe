package com.linguaframe.job.controller;

import com.linguaframe.job.domain.vo.RetentionCleanupResultVo;
import com.linguaframe.job.service.RetentionCleanupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RetentionCleanupControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RetentionCleanupService retentionCleanupService;

    @Test
    void previewsRetentionCleanup() throws Exception {
        when(retentionCleanupService.previewCleanup())
                .thenReturn(new RetentionCleanupResultVo(true, 2, 0, 0, 0, 3, 0));

        mockMvc.perform(get("/api/retention/cleanup/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.candidateJobCount").value(2))
                .andExpect(jsonPath("$.deletedJobCount").value(0))
                .andExpect(jsonPath("$.deletedVideoCount").value(0))
                .andExpect(jsonPath("$.deletedObjectCount").value(0))
                .andExpect(jsonPath("$.skippedObjectCount").value(3))
                .andExpect(jsonPath("$.failureCount").value(0));
    }

    @Test
    void runsRetentionCleanup() throws Exception {
        when(retentionCleanupService.runCleanup())
                .thenReturn(new RetentionCleanupResultVo(false, 1, 1, 1, 2, 0, 0));

        mockMvc.perform(post("/api/retention/cleanup/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(false))
                .andExpect(jsonPath("$.candidateJobCount").value(1))
                .andExpect(jsonPath("$.deletedJobCount").value(1))
                .andExpect(jsonPath("$.deletedVideoCount").value(1))
                .andExpect(jsonPath("$.deletedObjectCount").value(2))
                .andExpect(jsonPath("$.skippedObjectCount").value(0))
                .andExpect(jsonPath("$.failureCount").value(0));
    }
}
