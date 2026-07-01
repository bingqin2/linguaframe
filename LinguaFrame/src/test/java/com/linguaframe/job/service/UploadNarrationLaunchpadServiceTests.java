package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.NarrationMixAutomationVo;
import com.linguaframe.job.domain.vo.NarrationMixSettingsVo;
import com.linguaframe.job.domain.vo.NarrationSceneBoardActionVo;
import com.linguaframe.job.domain.vo.NarrationSceneBoardCheckVo;
import com.linguaframe.job.domain.vo.NarrationSceneBoardLinkVo;
import com.linguaframe.job.domain.vo.NarrationSceneBoardSegmentVo;
import com.linguaframe.job.domain.vo.NarrationSceneBoardVo;
import com.linguaframe.job.domain.vo.NarrationTimelineSummaryVo;
import com.linguaframe.job.domain.vo.NarrationVoiceCatalogVo;
import com.linguaframe.job.domain.vo.NarrationVoicePresetVo;
import com.linguaframe.job.domain.vo.NarrationWorkspaceVo;
import com.linguaframe.job.domain.vo.UploadNarrationLaunchpadVo;
import com.linguaframe.job.service.impl.UploadNarrationLaunchpadServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UploadNarrationLaunchpadServiceTests {

    @Test
    void returnsReadyLaunchpadForSeededNarrationWorkspaceWithoutTextLeakage() {
        UploadNarrationLaunchpadService service = new UploadNarrationLaunchpadServiceImpl(
                new StubNarrationWorkspaceService(workspace(2, 54)),
                new StubNarrationSceneBoardService(sceneBoard("READY", 2, false, false)),
                new StubNarrationVoiceCatalogService(catalog())
        );

        UploadNarrationLaunchpadVo launchpad = service.getLaunchpad("job-seeded");

        assertThat(launchpad.status()).isEqualTo("READY");
        assertThat(launchpad.jobId()).isEqualTo("job-seeded");
        assertThat(launchpad.segmentCount()).isEqualTo(2);
        assertThat(launchpad.characterCount()).isEqualTo(54);
        assertThat(launchpad.voiceSummary()).isEqualTo("demo-voice: 1, inherited: 1");
        assertThat(launchpad.voiceProvider()).isEqualTo("demo");
        assertThat(launchpad.defaultVoice()).isEqualTo("demo-voice");
        assertThat(launchpad.selectedSegmentIndex()).isZero();
        assertThat(launchpad.nextAction()).contains("Preview");
        assertThat(launchpad.actions())
                .extracting("key")
                .containsExactly("open-workspace", "preview-tts", "render-preflight", "render-review");
        assertThat(launchpad.safeLinks())
                .extracting("href")
                .contains("/api/jobs/job-seeded/narration-workspace");
        assertThat(launchpad.toString())
                .doesNotContain("Private narration line")
                .doesNotContain("hidden/object/key");
    }

    @Test
    void returnsNotApplicableLaunchpadWhenNoNarrationRowsExist() {
        UploadNarrationLaunchpadService service = new UploadNarrationLaunchpadServiceImpl(
                new StubNarrationWorkspaceService(workspace(0, 0)),
                new StubNarrationSceneBoardService(sceneBoard("EMPTY", 0, false, false)),
                new StubNarrationVoiceCatalogService(catalog())
        );

        UploadNarrationLaunchpadVo launchpad = service.getLaunchpad("job-empty");

        assertThat(launchpad.status()).isEqualTo("NOT_APPLICABLE");
        assertThat(launchpad.segmentCount()).isZero();
        assertThat(launchpad.characterCount()).isZero();
        assertThat(launchpad.selectedSegmentIndex()).isNull();
        assertThat(launchpad.nextAction()).contains("Add narration rows");
        assertThat(launchpad.actions())
                .extracting("key")
                .containsExactly("open-workspace");
    }

    @Test
    void returnsBlockedLaunchpadWhenSceneBoardReportsBlockingChecks() {
        UploadNarrationLaunchpadService service = new UploadNarrationLaunchpadServiceImpl(
                new StubNarrationWorkspaceService(workspace(1, 24)),
                new StubNarrationSceneBoardService(sceneBoard("BLOCKED", 1, true, true)),
                new StubNarrationVoiceCatalogService(catalog())
        );

        UploadNarrationLaunchpadVo launchpad = service.getLaunchpad("job-blocked");

        assertThat(launchpad.status()).isEqualTo("BLOCKED");
        assertThat(launchpad.blockingIssueCount()).isEqualTo(2);
        assertThat(launchpad.attentionIssueCount()).isZero();
        assertThat(launchpad.nextAction()).contains("Fix blocked narration rows");
        assertThat(launchpad.actions())
                .extracting("key")
                .contains("open-workspace");
        assertThat(launchpad.toString()).doesNotContain("Private narration line");
    }

    private static NarrationWorkspaceVo workspace(int segmentCount, int characterCount) {
        return new NarrationWorkspaceVo(
                "job-seeded",
                segmentCount == 0 ? "EMPTY" : "READY",
                segmentCount,
                new BigDecimal(segmentCount == 0 ? "0.000" : "28.000"),
                characterCount,
                segmentCount > 0,
                new NarrationMixSettingsVo(new BigDecimal("0.350"), new BigDecimal("1.000"), 250, Instant.parse("2026-07-01T00:00:00Z")),
                new NarrationMixAutomationVo(0, 0, 0, 0, List.of(), List.of()),
                catalog(),
                new NarrationTimelineSummaryVo(
                        segmentCount == 0 ? BigDecimal.ZERO : new BigDecimal("15.000"),
                        segmentCount == 0 ? BigDecimal.ZERO : new BigDecimal("70.000"),
                        segmentCount == 0 ? BigDecimal.ZERO : new BigDecimal("55.000"),
                        segmentCount == 0 ? BigDecimal.ZERO : new BigDecimal("28.000"),
                        segmentCount == 0 ? BigDecimal.ZERO : new BigDecimal("27.000"),
                        segmentCount > 1 ? 1 : 0,
                        false,
                        segmentCount > 0,
                        List.of()
                ),
                List.of(),
                List.of("Workspace text is intentionally omitted from launchpad tests.")
        );
    }

    private static NarrationSceneBoardVo sceneBoard(String status, int segmentCount, boolean blocked, boolean includeAttention) {
        return new NarrationSceneBoardVo(
                "job-seeded",
                Instant.parse("2026-07-01T00:00:00Z"),
                status,
                segmentCount,
                segmentCount == 0 ? BigDecimal.ZERO : new BigDecimal("28.000"),
                segmentCount == 0 ? BigDecimal.ZERO : new BigDecimal("55.000"),
                segmentCount == 0 ? BigDecimal.ZERO : new BigDecimal("50.91"),
                segmentCount > 1 ? 1 : 0,
                segmentCount > 1 ? new BigDecimal("27.000") : BigDecimal.ZERO,
                false,
                segmentCount == 0 ? 0 : 1,
                0,
                0,
                false,
                false,
                segments(segmentCount, blocked),
                checks(blocked, includeAttention),
                List.of(new NarrationSceneBoardActionVo("render-preflight", "Run render preflight", "Inspect preflight before render.", "#narration-render")),
                List.of(new NarrationSceneBoardLinkVo("workspace", "/api/jobs/job-seeded/narration-workspace", "Narration workspace")),
                List.of("Scene board excludes narration text.")
        );
    }

    private static List<NarrationSceneBoardCheckVo> checks(boolean blocked, boolean includeAttention) {
        if (blocked) {
            return List.of(
                    new NarrationSceneBoardCheckVo("text", "Narration text", "BLOCKED", "At least one row has blank text."),
                    new NarrationSceneBoardCheckVo("voice", "Voice presets", "BLOCKED", "At least one row uses an unknown voice preset.")
            );
        }
        if (includeAttention) {
            return List.of(new NarrationSceneBoardCheckVo("audio", "Narration audio", "ATTENTION", "Generate audio later."));
        }
        return List.of(new NarrationSceneBoardCheckVo("text", "Narration text", "READY", "All rows include text."));
    }

    private static List<NarrationSceneBoardSegmentVo> segments(int segmentCount, boolean blocked) {
        if (segmentCount == 0) {
            return List.of();
        }
        List<NarrationSceneBoardSegmentVo> segments = new java.util.ArrayList<>();
        segments.add(new NarrationSceneBoardSegmentVo(
                0,
                new BigDecimal("15.000"),
                new BigDecimal("28.000"),
                new BigDecimal("13.000"),
                "00:15.000-00:28.000",
                "demo-voice",
                24,
                new BigDecimal("1.846"),
                "READY",
                "INHERIT",
                blocked ? "BLOCKED" : "READY"
        ));
        if (segmentCount > 1) {
            segments.add(new NarrationSceneBoardSegmentVo(
                    1,
                    new BigDecimal("55.000"),
                    new BigDecimal("70.000"),
                    new BigDecimal("15.000"),
                    "00:55.000-01:10.000",
                    "Inherit default",
                    30,
                    new BigDecimal("2.000"),
                    "READY",
                    "INHERIT",
                    "READY"
            ));
        }
        return segments;
    }

    private static NarrationVoiceCatalogVo catalog() {
        return new NarrationVoiceCatalogVo(
                "demo",
                "demo-voice",
                List.of(new NarrationVoicePresetVo("demo-voice", "Demo voice", "demo", true, "Deterministic local demo TTS voice.")),
                List.of("Voice presets are provider identifiers.")
        );
    }

    private record StubNarrationWorkspaceService(NarrationWorkspaceVo workspace) implements NarrationWorkspaceService {
        @Override
        public NarrationWorkspaceVo getWorkspace(String jobId) {
            return workspace;
        }

        @Override
        public NarrationWorkspaceVo saveWorkspace(String jobId, com.linguaframe.job.domain.dto.SaveNarrationSegmentsRequest request) {
            throw new UnsupportedOperationException("read only");
        }

        @Override
        public NarrationWorkspaceVo updateMixSettings(String jobId, com.linguaframe.job.domain.dto.UpdateNarrationMixSettingsDto request) {
            throw new UnsupportedOperationException("read only");
        }

        @Override
        public NarrationWorkspaceVo clearWorkspace(String jobId) {
            throw new UnsupportedOperationException("read only");
        }
    }

    private record StubNarrationSceneBoardService(NarrationSceneBoardVo board) implements NarrationSceneBoardService {
        @Override
        public NarrationSceneBoardVo getSceneBoard(String jobId) {
            return board;
        }

        @Override
        public String renderMarkdown(String jobId) {
            return "# Narration Scene Board";
        }
    }

    private record StubNarrationVoiceCatalogService(NarrationVoiceCatalogVo catalog) implements NarrationVoiceCatalogService {
        @Override
        public NarrationVoiceCatalogVo catalog() {
            return catalog;
        }
    }
}
