package com.linguaframe.media.controller;

import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.repository.VideoRepository;
import com.linguaframe.storage.service.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "linguaframe.auth.enabled=true",
        "linguaframe.auth.owner-username=owner",
        "linguaframe.auth.owner-password=owner-password",
        "linguaframe.auth.jwt-secret=0123456789abcdef0123456789abcdef",
        "linguaframe.demo.owner-id=owner-alpha",
        "linguaframe.demo.access-token=demo-token"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticatedOwnerMediaAccessTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoBean
    private ObjectStorageService objectStorageService;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM model_call_records").update();
        jdbcClient.sql("DELETE FROM job_artifacts").update();
        jdbcClient.sql("DELETE FROM job_timeline_events").update();
        jdbcClient.sql("DELETE FROM job_dispatch_events").update();
        jdbcClient.sql("DELETE FROM localization_jobs").update();
        jdbcClient.sql("DELETE FROM videos").update();
    }

    @Test
    void bearerAuthReadsCurrentOwnerMediaMetadata() throws Exception {
        createOwnedVideo("video-owner-alpha", "owner-alpha", "alpha.mp4");

        mockMvc.perform(get("/api/media/uploads/{videoId}", "video-owner-alpha")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoId").value("video-owner-alpha"))
                .andExpect(jsonPath("$.filename").value("alpha.mp4"));
    }

    @Test
    void bearerAuthReturnsSafeNotFoundForOtherOwnerMediaMetadata() throws Exception {
        createOwnedVideo("video-owner-beta", "owner-beta", "beta.mp4");

        mockMvc.perform(get("/api/media/uploads/{videoId}", "video-owner-beta")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(content().string(not(containsString("owner-beta"))))
                .andExpect(content().string(not(containsString("beta.mp4"))));
    }

    @Test
    void bearerAuthDownloadsCurrentOwnerSourceMedia() throws Exception {
        createOwnedVideo("video-owner-alpha-source", "owner-alpha", "alpha-source.mp4");
        byte[] sourceBytes = new byte[] {1, 2, 3, 4};
        when(objectStorageService.open("source-videos/video-owner-alpha-source/alpha-source.mp4"))
                .thenReturn(new ByteArrayInputStream(sourceBytes));

        mockMvc.perform(get("/api/media/uploads/{videoId}/source/download", "video-owner-alpha-source")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginToken()))
                .andExpect(status().isOk())
                .andExpect(content().bytes(sourceBytes));
    }

    @Test
    void bearerAuthReturnsSafeNotFoundForOtherOwnerSourceMedia() throws Exception {
        createOwnedVideo("video-owner-beta-source", "owner-beta", "beta-source.mp4");

        mockMvc.perform(get("/api/media/uploads/{videoId}/source/download", "video-owner-beta-source")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(content().string(not(containsString("owner-beta"))))
                .andExpect(content().string(not(containsString("beta-source.mp4"))));
    }

    private void createOwnedVideo(String videoId, String ownerId, String filename) {
        videoRepository.save(new VideoRecord(
                videoId,
                ownerId,
                filename,
                "video/mp4",
                4L,
                null,
                "source-videos/" + videoId + "/" + filename,
                MediaUploadStatus.UPLOADED,
                Instant.parse("2026-06-27T02:00:00Z")
        ));
    }

    private String loginToken() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"owner\",\"password\":\"owner-password\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        int start = body.indexOf("\"token\":\"") + "\"token\":\"".length();
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }
}
