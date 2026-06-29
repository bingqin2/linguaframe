package com.linguaframe.operator.controller;

import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.entity.ModelCallRecord;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.domain.enums.ModelCallStatus;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.repository.JobTimelineEventRepository;
import com.linguaframe.job.repository.ModelCallRepository;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OperatorDashboardControllerTests {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class OpenDemoMode {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private VideoRepository videoRepository;

        @Autowired
        private LocalizationJobRepository jobRepository;

        @Autowired
        private JobTimelineEventRepository timelineEventRepository;

        @Autowired
        private ModelCallRepository modelCallRepository;

        @Autowired
        private JdbcClient jdbcClient;

        @BeforeEach
        void cleanDatabase() {
            jdbcClient.sql("DELETE FROM quality_evaluations").update();
            jdbcClient.sql("DELETE FROM model_call_records").update();
            jdbcClient.sql("DELETE FROM subtitle_segments").update();
            jdbcClient.sql("DELETE FROM transcript_segments").update();
            jdbcClient.sql("DELETE FROM job_artifacts").update();
            jdbcClient.sql("DELETE FROM job_timeline_events").update();
            jdbcClient.sql("DELETE FROM job_dispatch_events").update();
            jdbcClient.sql("DELETE FROM localization_jobs").update();
            jdbcClient.sql("DELETE FROM videos").update();
        }

        @Test
        void returnsOperatorDashboard() throws Exception {
            Instant createdAt = Instant.parse("2026-06-27T07:00:00Z");
            createJob("dashboard-controller-video", "dashboard-controller-job", "failed.mp4",
                    LocalizationJobStatus.FAILED, createdAt);
            jobRepository.markFailed(
                    "dashboard-controller-job",
                    LocalizationJobStage.WORKER_SMOKE,
                    "worker smoke failed",
                    createdAt.plusSeconds(1)
            );
            timelineEventRepository.save(new JobTimelineEventRecord(
                    "dashboard-controller-stage-timing",
                    "dashboard-controller-job",
                    LocalizationJobStage.WORKER_SMOKE,
                    JobTimelineEventStatus.FAILED,
                    "WORKER_SMOKE failed.",
                    1500L,
                    "worker smoke failed",
                    createdAt.plusSeconds(1)
            ));

            mockMvc.perform(get("/api/operator/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCounts").isArray())
                    .andExpect(jsonPath("$.statusCounts[?(@.status == 'FAILED')].count").value(1))
                    .andExpect(jsonPath("$.recentFailures[0].jobId").value("dashboard-controller-job"))
                    .andExpect(jsonPath("$.recentFailures[0].filename").value("failed.mp4"))
                    .andExpect(jsonPath("$.recentFailures[0].failureStage").value("WORKER_SMOKE"))
                    .andExpect(jsonPath("$.modelCalls.modelCallCount").value(0))
                    .andExpect(jsonPath("$.modelCalls.failedModelCallCount").value(0))
                    .andExpect(jsonPath("$.modelCalls.estimatedCostUsd").value(0))
                    .andExpect(jsonPath("$.cache.artifactCacheHitCount").value(0))
                    .andExpect(jsonPath("$.cache.providerCacheHitCount").value(0))
                    .andExpect(jsonPath("$.stageTimings[0].stage").value("WORKER_SMOKE"))
                    .andExpect(jsonPath("$.stageTimings[0].failedEventCount").value(1))
                    .andExpect(jsonPath("$.stageTimings[0].maxDurationMs").value(1500));
        }

        @Test
        void returnsPrivateDemoOperationsReadiness() throws Exception {
            Instant createdAt = Instant.parse("2026-06-27T07:00:00Z");
            createJob("operations-controller-video", "operations-controller-job", "operations.mp4",
                    LocalizationJobStatus.COMPLETED, createdAt);

            mockMvc.perform(get("/api/operator/private-demo/operations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.overallStatus").exists())
                    .andExpect(jsonPath("$.readyCount").isNumber())
                    .andExpect(jsonPath("$.attentionCount").isNumber())
                    .andExpect(jsonPath("$.blockedCount").isNumber())
                    .andExpect(jsonPath("$.sections[?(@.title == 'Access gate')]").exists())
                    .andExpect(jsonPath("$.sections[?(@.title == 'Live dependencies')]").exists())
                    .andExpect(jsonPath("$.sections[?(@.title == 'Cost safety')]").exists())
                    .andExpect(jsonPath("$.sections[?(@.title == 'Storage and recovery')]").exists())
                    .andExpect(jsonPath("$.sections[?(@.title == 'Retention cleanup')]").exists())
                    .andExpect(jsonPath("$.sections[?(@.title == 'Demo evidence')]").exists())
                    .andExpect(jsonPath("$.commands[?(@.command == 'scripts/demo/private-demo-preflight.sh')]").exists())
                    .andExpect(jsonPath("$.commands[?(@.command == 'scripts/demo/private-demo-backup.sh --dry-run')]").exists())
                    .andExpect(jsonPath("$.documentationLinks[?(@.path == 'docs/deployment/private-demo.md')]").exists());
        }

        @Test
        void returnsPrivateDemoLaunchRehearsal() throws Exception {
            Instant createdAt = Instant.parse("2026-06-27T07:00:00Z");
            createJob("launch-controller-video", "launch-controller-job", "launch.mp4",
                    LocalizationJobStatus.COMPLETED, createdAt);

            mockMvc.perform(get("/api/operator/private-demo/launch-rehearsal"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.overallStatus").exists())
                    .andExpect(jsonPath("$.recommendedNextStepId").exists())
                    .andExpect(jsonPath("$.readyCount").isNumber())
                    .andExpect(jsonPath("$.attentionCount").isNumber())
                    .andExpect(jsonPath("$.blockedCount").isNumber())
                    .andExpect(jsonPath("$.steps[?(@.id == 'deploy-preflight')]").exists())
                    .andExpect(jsonPath("$.steps[?(@.id == 'private-preflight')]").exists())
                    .andExpect(jsonPath("$.steps[?(@.id == 'openai-preflight')]").exists())
                    .andExpect(jsonPath("$.steps[?(@.id == 'full-tears-demo')]").exists())
                    .andExpect(jsonPath("$.evidenceDownloads[?(@ == '/api/operator/private-demo/operations')]").exists())
                    .andExpect(jsonPath("$.evidenceDownloads[?(@ == '/api/jobs/{jobId}/demo-presenter-pack')]").exists())
                    .andExpect(jsonPath("$.rehearsalNotesMarkdown").value(org.hamcrest.Matchers.containsString(
                            "LinguaFrame Private Demo Launch Rehearsal"
                    )));
        }

        @Test
        void returnsPrivateDemoEvidenceGallery() throws Exception {
            Instant createdAt = Instant.parse("2026-06-27T07:00:00Z");
            createJob("gallery-controller-video", "gallery-controller-job", "gallery.mp4",
                    LocalizationJobStatus.COMPLETED, createdAt);

            mockMvc.perform(get("/api/operator/private-demo/evidence-gallery").param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.overallStatus").exists())
                    .andExpect(jsonPath("$.completedJobCount").value(1))
                    .andExpect(jsonPath("$.recommendedJobId").value("gallery-controller-job"))
                    .andExpect(jsonPath("$.jobs[0].jobId").value("gallery-controller-job"))
                    .andExpect(jsonPath("$.jobs[0].filename").value("gallery.mp4"))
                    .andExpect(jsonPath("$.jobs[0].downloads[?(@.href == '/api/jobs/gallery-controller-job/demo-run-package/download')]").exists())
                    .andExpect(jsonPath("$.jobs[0].downloads[?(@.href == '/api/jobs/gallery-controller-job/ai-audit-package/download')]").exists())
                    .andExpect(jsonPath("$.galleryNotesMarkdown").value(org.hamcrest.Matchers.containsString(
                            "LinguaFrame Private Demo Evidence Gallery"
                    )));
        }

        @Test
        void returnsPrivateDemoRunArchive() throws Exception {
            Instant createdAt = Instant.parse("2026-06-27T07:00:00Z");
            createJob("archive-controller-video", "archive-controller-job", "archive.mp4",
                    LocalizationJobStatus.COMPLETED, createdAt);

            mockMvc.perform(get("/api/operator/private-demo/run-archive"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.overallStatus").exists())
                    .andExpect(jsonPath("$.recommendedJobId").value("archive-controller-job"))
                    .andExpect(jsonPath("$.recommendedVideoId").value("archive-controller-video"))
                    .andExpect(jsonPath("$.operationsOverallStatus").exists())
                    .andExpect(jsonPath("$.launchOverallStatus").exists())
                    .andExpect(jsonPath("$.galleryCompletedJobCount").value(1))
                    .andExpect(jsonPath("$.candidates[0].jobId").value("archive-controller-job"))
                    .andExpect(jsonPath("$.archiveLinks[?(@.href == '/api/operator/private-demo/operations')]").exists())
                    .andExpect(jsonPath("$.archiveLinks[?(@.href == '/api/operator/private-demo/evidence-gallery')]").exists())
                    .andExpect(jsonPath("$.archiveLinks[?(@.href == '/api/jobs/archive-controller-job/demo-run-package/download')]").exists())
                    .andExpect(jsonPath("$.archiveNotesMarkdown").value(org.hamcrest.Matchers.containsString(
                            "LinguaFrame Private Demo Run Archive"
                    )));
        }

        @Test
        void returnsDemoSampleMediaCatalog() throws Exception {
            mockMvc.perform(get("/api/operator/demo-sample-media-catalog"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.overallStatus").exists())
                    .andExpect(jsonPath("$.uploadDurationLimitSeconds").isNumber())
                    .andExpect(jsonPath("$.recommendedSampleId").value("tears-of-steel-casting"))
                    .andExpect(jsonPath("$.items[?(@.id == 'tears-of-steel-casting')]").exists())
                    .andExpect(jsonPath("$.items[?(@.id == 'big-buck-bunny-w3schools')]").exists())
                    .andExpect(jsonPath("$.items[?(@.id == 'nasa-library')]").exists())
                    .andExpect(jsonPath("$.configuredPaths[?(@.envVar == 'LINGUAFRAME_TEARS_SAMPLE_PATH')]").exists())
                    .andExpect(jsonPath("$.configuredPaths[?(@.envVar == 'LINGUAFRAME_DEMO_SAMPLE_PATH')]").exists())
                    .andExpect(jsonPath("$.commands[?(@.command == 'scripts/demo/docker-e2e-tears-of-steel-full.sh')]").exists())
                    .andExpect(jsonPath("$.notesMarkdown").value(org.hamcrest.Matchers.containsString(
                            "LinguaFrame Demo Sample Media Catalog"
                    )));
        }

        @Test
        void returnsDemoRunLauncher() throws Exception {
            mockMvc.perform(get("/api/operator/demo-run-launcher"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.overallStatus").exists())
                    .andExpect(jsonPath("$.recommendedSampleId").value("tears-of-steel-casting"))
                    .andExpect(jsonPath("$.recommendedProfileId").value("tears-showcase"))
                    .andExpect(jsonPath("$.recommendedNextCommand").exists())
                    .andExpect(jsonPath("$.gates[?(@.id == 'sample-media')]").exists())
                    .andExpect(jsonPath("$.gates[?(@.id == 'upload-readiness')]").exists())
                    .andExpect(jsonPath("$.commands[?(@.command == 'scripts/demo/docker-e2e-tears-of-steel-full.sh')]").exists())
                    .andExpect(jsonPath("$.expectedEvidence[?(@.path == '/tmp/linguaframe-demo/full-tears/demo-presenter-pack.json')]").exists())
                    .andExpect(jsonPath("$.notesMarkdown").value(org.hamcrest.Matchers.containsString(
                            "LinguaFrame Demo Run Launcher"
                    )));
        }

        @Test
        void returnsDemoPresentationCockpit() throws Exception {
            Instant createdAt = Instant.parse("2026-06-27T07:00:00Z");
            createJob("cockpit-controller-video", "cockpit-controller-job", "cockpit.mp4",
                    LocalizationJobStatus.COMPLETED, createdAt);

            mockMvc.perform(get("/api/operator/demo-presentation-cockpit")
                            .param("jobId", "cockpit-controller-job"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.overallStatus").exists())
                    .andExpect(jsonPath("$.phase").exists())
                    .andExpect(jsonPath("$.recommendedNextAction").exists())
                    .andExpect(jsonPath("$.selectedRun.jobId").value("cockpit-controller-job"))
                    .andExpect(jsonPath("$.checks[?(@.key == 'DEMO_RUN_LAUNCHER')]").exists())
                    .andExpect(jsonPath("$.checks[?(@.key == 'UPLOAD_READINESS')]").exists())
                    .andExpect(jsonPath("$.checks[?(@.key == 'ACCEPTANCE_GATE')]").exists())
                    .andExpect(jsonPath("$.links[?(@.url == '/api/operator/demo-run-launcher')]").exists())
                    .andExpect(jsonPath("$.links[?(@.url == '/api/jobs/cockpit-controller-job/demo-acceptance-gate')]").exists())
                    .andExpect(jsonPath("$.safetyNotes[0]").value(org.hamcrest.Matchers.containsString(
                            "Metadata-only cockpit"
                    )));
        }

        @Test
        void returnsModelUsageLedger() throws Exception {
            Instant createdAt = Instant.parse("2026-06-27T08:00:00Z");
            createJob("ledger-controller-video", "ledger-controller-job", "ledger.mp4",
                    LocalizationJobStatus.COMPLETED, createdAt);
            saveModelCall("ledger-controller-call", "ledger-controller-job", createdAt.plusSeconds(1));

            mockMvc.perform(get("/api/operator/model-usage-ledger").param("limit", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.limit").value(5))
                    .andExpect(jsonPath("$.summary.ledgerStatus").value("READY"))
                    .andExpect(jsonPath("$.summary.modelCallCount").value(1))
                    .andExpect(jsonPath("$.jobs[0].jobId").value("ledger-controller-job"))
                    .andExpect(jsonPath("$.recentCalls[0].modelCallId").value("ledger-controller-call"))
                    .andExpect(jsonPath("$.safeLinks[?(@ == '/api/operator/model-usage-ledger/markdown/download')]").exists());
        }

        @Test
        void downloadsModelUsageLedgerMarkdown() throws Exception {
            Instant createdAt = Instant.parse("2026-06-27T08:30:00Z");
            createJob("ledger-markdown-controller-video", "ledger-markdown-controller-job", "ledger-markdown.mp4",
                    LocalizationJobStatus.COMPLETED, createdAt);
            saveModelCall("ledger-markdown-controller-call", "ledger-markdown-controller-job", createdAt.plusSeconds(1));

            mockMvc.perform(get("/api/operator/model-usage-ledger/markdown/download").param("limit", "5"))
                    .andExpect(status().isOk())
                    .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                            result.getResponse().getContentAsString()
                    ).contains("LinguaFrame Model Usage Ledger", "ledger-markdown-controller-job")
                            .doesNotContain("source-videos/ledger-markdown-controller-video"));
        }

        @Test
        void returnsDemoSessionCommandCenter() throws Exception {
            Instant createdAt = Instant.parse("2026-06-27T09:00:00Z");
            createJob("session-controller-video", "session-controller-job", "session.mp4",
                    LocalizationJobStatus.COMPLETED, createdAt);
            saveModelCall("session-controller-call", "session-controller-job", createdAt.plusSeconds(1));

            mockMvc.perform(get("/api/operator/demo-session-command-center")
                            .param("jobId", "session-controller-job"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.overallStatus").exists())
                    .andExpect(jsonPath("$.phase").exists())
                    .andExpect(jsonPath("$.focusRun.jobId").value("session-controller-job"))
                    .andExpect(jsonPath("$.modelCallCount").value(1))
                    .andExpect(jsonPath("$.phases[?(@.id == 'model-usage')]").exists())
                    .andExpect(jsonPath("$.actions[?(@.command == 'LINGUAFRAME_DEMO_JOB_ID=session-controller-job scripts/demo/demo-session-command-center.sh')]").exists())
                    .andExpect(jsonPath("$.evidenceLinks[?(@.href == '/api/operator/demo-session-command-center/markdown/download')]").exists())
                    .andExpect(jsonPath("$.safetyNotes[0]").value(org.hamcrest.Matchers.containsString(
                            "metadata-only and read-only"
                    )));
        }

        @Test
        void downloadsDemoSessionCommandCenterMarkdown() throws Exception {
            Instant createdAt = Instant.parse("2026-06-27T09:30:00Z");
            createJob("session-markdown-controller-video", "session-markdown-controller-job", "session-markdown.mp4",
                    LocalizationJobStatus.COMPLETED, createdAt);
            saveModelCall("session-markdown-controller-call", "session-markdown-controller-job", createdAt.plusSeconds(1));

            mockMvc.perform(get("/api/operator/demo-session-command-center/markdown/download")
                            .param("jobId", "session-markdown-controller-job"))
                    .andExpect(status().isOk())
                    .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                            result.getResponse().getContentAsString()
                    ).contains("LinguaFrame Demo Session Command Center", "session-markdown-controller-job")
                            .doesNotContain("source-videos/session-markdown-controller-video"));
        }

        @Test
        void downloadsDemoSessionEvidencePackageZip() throws Exception {
            Instant createdAt = Instant.parse("2026-06-27T10:00:00Z");
            createJob("session-package-controller-video", "session-package-controller-job", "session-package.mp4",
                    LocalizationJobStatus.COMPLETED, createdAt);
            saveModelCall("session-package-controller-call", "session-package-controller-job", createdAt.plusSeconds(1));

            mockMvc.perform(get("/api/operator/demo-session-evidence-package/download")
                            .param("jobId", "session-package-controller-job"))
                    .andExpect(status().isOk())
                    .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                            result.getResponse().getHeader("Content-Disposition")
                    ).contains("linguaframe-demo-session-session-package-controller-job-evidence-package.zip"))
                    .andExpect(result -> {
                        Map<String, String> entries = zipEntries(result.getResponse().getContentAsByteArray());
                        org.assertj.core.api.Assertions.assertThat(entries.keySet())
                                .contains(
                                        "manifest.json",
                                        "README.md",
                                        "command-center.json",
                                        "command-center.md",
                                        "operations.json",
                                        "launch-rehearsal.json",
                                        "model-usage-ledger.json",
                                        "presentation-cockpit.json",
                                        "evidence-gallery.json",
                                        "run-archive.json"
                                );
                        org.assertj.core.api.Assertions.assertThat(entries.get("manifest.json"))
                                .contains("DEMO_SESSION_EVIDENCE_PACKAGE", "session-package-controller-job");
                        org.assertj.core.api.Assertions.assertThat(String.join("\n", entries.values()))
                                .doesNotContain("source-videos/session-package-controller-video")
                                .doesNotContain("OPENAI_API_KEY")
                                .doesNotContain("private-demo-token")
                                .doesNotContain("provider request payload")
                                .doesNotContain("raw transcript text:");
                    });
        }

        private void createJob(
                String videoId,
                String jobId,
                String filename,
                LocalizationJobStatus status,
                Instant createdAt
        ) {
            videoRepository.save(new VideoRecord(
                    videoId,
                    filename,
                    "video/mp4",
                    123L,
                    "source-videos/" + videoId + "/" + filename,
                    MediaUploadStatus.UPLOADED,
                    createdAt
            ));
            jobRepository.save(new LocalizationJobRecord(jobId, videoId, "zh-CN", status, createdAt));
        }

        private void saveModelCall(String id, String jobId, Instant createdAt) {
            modelCallRepository.save(new ModelCallRecord(
                    id,
                    jobId,
                    LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                    ModelCallOperation.TRANSLATION,
                    ModelCallProvider.OPENAI,
                    "gpt-test",
                    "openai-translation-v1",
                    ModelCallStatus.SUCCEEDED,
                    120L,
                    80,
                    40,
                    null,
                    null,
                    "input summary",
                    "output summary",
                    "demo-owner",
                    new BigDecimal("0.00010000"),
                    null,
                    createdAt
            ));
        }

        private Map<String, String> zipEntries(byte[] bytes) throws Exception {
            Map<String, String> entries = new LinkedHashMap<>();
            try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    entries.put(entry.getName(), new String(zipInputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            return entries;
        }
    }

    @Nested
    @SpringBootTest(properties = "linguaframe.demo.access-token=test-token")
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class GatedDemoMode {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void requiresDemoTokenWhenGateIsConfigured() throws Exception {
            mockMvc.perform(get("/api/operator/dashboard"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/operator/dashboard")
                            .header("X-LinguaFrame-Demo-Token", "test-token"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/operator/private-demo/operations"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/operator/private-demo/operations")
                            .header("X-LinguaFrame-Demo-Token", "test-token"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/operator/private-demo/launch-rehearsal"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/operator/private-demo/launch-rehearsal")
                            .header("X-LinguaFrame-Demo-Token", "test-token"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/operator/private-demo/evidence-gallery"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/operator/private-demo/evidence-gallery")
                            .header("X-LinguaFrame-Demo-Token", "test-token"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/operator/private-demo/run-archive"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/operator/private-demo/run-archive")
                            .header("X-LinguaFrame-Demo-Token", "test-token"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/operator/demo-sample-media-catalog"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/operator/demo-sample-media-catalog")
                            .header("X-LinguaFrame-Demo-Token", "test-token"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/operator/demo-run-launcher"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/operator/demo-run-launcher")
                            .header("X-LinguaFrame-Demo-Token", "test-token"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/operator/demo-presentation-cockpit"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/operator/demo-presentation-cockpit")
                            .header("X-LinguaFrame-Demo-Token", "test-token"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/operator/model-usage-ledger"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/operator/model-usage-ledger")
                            .header("X-LinguaFrame-Demo-Token", "test-token"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/operator/model-usage-ledger/markdown/download"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/operator/model-usage-ledger/markdown/download")
                            .header("X-LinguaFrame-Demo-Token", "test-token"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/operator/demo-session-command-center"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/operator/demo-session-command-center")
                            .header("X-LinguaFrame-Demo-Token", "test-token"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/operator/demo-session-command-center/markdown/download"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/operator/demo-session-command-center/markdown/download")
                            .header("X-LinguaFrame-Demo-Token", "test-token"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/operator/demo-session-evidence-package/download"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/operator/demo-session-evidence-package/download")
                            .header("X-LinguaFrame-Demo-Token", "test-token"))
                    .andExpect(status().isOk());
        }
    }
}
