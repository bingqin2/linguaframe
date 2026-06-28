package com.linguaframe.operator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.common.runtime.domain.vo.NetworkDependencyVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeContractVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeDependencySummaryVo;
import com.linguaframe.common.runtime.domain.vo.StorageDependencyVo;
import com.linguaframe.common.runtime.service.RuntimeDependencySummaryService;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsCheckVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsCommandVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsLinkVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsSectionVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsVo;
import com.linguaframe.operator.domain.vo.PrivateDemoLaunchRehearsalVo;
import com.linguaframe.operator.service.impl.PrivateDemoLaunchRehearsalServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrivateDemoLaunchRehearsalServiceTests {

    private final StubPrivateDemoOperationsService operationsService = new StubPrivateDemoOperationsService();
    private final StubRuntimeDependencySummaryService summaryService = new StubRuntimeDependencySummaryService();
    private final PrivateDemoLaunchRehearsalService service = new PrivateDemoLaunchRehearsalServiceImpl(
            operationsService,
            summaryService
    );

    @Test
    void buildsOrderedLaunchRehearsalWithRecommendedNextAttentionStep() {
        operationsService.operations = operations("ATTENTION", List.of(
                section("Access gate", "READY"),
                section("Live dependencies", "READY"),
                section("Provider readiness", "ATTENTION")
        ));

        PrivateDemoLaunchRehearsalVo rehearsal = service.launchRehearsal();

        assertThat(rehearsal.overallStatus()).isEqualTo("ATTENTION");
        assertThat(rehearsal.readyCount()).isGreaterThan(0);
        assertThat(rehearsal.attentionCount()).isGreaterThan(0);
        assertThat(rehearsal.blockedCount()).isZero();
        assertThat(rehearsal.recommendedNextStepId()).isEqualTo("openai-preflight");
        assertThat(rehearsal.steps())
                .extracting("id")
                .containsExactly(
                        "deploy-preflight",
                        "stack-startup",
                        "private-preflight",
                        "openai-preflight",
                        "backup-dry-run",
                        "restore-dry-run",
                        "short-smoke-demo",
                        "full-tears-demo",
                        "presenter-pack-export",
                        "operations-report-export"
                );
        assertThat(rehearsal.steps())
                .extracting("command")
                .contains(
                        "LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-deploy-preflight.sh",
                        "LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/openai-demo-preflight.sh",
                        "scripts/demo/docker-e2e-tears-of-steel-full.sh"
                );
        assertThat(rehearsal.evidenceDownloads())
                .contains(
                        "/api/operator/private-demo/operations",
                        "/api/jobs/{jobId}/demo-presenter-pack"
                );
        assertThat(rehearsal.rehearsalNotesMarkdown())
                .contains("# LinguaFrame Private Demo Launch Rehearsal")
                .contains("Recommended next step: openai-preflight")
                .contains("scripts/demo/private-demo-launch-rehearsal.sh");
    }

    @Test
    void blocksWhenOperationsOrRuntimeRouteIsBlocked() {
        operationsService.operations = operations("BLOCKED", List.of(
                section("Runtime contract", "BLOCKED"),
                section("Live dependencies", "READY")
        ));
        summaryService.summary = new RuntimeDependencySummaryVo(
                new RuntimeContractVo("0.0.1-SNAPSHOT", 24, List.of(
                        "/api/operator/private-demo/operations"
                )),
                new NetworkDependencyVo("mysql", "localhost", 3306),
                new NetworkDependencyVo("redis", "localhost", 6379),
                new NetworkDependencyVo("rabbitmq", "localhost", 5672),
                new StorageDependencyVo("minio", "http://localhost:9000", "linguaframe-artifacts"),
                null
        );

        PrivateDemoLaunchRehearsalVo rehearsal = service.launchRehearsal();

        assertThat(rehearsal.overallStatus()).isEqualTo("BLOCKED");
        assertThat(rehearsal.blockedCount()).isGreaterThan(0);
        assertThat(rehearsal.recommendedNextStepId()).isEqualTo("deploy-preflight");
        assertThat(rehearsal.steps())
                .filteredOn(step -> step.id().equals("deploy-preflight"))
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.status()).isEqualTo("BLOCKED");
                    assertThat(step.blocking()).isTrue();
                    assertThat(step.nextAction()).contains("Rebuild and redeploy");
                });
    }

    @Test
    void keepsLaunchRehearsalMetadataOnly() throws Exception {
        PrivateDemoLaunchRehearsalVo rehearsal = service.launchRehearsal();
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(rehearsal);

        assertThat(json)
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("private-demo-token")
                .doesNotContain("password")
                .doesNotContain("secret")
                .doesNotContain("accessKey")
                .doesNotContain("secretKey")
                .doesNotContain("job-artifacts/")
                .doesNotContain("/Users/")
                .doesNotContain("provider payload")
                .doesNotContain("raw transcript text")
                .doesNotContain("raw subtitle text");
    }

    private PrivateDemoOperationsVo operations(String overallStatus, List<PrivateDemoOperationsSectionVo> sections) {
        long ready = sections.stream().filter(section -> "READY".equals(section.status())).count();
        long attention = sections.stream().filter(section -> "ATTENTION".equals(section.status())).count();
        long blocked = sections.stream().filter(section -> "BLOCKED".equals(section.status())).count();
        return new PrivateDemoOperationsVo(
                Instant.parse("2026-06-28T00:00:00Z"),
                overallStatus,
                ready,
                attention,
                blocked,
                sections,
                List.of(new PrivateDemoOperationsCommandVo(
                        "Private demo preflight",
                        "scripts/demo/private-demo-preflight.sh",
                        "Safe preflight"
                )),
                List.of(new PrivateDemoOperationsLinkVo(
                        "Private demo deployment",
                        "docs/deployment/private-demo.md",
                        "Runbook"
                ))
        );
    }

    private PrivateDemoOperationsSectionVo section(String title, String status) {
        return new PrivateDemoOperationsSectionVo(
                title,
                status,
                List.of(new PrivateDemoOperationsCheckVo(
                        title + " check",
                        status,
                        title + " detail",
                        title + " next action"
                ))
        );
    }

    private final class StubPrivateDemoOperationsService implements PrivateDemoOperationsService {
        private PrivateDemoOperationsVo operations = PrivateDemoLaunchRehearsalServiceTests.this.operations("READY", List.of(
                section("Access gate", "READY"),
                section("Runtime contract", "READY"),
                section("Live dependencies", "READY"),
                section("Provider readiness", "READY")
        ));

        @Override
        public PrivateDemoOperationsVo operations() {
            return operations;
        }
    }

    private final class StubRuntimeDependencySummaryService implements RuntimeDependencySummaryService {
        private RuntimeDependencySummaryVo summary = new RuntimeDependencySummaryVo(
                new RuntimeContractVo("0.0.1-SNAPSHOT", 24, List.of(
                        "/api/operator/private-demo/operations",
                        "/api/operator/private-demo/launch-rehearsal"
                )),
                new NetworkDependencyVo("mysql", "localhost", 3306),
                new NetworkDependencyVo("redis", "localhost", 6379),
                new NetworkDependencyVo("rabbitmq", "localhost", 5672),
                new StorageDependencyVo("minio", "http://localhost:9000", "linguaframe-artifacts"),
                null
        );

        @Override
        public RuntimeDependencySummaryVo getSummary() {
            return summary;
        }
    }
}
