package com.linguaframe.media.service;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.media.domain.vo.UploadSourceReuseCandidateVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseDecisionVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseVo;
import com.linguaframe.media.service.impl.UploadSourceReuseDecisionServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UploadSourceReuseDecisionServiceTests {

    private final UploadSourceReuseDecisionService service = new UploadSourceReuseDecisionServiceImpl();

    @Test
    void recommendsReusingCompletedRunWithSafeLinks() {
        UploadSourceReuseDecisionVo decision = service.decide(new UploadSourceReuseVo(
                "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81",
                1,
                "REVIEW_EXISTING_COMPLETED_RUN",
                "job-existing",
                List.of(candidate("job-existing", LocalizationJobStatus.COMPLETED))
        ));

        assertThat(decision.status()).isEqualTo("REUSE_COMPLETED_RUN");
        assertThat(decision.headline()).contains("completed run");
        assertThat(decision.recommendedExistingJobId()).isEqualTo("job-existing");
        assertThat(decision.actions())
                .extracting("id")
                .contains("openJob", "downloadPackage", "refreshPlan");
        assertThat(decision.links())
                .extracting("href")
                .contains("/api/jobs/job-existing/demo-run-package/download");
        assertThat(decision.safetyNotes()).allSatisfy(note -> assertThat(note).doesNotContain("source-videos/"));
    }

    @Test
    void recommendsWaitingForActiveRun() {
        UploadSourceReuseDecisionVo decision = service.decide(new UploadSourceReuseVo(
                "hash",
                1,
                "WAIT_FOR_ACTIVE_RUN",
                "job-active",
                List.of(candidate("job-active", LocalizationJobStatus.PROCESSING))
        ));

        assertThat(decision.status()).isEqualTo("WAIT_FOR_ACTIVE_RUN");
        assertThat(decision.actions())
                .anySatisfy(action -> {
                    assertThat(action.id()).isEqualTo("openActiveJob");
                    assertThat(action.enabled()).isTrue();
                    assertThat(action.href()).isEqualTo("/api/jobs/job-active");
                })
                .anySatisfy(action -> {
                    assertThat(action.id()).isEqualTo("waitForCompletion");
                    assertThat(action.enabled()).isFalse();
                });
    }

    @Test
    void recommendsNewUploadWhenNoCandidateExists() {
        UploadSourceReuseDecisionVo decision = service.decide(UploadSourceReuseVo.empty());

        assertThat(decision.status()).isEqualTo("UPLOAD_NEW_SOURCE");
        assertThat(decision.candidateCount()).isZero();
        assertThat(decision.links()).isEmpty();
        assertThat(decision.actions())
                .singleElement()
                .satisfies(action -> {
                    assertThat(action.id()).isEqualTo("uploadNewSource");
                    assertThat(action.enabled()).isTrue();
                });
    }

    private static UploadSourceReuseCandidateVo candidate(String jobId, LocalizationJobStatus status) {
        return new UploadSourceReuseCandidateVo(
                "video-" + jobId,
                jobId,
                "sample.mp4",
                90,
                status,
                "tears-showcase",
                "FORMAL",
                "HIGH_CONTRAST",
                "BALANCED",
                Instant.parse("2026-06-29T00:00:00Z"),
                "/api/jobs/" + jobId,
                "/api/jobs/" + jobId + "/demo-share-sheet",
                "/api/jobs/" + jobId + "/evidence/markdown/download",
                "/api/jobs/" + jobId + "/demo-run-package/download",
                "/api/jobs/" + jobId + "/demo-acceptance-gate"
        );
    }
}
