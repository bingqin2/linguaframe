package com.linguaframe.job.service;

import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.DemoPresenterPackDownloadVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackRunVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackVo;
import com.linguaframe.job.domain.vo.DemoReplayCardVo;
import com.linguaframe.job.domain.vo.DemoRunMatrixJobVo;
import com.linguaframe.job.domain.vo.DemoRunMatrixVo;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.service.impl.DemoReplayCardServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoReplayCardServiceTests {

    private static final Instant NOW = Instant.parse("2026-06-29T10:15:30Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void buildsMetadataOnlyReplayCardWithCommandsAndEvidenceLinks() {
        LocalizationJobVo job = job("job-replay", LocalizationJobStatus.COMPLETED);
        DemoRunMatrixVo matrix = matrix(job);
        DemoPresenterPackVo presenterPack = presenterPack("READY");
        DemoReplayCardService service = new DemoReplayCardServiceImpl(
                new StaticQueryService(job),
                new StaticMatrixService(matrix),
                new StaticPresenterPackService(presenterPack),
                CLOCK
        );

        DemoReplayCardVo card = service.buildReplayCard("job-replay");

        assertThat(card.jobId()).isEqualTo("job-replay");
        assertThat(card.videoId()).isEqualTo("video-replay");
        assertThat(card.generatedAt()).isEqualTo(NOW);
        assertThat(card.readiness()).isEqualTo("READY");
        assertThat(card.demoProfileId()).isEqualTo("tears-showcase");
        assertThat(card.modelCallCount()).isEqualTo(3);
        assertThat(card.providerCacheHitCount()).isEqualTo(2);
        assertThat(card.artifactCacheHitCount()).isEqualTo(1);
        assertThat(card.recommendedBaselineJobId()).isEqualTo("job-baseline");
        assertThat(card.settings())
                .extracting("label")
                .contains("Target language", "Demo profile", "Glossary", "Same-source handoff");
        assertThat(card.commands())
                .extracting("kind")
                .contains(
                        "BROWSER_REPLAY",
                        "LAUNCHER_CHECK",
                        "TEARS_FULL_REPLAY",
                        "COMPARE_WITH_BASELINE",
                        "EXPORT_REPLAY_CARD"
                );
        assertThat(card.commands())
                .extracting("command")
                .anySatisfy(command -> assertThat(String.valueOf(command)).contains("LINGUAFRAME_COMPARISON_BASELINE_JOB_ID=job-baseline"))
                .anySatisfy(command -> assertThat(String.valueOf(command)).contains("LINGUAFRAME_DEMO_JOB_ID=job-replay"));
        assertThat(card.links())
                .extracting("kind")
                .contains("REPLAY_CARD_JSON", "RUN_MATRIX_JSON", "PRESENTER_PACK_JSON", "DEMO_RUN_PACKAGE");
        assertThat(card.toString())
                .doesNotContain("sk-test")
                .doesNotContain("/Users/example")
                .doesNotContain("provider payload");
    }

    @Test
    void marksReplayCardNeedsAttentionWhenJobIsNotCompleted() {
        LocalizationJobVo job = job("job-replay-failed", LocalizationJobStatus.FAILED);
        DemoReplayCardService service = new DemoReplayCardServiceImpl(
                new StaticQueryService(job),
                new StaticMatrixService(matrix(job)),
                new StaticPresenterPackService(presenterPack("NEEDS_ATTENTION")),
                CLOCK
        );

        DemoReplayCardVo card = service.buildReplayCard("job-replay-failed");

        assertThat(card.readiness()).isEqualTo("NEEDS_ATTENTION");
        assertThat(card.safetyNotes())
                .contains("The selected job is not completed, so replay guidance may be incomplete.");
    }

    private static LocalizationJobVo job(String jobId, LocalizationJobStatus status) {
        return new LocalizationJobVo(
                jobId,
                "video-replay",
                "zh-CN",
                "verse",
                "FORMAL",
                "HIGH_CONTRAST",
                3,
                "abc123",
                "BALANCED",
                "tears-showcase",
                status,
                NOW.minusSeconds(120),
                NOW.minusSeconds(90),
                status == LocalizationJobStatus.COMPLETED ? NOW.minusSeconds(10) : null,
                status == LocalizationJobStatus.FAILED ? NOW.minusSeconds(10) : null,
                null,
                status == LocalizationJobStatus.FAILED ? "Provider unavailable" : null,
                0,
                JobDispatchEventStatus.DISPATCHED,
                0,
                NOW.minusSeconds(110),
                List.of(),
                new JobUsageSummaryVo(3, 0, 4200, new BigDecimal("0.00007800"), 100, 80, new BigDecimal("45.0"), 1200),
                new JobCacheSummaryVo(1, 6, 2),
                List.of(),
                null,
                null,
                null
        );
    }

    private static DemoRunMatrixVo matrix(LocalizationJobVo job) {
        return new DemoRunMatrixVo(
                job.jobId(),
                job.videoId(),
                NOW,
                List.of(matrixJob(job), matrixJob(job, "job-baseline", "quick-baseline")),
                "job-baseline",
                job.jobId(),
                "job-baseline"
        );
    }

    private static DemoRunMatrixJobVo matrixJob(LocalizationJobVo job) {
        return matrixJob(job, job.jobId(), job.demoProfileId());
    }

    private static DemoRunMatrixJobVo matrixJob(LocalizationJobVo job, String jobId, String demoProfileId) {
        return new DemoRunMatrixJobVo(
                jobId,
                job.videoId(),
                "replay.mp4",
                job.targetLanguage(),
                demoProfileId,
                job.ttsVoice(),
                job.translationStyle(),
                job.subtitleStylePreset(),
                job.translationGlossaryEntryCount(),
                job.translationGlossaryHash(),
                job.subtitlePolishingMode(),
                job.status(),
                job.createdAt(),
                job.completedAt(),
                null,
                null,
                0,
                "tears-showcase".equals(demoProfileId) ? 91 : 82,
                "GOOD",
                3,
                0,
                new BigDecimal("0.00007800"),
                1,
                6,
                2,
                true
        );
    }

    private static DemoPresenterPackVo presenterPack(String readiness) {
        return new DemoPresenterPackVo(
                "job-replay",
                "video-replay",
                NOW,
                "tears-showcase demo to zh-CN",
                readiness,
                "job-baseline",
                "job-replay",
                "job-baseline",
                List.of(new DemoPresenterPackRunVo(
                        "job-replay",
                        "tears-showcase",
                        LocalizationJobStatus.COMPLETED,
                        NOW.minusSeconds(10),
                        91,
                        new BigDecimal("0.00007800"),
                        3,
                        2,
                        true,
                        List.of("ANCHOR", "BEST_QUALITY")
                )),
                List.of(
                        new DemoPresenterPackDownloadVo("DEMO_RUN_PACKAGE", "Demo run package", "/api/jobs/job-replay/demo-run-package/download"),
                        new DemoPresenterPackDownloadVo("AI_AUDIT_PACKAGE", "AI audit package", "/api/jobs/job-replay/ai-audit-package/download")
                ),
                "# LinguaFrame Demo Presenter Pack\n"
        );
    }

    private record StaticQueryService(LocalizationJobVo job) implements LocalizationJobQueryService {
        @Override
        public com.linguaframe.job.domain.vo.LocalizationJobListVo listJobs(LocalizationJobStatus status, Integer limit, Integer offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LocalizationJobVo getJob(String jobId) {
            return job;
        }

        @Override
        public com.linguaframe.job.domain.vo.JobDiagnosticsReportVo getDiagnosticsReport(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticMatrixService(DemoRunMatrixVo matrix) implements DemoRunMatrixService {
        @Override
        public DemoRunMatrixVo buildMatrix(String anchorJobId, Integer limit) {
            return matrix;
        }
    }

    private record StaticPresenterPackService(DemoPresenterPackVo presenterPack) implements DemoPresenterPackService {
        @Override
        public DemoPresenterPackVo buildPresenterPack(String jobId) {
            return presenterPack;
        }
    }
}
