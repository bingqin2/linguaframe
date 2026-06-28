package com.linguaframe.job.controller;

import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.repository.JobArtifactRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
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
class AuthenticatedOwnerJobAccessTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private LocalizationJobRepository jobRepository;

    @Autowired
    private JobArtifactRepository artifactRepository;

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
    void bearerAuthListsOnlyCurrentOwnerJobs() throws Exception {
        Instant base = Instant.parse("2026-06-27T01:05:00Z");
        createOwnedJob("video-owner-alpha-list", "job-owner-alpha-list", "owner-alpha", "alpha.mp4",
                LocalizationJobStatus.COMPLETED, base);
        createOwnedJob("video-owner-beta-list", "job-owner-beta-list", "owner-beta", "beta.mp4",
                LocalizationJobStatus.COMPLETED, base.plusSeconds(10));

        mockMvc.perform(get("/api/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.jobs[0].jobId").value("job-owner-alpha-list"))
                .andExpect(jsonPath("$.jobs[0].filename").value("alpha.mp4"))
                .andExpect(content().string(not(containsString("job-owner-beta-list"))))
                .andExpect(content().string(not(containsString("beta.mp4"))));
    }

    @Test
    void bearerAuthReturnsSafeNotFoundForOtherOwnerJobDetail() throws Exception {
        Instant base = Instant.parse("2026-06-27T01:06:00Z");
        createOwnedJob("video-owner-beta-detail", "job-owner-beta-detail", "owner-beta", "beta-detail.mp4",
                LocalizationJobStatus.COMPLETED, base);

        mockMvc.perform(get("/api/jobs/{jobId}", "job-owner-beta-detail")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(content().string(not(containsString("owner-beta"))))
                .andExpect(content().string(not(containsString("beta-detail.mp4"))));
    }

    @Test
    void demoTokenListsCurrentConfiguredOwnerJobs() throws Exception {
        Instant base = Instant.parse("2026-06-27T01:07:00Z");
        createOwnedJob("video-demo-owner-alpha-list", "job-demo-owner-alpha-list", "owner-alpha", "demo-alpha.mp4",
                LocalizationJobStatus.COMPLETED, base);
        createOwnedJob("video-demo-owner-beta-list", "job-demo-owner-beta-list", "owner-beta", "demo-beta.mp4",
                LocalizationJobStatus.COMPLETED, base.plusSeconds(10));

        mockMvc.perform(get("/api/jobs")
                        .header("X-LinguaFrame-Demo-Token", "demo-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.jobs[0].jobId").value("job-demo-owner-alpha-list"))
                .andExpect(content().string(not(containsString("job-demo-owner-beta-list"))));
    }

    @Test
    void bearerAuthReturnsSafeNotFoundForOtherOwnerArtifactList() throws Exception {
        Instant base = Instant.parse("2026-06-27T01:08:00Z");
        createOwnedJob("video-owner-beta-artifacts", "job-owner-beta-artifacts", "owner-beta", "beta-artifacts.mp4",
                LocalizationJobStatus.COMPLETED, base);
        createArtifact("artifact-owner-beta-list", "job-owner-beta-artifacts", "beta-artifacts.json");

        mockMvc.perform(get("/api/jobs/{jobId}/artifacts", "job-owner-beta-artifacts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(content().string(not(containsString("owner-beta"))))
                .andExpect(content().string(not(containsString("beta-artifacts.json"))));
    }

    @Test
    void bearerAuthReturnsSafeNotFoundForOtherOwnerArtifactDownload() throws Exception {
        Instant base = Instant.parse("2026-06-27T01:09:00Z");
        createOwnedJob("video-owner-beta-artifact-download", "job-owner-beta-artifact-download", "owner-beta",
                "beta-artifact-download.mp4", LocalizationJobStatus.COMPLETED, base);
        createArtifact("artifact-owner-beta-download", "job-owner-beta-artifact-download", "beta-download.json");
        when(objectStorageService.open("job-artifacts/job-owner-beta-artifact-download/artifact-owner-beta-download/beta-download.json"))
                .thenReturn(new ByteArrayInputStream("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/jobs/{jobId}/artifacts/{artifactId}/download",
                        "job-owner-beta-artifact-download",
                        "artifact-owner-beta-download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(content().string(not(containsString("owner-beta"))))
                .andExpect(content().string(not(containsString("beta-download.json"))));
    }

    @Test
    void bearerAuthReturnsSafeNotFoundForOtherOwnerArtifactArchive() throws Exception {
        Instant base = Instant.parse("2026-06-27T01:09:30Z");
        createOwnedJob("video-owner-beta-artifact-archive", "job-owner-beta-artifact-archive", "owner-beta",
                "beta-artifact-archive.mp4", LocalizationJobStatus.COMPLETED, base);
        createArtifact("artifact-owner-beta-archive", "job-owner-beta-artifact-archive", "beta-archive.json");

        mockMvc.perform(get("/api/jobs/{jobId}/artifacts/archive/download", "job-owner-beta-artifact-archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(content().string(not(containsString("owner-beta"))))
                .andExpect(content().string(not(containsString("beta-archive.json"))));
    }

    @Test
    void bearerAuthReturnsSafeNotFoundForOtherOwnerDiagnostics() throws Exception {
        Instant base = Instant.parse("2026-06-27T01:10:00Z");
        createOwnedJob("video-owner-beta-diagnostics", "job-owner-beta-diagnostics", "owner-beta", "beta-diagnostics.mp4",
                LocalizationJobStatus.COMPLETED, base);

        mockMvc.perform(get("/api/jobs/{jobId}/diagnostics/download", "job-owner-beta-diagnostics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(content().string(not(containsString("owner-beta"))))
                .andExpect(content().string(not(containsString("beta-diagnostics.mp4"))));
    }

    @Test
    void bearerAuthReturnsSafeNotFoundForOtherOwnerEvidenceMarkdown() throws Exception {
        Instant base = Instant.parse("2026-06-27T01:10:30Z");
        createOwnedJob("video-owner-beta-evidence", "job-owner-beta-evidence", "owner-beta", "beta-evidence.mp4",
                LocalizationJobStatus.COMPLETED, base);

        mockMvc.perform(get("/api/jobs/{jobId}/evidence/markdown/download", "job-owner-beta-evidence")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(content().string(not(containsString("owner-beta"))))
                .andExpect(content().string(not(containsString("beta-evidence.mp4"))));
    }

    private void createOwnedJob(
            String videoId,
            String jobId,
            String ownerId,
            String filename,
            LocalizationJobStatus status,
            Instant createdAt
    ) {
        videoRepository.save(new VideoRecord(
                videoId,
                ownerId,
                filename,
                "video/mp4",
                123L,
                null,
                "source-videos/" + videoId + "/" + filename,
                MediaUploadStatus.UPLOADED,
                createdAt
        ));
        jobRepository.save(new LocalizationJobRecord(
                jobId,
                videoId,
                ownerId,
                "zh-CN",
                null,
                "NATURAL",
                "STANDARD",
                "[]",
                "",
                0,
                "OFF",
                null,
                status,
                createdAt
        ));
    }

    private void createArtifact(String artifactId, String jobId, String filename) {
        artifactRepository.save(new JobArtifactRecord(
                artifactId,
                jobId,
                JobArtifactType.WORKER_SUMMARY,
                "job-artifacts/" + jobId + "/" + artifactId + "/" + filename,
                filename,
                "application/json",
                2L,
                artifactId + "-hash",
                false,
                null,
                Instant.parse("2026-06-27T01:11:00Z")
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
