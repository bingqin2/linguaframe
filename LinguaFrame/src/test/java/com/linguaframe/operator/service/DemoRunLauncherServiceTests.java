package com.linguaframe.operator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.media.domain.vo.DemoUploadReadinessCheckVo;
import com.linguaframe.media.domain.vo.DemoUploadReadinessVo;
import com.linguaframe.media.service.DemoUploadReadinessService;
import com.linguaframe.operator.domain.vo.DemoRunLauncherVo;
import com.linguaframe.operator.domain.vo.DemoSampleMediaCatalogVo;
import com.linguaframe.operator.domain.vo.DemoSampleMediaCommandVo;
import com.linguaframe.operator.domain.vo.DemoSampleMediaConfiguredPathVo;
import com.linguaframe.operator.domain.vo.DemoSampleMediaItemVo;
import com.linguaframe.operator.service.impl.DemoRunLauncherServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoRunLauncherServiceTests {

    @Test
    void reportsReadyWhenRecommendedSampleExistsAndUploadReadinessIsReady() throws Exception {
        DemoRunLauncherVo launcher = service(
                catalog("READY", configuredTearsPath()),
                readiness("READY", List.of(check("runtime-contract", "READY", false)))
        ).launcher();

        assertThat(launcher.overallStatus()).isEqualTo("READY");
        assertThat(launcher.recommendedSampleId()).isEqualTo("tears-of-steel-casting");
        assertThat(launcher.recommendedProfileId()).isEqualTo("tears-showcase");
        assertThat(launcher.recommendedNextCommand()).isEqualTo("scripts/demo/docker-e2e-tears-of-steel-full.sh");
        assertThat(launcher.gates())
                .extracting("id")
                .contains("sample-media", "upload-readiness", "runtime-contract");
        assertThat(launcher.expectedEvidence())
                .extracting("path")
                .contains(
                        "/tmp/linguaframe-demo/full-tears/job-detail.json",
                        "/tmp/linguaframe-demo/full-tears/demo-presenter-pack.json",
                        "/tmp/linguaframe-demo/full-tears/demo-run-snapshot.zip"
                );
        assertThat(toJson(launcher))
                .contains("Demo Run Launcher")
                .contains("LINGUAFRAME_DEMO_PROFILE_ID=tears-showcase")
                .doesNotContain("/Users/example")
                .doesNotContain("sk-test")
                .doesNotContain("provider payload")
                .doesNotContain("raw transcript text")
                .doesNotContain("raw subtitle text");
    }

    @Test
    void reportsAttentionWhenRecommendedSampleIsNotConfigured() {
        DemoRunLauncherVo launcher = service(
                catalog("ATTENTION", unconfiguredTearsPath()),
                readiness("READY", List.of(check("runtime-contract", "READY", false)))
        ).launcher();

        assertThat(launcher.overallStatus()).isEqualTo("ATTENTION");
        assertThat(launcher.gates())
                .filteredOn(gate -> gate.id().equals("sample-media"))
                .singleElement()
                .satisfies(gate -> {
                    assertThat(gate.status()).isEqualTo("ATTENTION");
                    assertThat(gate.blocking()).isFalse();
                    assertThat(gate.nextAction()).contains("LINGUAFRAME_TEARS_SAMPLE_PATH");
                });
    }

    @Test
    void reportsBlockedWhenUploadReadinessHasBlockingGate() {
        DemoRunLauncherVo launcher = service(
                catalog("READY", configuredTearsPath()),
                readiness("BLOCKED", List.of(check("owner-quota", "BLOCKED", true)))
        ).launcher();

        assertThat(launcher.overallStatus()).isEqualTo("BLOCKED");
        assertThat(launcher.gates())
                .filteredOn(gate -> gate.id().equals("upload-readiness"))
                .singleElement()
                .satisfies(gate -> {
                    assertThat(gate.status()).isEqualTo("BLOCKED");
                    assertThat(gate.blocking()).isTrue();
                });
        assertThat(launcher.recommendedNextCommand()).isEqualTo("scripts/demo/upload-readiness.sh");
    }

    @Test
    void includesCommandAndEvidenceContract() {
        DemoRunLauncherVo launcher = service(
                catalog("READY", configuredTearsPath()),
                readiness("ATTENTION", List.of(check("paid-provider-check", "ATTENTION", false)))
        ).launcher();

        assertThat(launcher.overallStatus()).isEqualTo("ATTENTION");
        assertThat(launcher.commands())
                .extracting("command")
                .contains(
                        "scripts/demo/demo-run-launcher.sh",
                        "scripts/demo/openai-demo-preflight.sh",
                        "scripts/demo/docker-e2e-tears-of-steel-full.sh"
                );
        assertThat(launcher.expectedEvidence())
                .extracting("label")
                .contains("Job detail JSON", "Demo presenter pack", "Demo run snapshot ZIP");
    }

    @Test
    void keepsLauncherMetadataOnly() throws Exception {
        DemoSampleMediaConfiguredPathVo path = new DemoSampleMediaConfiguredPathVo(
                "LINGUAFRAME_TEARS_SAMPLE_PATH",
                "CONFIGURED",
                "tos_casting-720p.mp4",
                "mp4",
                123L,
                "Configured from /Users/example/Downloads/tos_casting-720p.mp4",
                false
        );
        DemoRunLauncherVo launcher = service(
                catalog("READY", path),
                readiness("READY", List.of(check("runtime-contract", "READY", false)))
        ).launcher();

        assertThat(toJson(launcher))
                .contains("tos_casting-720p.mp4")
                .doesNotContain("/Users/example")
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("private-demo-token")
                .doesNotContain("source-videos/")
                .doesNotContain("job-artifacts/")
                .doesNotContain("provider payload")
                .doesNotContain("raw transcript text")
                .doesNotContain("raw subtitle text");
    }

    private DemoRunLauncherService service(
            DemoSampleMediaCatalogVo catalog,
            DemoUploadReadinessVo readiness
    ) {
        DemoSampleMediaCatalogService sampleService = () -> catalog;
        DemoUploadReadinessService readinessService = ignored -> readiness;
        return new DemoRunLauncherServiceImpl(sampleService, readinessService);
    }

    private DemoSampleMediaCatalogVo catalog(String status, DemoSampleMediaConfiguredPathVo configuredPath) {
        return new DemoSampleMediaCatalogVo(
                Instant.parse("2026-06-29T00:00:00Z"),
                status,
                300,
                "tears-of-steel-casting",
                List.of(new DemoSampleMediaItemVo(
                        "tears-of-steel-casting",
                        "Tears of Steel casting clip",
                        "Blender Studio",
                        "https://studio.blender.org/films/tears-of-steel/",
                        "Credit Blender Studio.",
                        "Check license.",
                        "Best full demo.",
                        "Complete local sample under 300 seconds.",
                        "scripts/demo/docker-e2e-tears-of-steel-full.sh",
                        List.of("recommended")
                )),
                List.of(configuredPath),
                List.of(new DemoSampleMediaCommandVo(
                        "Run full Tears sample",
                        "scripts/demo/docker-e2e-tears-of-steel-full.sh",
                        "Process the configured complete Tears sample."
                )),
                "# Catalog",
                List.of()
        );
    }

    private DemoSampleMediaConfiguredPathVo configuredTearsPath() {
        return new DemoSampleMediaConfiguredPathVo(
                "LINGUAFRAME_TEARS_SAMPLE_PATH",
                "CONFIGURED",
                "tos_casting-720p.mp4",
                "mp4",
                123L,
                "LINGUAFRAME_TEARS_SAMPLE_PATH is configured.",
                false
        );
    }

    private DemoSampleMediaConfiguredPathVo unconfiguredTearsPath() {
        return new DemoSampleMediaConfiguredPathVo(
                "LINGUAFRAME_TEARS_SAMPLE_PATH",
                "UNCONFIGURED",
                "",
                "",
                null,
                "LINGUAFRAME_TEARS_SAMPLE_PATH is not configured.",
                false
        );
    }

    private DemoUploadReadinessVo readiness(String status, List<DemoUploadReadinessCheckVo> checks) {
        return new DemoUploadReadinessVo(
                status,
                "demo-owner",
                "tears-showcase",
                Instant.parse("2026-06-29T00:00:00Z"),
                checks,
                List.of("Upload can start after file validation passes."),
                List.of("/api/media/uploads/readiness")
        );
    }

    private DemoUploadReadinessCheckVo check(String id, String status, boolean blocking) {
        return new DemoUploadReadinessCheckVo(
                id,
                id,
                status,
                id + " detail",
                id + " next action",
                blocking
        );
    }

    private String toJson(DemoRunLauncherVo launcher) throws Exception {
        return new ObjectMapper().findAndRegisterModules().writeValueAsString(launcher);
    }
}
