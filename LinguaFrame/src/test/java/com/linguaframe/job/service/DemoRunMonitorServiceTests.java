package com.linguaframe.job.service;

import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.DemoRunMonitorVo;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.JobPipelineProgressVo;
import com.linguaframe.job.domain.vo.JobStageProgressVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.service.impl.DemoRunMonitorServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoRunMonitorServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-29T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void buildsRunningMonitorWithCurrentStageTimingAndSafeLinks() {
        DemoRunMonitorService service = new DemoRunMonitorServiceImpl(
                new SingleJobQueryService(processingJob("job-monitor-running", "video-monitor", false)),
                FIXED_CLOCK
        );

        DemoRunMonitorVo monitor = service.buildMonitor("job-monitor-running");

        assertThat(monitor.jobId()).isEqualTo("job-monitor-running");
        assertThat(monitor.videoId()).isEqualTo("video-monitor");
        assertThat(monitor.status()).isEqualTo(LocalizationJobStatus.PROCESSING);
        assertThat(monitor.dispatchStatus()).isEqualTo(JobDispatchEventStatus.DISPATCHED);
        assertThat(monitor.generatedAt()).isEqualTo(Instant.parse("2026-06-29T10:00:00Z"));
        assertThat(monitor.elapsedMs()).isEqualTo(540000L);
        assertThat(monitor.currentStage()).isEqualTo(LocalizationJobStage.TARGET_SUBTITLE_EXPORT);
        assertThat(monitor.completedStageCount()).isEqualTo(2);
        assertThat(monitor.totalStageCount()).isEqualTo(3);
        assertThat(monitor.slowestStage()).isEqualTo(LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT);
        assertThat(monitor.slowestStageDurationMs()).isEqualTo(90000L);
        assertThat(monitor.attentionLevel()).isEqualTo("RUNNING");
        assertThat(monitor.summary()).contains("TARGET_SUBTITLE_EXPORT", "running");
        assertThat(monitor.recommendedNextAction()).contains("Keep watching");
        assertThat(monitor.stages()).hasSize(3);
        assertThat(monitor.stages().get(2).attention()).isEqualTo("RUNNING");
        assertThat(monitor.links()).extracting(link -> link.kind()).containsExactly(
                "JOB_DETAIL",
                "EVENT_STREAM",
                "DIAGNOSTICS_JSON",
                "DEMO_SHARE_SHEET"
        );
        assertThat(monitor.markdown()).contains("# LinguaFrame Demo Run Monitor");
        assertThat(monitor.markdown()).contains("- Current stage: TARGET_SUBTITLE_EXPORT");
        assertThat(monitor.markdown()).doesNotContain("raw transcript text");
        assertThat(monitor.markdown()).doesNotContain("/Users/example");
        assertThat(monitor.markdown()).doesNotContain("sk-test");
        assertThat(monitor.markdown()).doesNotContain("provider payload");
    }

    @Test
    void marksStaleInFlightStageAsAttention() {
        DemoRunMonitorService service = new DemoRunMonitorServiceImpl(
                new SingleJobQueryService(processingJob("job-monitor-stale", "video-monitor", true)),
                FIXED_CLOCK
        );

        DemoRunMonitorVo monitor = service.buildMonitor("job-monitor-stale");

        assertThat(monitor.attentionLevel()).isEqualTo("ATTENTION");
        assertThat(monitor.summary()).contains("has not produced timeline updates");
        assertThat(monitor.recommendedNextAction()).contains("Check worker logs");
        assertThat(monitor.stages().get(2).attention()).isEqualTo("STALE");
    }

    @Test
    void buildsCompletedMonitorWithTerminalNextAction() {
        DemoRunMonitorService service = new DemoRunMonitorServiceImpl(
                new SingleJobQueryService(completedJob()),
                FIXED_CLOCK
        );

        DemoRunMonitorVo monitor = service.buildMonitor("job-monitor-complete");

        assertThat(monitor.attentionLevel()).isEqualTo("READY");
        assertThat(monitor.currentStage()).isEqualTo(LocalizationJobStage.ARTIFACT_SUMMARY);
        assertThat(monitor.elapsedMs()).isEqualTo(420000L);
        assertThat(monitor.summary()).contains("completed");
        assertThat(monitor.recommendedNextAction()).contains("Open the demo share sheet");
    }

    @Test
    void buildsFailedMonitorWithoutLeakingFailureReason() {
        DemoRunMonitorService service = new DemoRunMonitorServiceImpl(
                new SingleJobQueryService(failedJob()),
                FIXED_CLOCK
        );

        DemoRunMonitorVo monitor = service.buildMonitor("job-monitor-failed");

        assertThat(monitor.attentionLevel()).isEqualTo("BLOCKED");
        assertThat(monitor.currentStage()).isEqualTo(LocalizationJobStage.TARGET_SUBTITLE_EXPORT);
        assertThat(monitor.summary()).contains("failed");
        assertThat(monitor.recommendedNextAction()).contains("Open diagnostics");
        assertThat(monitor.markdown()).doesNotContain("raw transcript text");
        assertThat(monitor.markdown()).doesNotContain("/Users/example");
        assertThat(monitor.markdown()).doesNotContain("sk-test");
        assertThat(monitor.markdown()).doesNotContain("provider payload");
    }

    @Test
    void buildsQueuedMonitorWithDispatchGuidance() {
        DemoRunMonitorService service = new DemoRunMonitorServiceImpl(
                new SingleJobQueryService(queuedJob()),
                FIXED_CLOCK
        );

        DemoRunMonitorVo monitor = service.buildMonitor("job-monitor-queued");

        assertThat(monitor.attentionLevel()).isEqualTo("QUEUED");
        assertThat(monitor.currentStage()).isNull();
        assertThat(monitor.elapsedMs()).isEqualTo(120000L);
        assertThat(monitor.summary()).contains("queued");
        assertThat(monitor.recommendedNextAction()).contains("Wait for dispatch");
    }

    private static LocalizationJobVo processingJob(String jobId, String videoId, boolean stale) {
        Instant startedAt = stale ? Instant.parse("2026-06-29T09:20:00Z") : Instant.parse("2026-06-29T09:51:00Z");
        return job(
                jobId,
                videoId,
                LocalizationJobStatus.PROCESSING,
                Instant.parse("2026-06-29T09:45:00Z"),
                startedAt,
                null,
                null,
                null,
                null,
                JobDispatchEventStatus.DISPATCHED,
                Instant.parse("2026-06-29T09:46:00Z"),
                progress(false, LocalizationJobStage.TARGET_SUBTITLE_EXPORT, 2, 0, List.of(
                        stage(LocalizationJobStage.WORKER_RECEIVED, JobTimelineEventStatus.SUCCEEDED,
                                Instant.parse("2026-06-29T09:51:00Z"), Instant.parse("2026-06-29T09:51:05Z"), 5000L, "worker received"),
                        stage(LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT, JobTimelineEventStatus.SUCCEEDED,
                                Instant.parse("2026-06-29T09:51:05Z"), Instant.parse("2026-06-29T09:52:35Z"), 90000L, "transcribed"),
                        stage(LocalizationJobStage.TARGET_SUBTITLE_EXPORT, JobTimelineEventStatus.STARTED,
                                startedAt, null, null, "raw transcript text /Users/example sk-test provider payload")
                ))
        );
    }

    private static LocalizationJobVo completedJob() {
        return job(
                "job-monitor-complete",
                "video-monitor",
                LocalizationJobStatus.COMPLETED,
                Instant.parse("2026-06-29T09:00:00Z"),
                Instant.parse("2026-06-29T09:01:00Z"),
                Instant.parse("2026-06-29T09:08:00Z"),
                null,
                null,
                null,
                JobDispatchEventStatus.DISPATCHED,
                Instant.parse("2026-06-29T09:00:30Z"),
                progress(true, LocalizationJobStage.ARTIFACT_SUMMARY, 3, 0, List.of(
                        stage(LocalizationJobStage.WORKER_RECEIVED, JobTimelineEventStatus.SUCCEEDED,
                                Instant.parse("2026-06-29T09:01:00Z"), Instant.parse("2026-06-29T09:01:05Z"), 5000L, "worker received"),
                        stage(LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT, JobTimelineEventStatus.SUCCEEDED,
                                Instant.parse("2026-06-29T09:01:05Z"), Instant.parse("2026-06-29T09:06:00Z"), 295000L, "transcribed"),
                        stage(LocalizationJobStage.ARTIFACT_SUMMARY, JobTimelineEventStatus.SUCCEEDED,
                                Instant.parse("2026-06-29T09:06:00Z"), Instant.parse("2026-06-29T09:08:00Z"), 120000L, "summary")
                ))
        );
    }

    private static LocalizationJobVo failedJob() {
        return job(
                "job-monitor-failed",
                "video-monitor",
                LocalizationJobStatus.FAILED,
                Instant.parse("2026-06-29T09:00:00Z"),
                Instant.parse("2026-06-29T09:01:00Z"),
                null,
                Instant.parse("2026-06-29T09:05:00Z"),
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                "raw transcript text /Users/example sk-test provider payload",
                JobDispatchEventStatus.DISPATCHED,
                Instant.parse("2026-06-29T09:00:30Z"),
                progress(true, LocalizationJobStage.TARGET_SUBTITLE_EXPORT, 1, 1, List.of(
                        stage(LocalizationJobStage.WORKER_RECEIVED, JobTimelineEventStatus.SUCCEEDED,
                                Instant.parse("2026-06-29T09:01:00Z"), Instant.parse("2026-06-29T09:01:05Z"), 5000L, "worker received"),
                        stage(LocalizationJobStage.TARGET_SUBTITLE_EXPORT, JobTimelineEventStatus.FAILED,
                                Instant.parse("2026-06-29T09:01:05Z"), Instant.parse("2026-06-29T09:05:00Z"), 235000L,
                                "raw transcript text /Users/example sk-test provider payload")
                ))
        );
    }

    private static LocalizationJobVo queuedJob() {
        return job(
                "job-monitor-queued",
                "video-monitor",
                LocalizationJobStatus.QUEUED,
                Instant.parse("2026-06-29T09:58:00Z"),
                null,
                null,
                null,
                null,
                null,
                JobDispatchEventStatus.PENDING,
                null,
                progress(false, null, 0, 0, List.of())
        );
    }

    private static LocalizationJobVo job(
            String jobId,
            String videoId,
            LocalizationJobStatus status,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            Instant failedAt,
            LocalizationJobStage failureStage,
            String failureReason,
            JobDispatchEventStatus dispatchStatus,
            Instant dispatchedAt,
            JobPipelineProgressVo progress
    ) {
        return new LocalizationJobVo(
                jobId,
                videoId,
                "zh-CN",
                "verse",
                "NATURAL",
                "STANDARD",
                0,
                "",
                "OFF",
                "tears-showcase",
                status,
                createdAt,
                startedAt,
                completedAt,
                failedAt,
                failureStage,
                failureReason,
                0,
                dispatchStatus,
                dispatchStatus == null ? 0 : 1,
                dispatchedAt,
                List.of(),
                new JobUsageSummaryVo(0, 0, 0L, BigDecimal.ZERO, null, null, null, null),
                new JobCacheSummaryVo(0, 0, 0),
                List.of(),
                null,
                null,
                progress
        );
    }

    private static JobPipelineProgressVo progress(
            boolean terminal,
            LocalizationJobStage currentStage,
            int completed,
            int failed,
            List<JobStageProgressVo> stages
    ) {
        return new JobPipelineProgressVo(
                stages.size(),
                completed,
                failed,
                0,
                0,
                currentStage,
                terminal,
                stages.stream().map(JobStageProgressVo::durationMs).filter(duration -> duration != null).mapToLong(Long::longValue).sum(),
                stages.stream()
                        .filter(stage -> stage.durationMs() != null)
                        .max(java.util.Comparator.comparing(JobStageProgressVo::durationMs))
                        .map(JobStageProgressVo::stage)
                        .orElse(null),
                stages.stream()
                        .map(JobStageProgressVo::durationMs)
                        .filter(duration -> duration != null)
                        .max(Long::compareTo)
                        .orElse(null),
                stages
        );
    }

    private static JobStageProgressVo stage(
            LocalizationJobStage stage,
            JobTimelineEventStatus status,
            Instant startedAt,
            Instant finishedAt,
            Long durationMs,
            String message
    ) {
        return new JobStageProgressVo(stage, status, startedAt, finishedAt, durationMs, message);
    }

    private record SingleJobQueryService(LocalizationJobVo job) implements LocalizationJobQueryService {

        @Override
        public LocalizationJobListVo listJobs(LocalizationJobStatus status, Integer limit, Integer offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LocalizationJobVo getJob(String jobId) {
            return job;
        }

        @Override
        public JobDiagnosticsReportVo getDiagnosticsReport(String jobId) {
            throw new UnsupportedOperationException();
        }
    }
}
