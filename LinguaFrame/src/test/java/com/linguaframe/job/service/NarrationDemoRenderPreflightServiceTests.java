package com.linguaframe.job.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.demo.domain.vo.NarrationDemoPresetMixSettingsVo;
import com.linguaframe.demo.domain.vo.NarrationDemoPresetSegmentVo;
import com.linguaframe.demo.domain.vo.NarrationDemoPresetVo;
import com.linguaframe.demo.service.NarrationDemoPresetService;
import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.dto.NarrationDemoRenderPreflightRequestDto;
import com.linguaframe.job.domain.dto.SaveNarrationSegmentsRequest;
import com.linguaframe.job.domain.dto.UpdateNarrationMixSettingsDto;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.NarrationDemoRenderPreflightVo;
import com.linguaframe.job.domain.vo.NarrationEvidenceVo;
import com.linguaframe.job.domain.vo.NarrationScriptPackageVo;
import com.linguaframe.job.domain.vo.NarrationWorkspaceVo;
import com.linguaframe.job.service.impl.NarrationDemoRenderPreflightServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class NarrationDemoRenderPreflightServiceTests {

    @Test
    void returnsReadyForDemoProviderWithReplaceConfirmationAndBaseVideo() {
        NarrationDemoRenderPreflightService service = service(
                properties("demo"),
                new StaticPresetService(Optional.of(preset("tears-showcase-narration", 4, 128))),
                new StaticWorkspaceService(workspace("job-preflight", 0)),
                new StaticScriptPackageService(scriptPackage("job-preflight", "READY", 4, 128)),
                new StaticEvidenceService(evidence("job-preflight", "READY", true, true)),
                new StaticArtifactService(List.of(artifact("burned", JobArtifactType.BURNED_VIDEO)))
        );

        NarrationDemoRenderPreflightVo result = service.preflight(
                "job-preflight",
                new NarrationDemoRenderPreflightRequestDto("tears-showcase-narration", true, true)
        );

        assertThat(result.jobId()).isEqualTo("job-preflight");
        assertThat(result.presetId()).isEqualTo("tears-showcase-narration");
        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.providerMode()).isEqualTo("demo");
        assertThat(result.paidProvider()).isFalse();
        assertThat(result.estimatedSegmentCount()).isEqualTo(4);
        assertThat(result.estimatedCharacterCount()).isEqualTo(128);
        assertThat(result.existingWorkspaceSegmentCount()).isZero();
        assertThat(result.generateNarratedVideo()).isTrue();
        assertThat(result.safeNextCommand()).isEqualTo("LINGUAFRAME_DEMO_JOB_ID=job-preflight scripts/demo/narration-demo-render.sh");
        assertThat(result.evidenceRoutes()).contains(
                "/api/jobs/job-preflight/narration-demo/render",
                "/api/jobs/job-preflight/narration-evidence"
        );
        assertThat(result.checks())
                .extracting(check -> check.key() + ":" + check.status())
                .contains(
                        "PRESET:PASS",
                        "REPLACE_CONFIRMATION:PASS",
                        "TTS_PROVIDER:PASS",
                        "NARRATED_VIDEO_INPUT:PASS"
                );
    }

    @Test
    void blocksBlankPresetId() {
        NarrationDemoRenderPreflightService service = service(
                properties("demo"),
                new StaticPresetService(Optional.empty()),
                new StaticWorkspaceService(workspace("job-preflight", 0)),
                new StaticScriptPackageService(scriptPackage("job-preflight", "BLOCKED", 0, 0)),
                new StaticEvidenceService(evidence("job-preflight", "BLOCKED", false, false)),
                new StaticArtifactService(List.of())
        );

        NarrationDemoRenderPreflightVo result = service.preflight(
                "job-preflight",
                new NarrationDemoRenderPreflightRequestDto(" ", true, true)
        );

        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.checks())
                .extracting(check -> check.key() + ":" + check.status())
                .contains("PRESET:BLOCK");
    }

    @Test
    void blocksExistingWorkspaceWhenReplaceIsNotConfirmed() {
        NarrationDemoRenderPreflightService service = service(
                properties("demo"),
                new StaticPresetService(Optional.of(preset("tears-showcase-narration", 4, 128))),
                new StaticWorkspaceService(workspace("job-preflight", 2)),
                new StaticScriptPackageService(scriptPackage("job-preflight", "READY", 2, 60)),
                new StaticEvidenceService(evidence("job-preflight", "ATTENTION", false, false)),
                new StaticArtifactService(List.of(artifact("burned", JobArtifactType.BURNED_VIDEO)))
        );

        NarrationDemoRenderPreflightVo result = service.preflight(
                "job-preflight",
                new NarrationDemoRenderPreflightRequestDto("tears-showcase-narration", false, true)
        );

        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.existingWorkspaceSegmentCount()).isEqualTo(2);
        assertThat(result.requiredConfirmations()).contains("REPLACE_EXISTING");
        assertThat(result.checks())
                .extracting(check -> check.key() + ":" + check.status())
                .contains("REPLACE_CONFIRMATION:BLOCK");
    }

    @Test
    void warnsForOpenAiTtsProviderWithoutBlockingDemo() {
        NarrationDemoRenderPreflightService service = service(
                properties("openai"),
                new StaticPresetService(Optional.of(preset("tears-showcase-narration", 4, 128))),
                new StaticWorkspaceService(workspace("job-preflight", 0)),
                new StaticScriptPackageService(scriptPackage("job-preflight", "READY", 4, 128)),
                new StaticEvidenceService(evidence("job-preflight", "ATTENTION", true, false)),
                new StaticArtifactService(List.of(artifact("burned", JobArtifactType.BURNED_VIDEO)))
        );

        NarrationDemoRenderPreflightVo result = service.preflight(
                "job-preflight",
                new NarrationDemoRenderPreflightRequestDto("tears-showcase-narration", true, true)
        );

        assertThat(result.status()).isEqualTo("ATTENTION");
        assertThat(result.providerMode()).isEqualTo("openai");
        assertThat(result.paidProvider()).isTrue();
        assertThat(result.requiredConfirmations()).contains("PAID_PROVIDER");
        assertThat(result.checks())
                .extracting(check -> check.key() + ":" + check.status())
                .contains("TTS_PROVIDER:WARN");
    }

    @Test
    void blocksNarratedVideoWhenBaseVideoIsMissing() {
        NarrationDemoRenderPreflightService service = service(
                properties("demo"),
                new StaticPresetService(Optional.of(preset("tears-showcase-narration", 4, 128))),
                new StaticWorkspaceService(workspace("job-preflight", 0)),
                new StaticScriptPackageService(scriptPackage("job-preflight", "READY", 4, 128)),
                new StaticEvidenceService(evidence("job-preflight", "ATTENTION", false, false)),
                new StaticArtifactService(List.of(artifact("audio", JobArtifactType.NARRATION_AUDIO)))
        );

        NarrationDemoRenderPreflightVo result = service.preflight(
                "job-preflight",
                new NarrationDemoRenderPreflightRequestDto("tears-showcase-narration", true, true)
        );

        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.checks())
                .extracting(check -> check.key() + ":" + check.status())
                .contains("NARRATED_VIDEO_INPUT:BLOCK");
    }

    @Test
    void skipsVideoInputCheckWhenNarratedVideoIsDisabled() {
        NarrationDemoRenderPreflightService service = service(
                properties("demo"),
                new StaticPresetService(Optional.of(preset("tears-showcase-narration", 4, 128))),
                new StaticWorkspaceService(workspace("job-preflight", 0)),
                new StaticScriptPackageService(scriptPackage("job-preflight", "READY", 4, 128)),
                new StaticEvidenceService(evidence("job-preflight", "ATTENTION", false, false)),
                new StaticArtifactService(List.of())
        );

        NarrationDemoRenderPreflightVo result = service.preflight(
                "job-preflight",
                new NarrationDemoRenderPreflightRequestDto("tears-showcase-narration", true, false)
        );

        assertThat(result.status()).isEqualTo("ATTENTION");
        assertThat(result.generateNarratedVideo()).isFalse();
        assertThat(result.checks())
                .extracting(check -> check.key() + ":" + check.status())
                .contains("NARRATED_VIDEO_INPUT:WARN");
    }

    private static NarrationDemoRenderPreflightService service(
            LinguaFrameProperties properties,
            NarrationDemoPresetService presetService,
            NarrationWorkspaceService workspaceService,
            NarrationScriptPackageService scriptPackageService,
            NarrationEvidenceService evidenceService,
            JobArtifactService artifactService
    ) {
        return new NarrationDemoRenderPreflightServiceImpl(
                properties,
                presetService,
                workspaceService,
                scriptPackageService,
                evidenceService,
                artifactService
        );
    }

    private static LinguaFrameProperties properties(String ttsProvider) {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getTts().setProvider(ttsProvider);
        return properties;
    }

    private static NarrationDemoPresetVo preset(String id, int segmentCount, int characterCount) {
        return new NarrationDemoPresetVo(
                id,
                "Tears showcase narration",
                "Reusable demo narration.",
                "tears-showcase",
                "tears-of-steel-casting",
                "zh-CN",
                "DEFAULT",
                segmentCount,
                characterCount,
                new BigDecimal("113.000"),
                new NarrationDemoPresetMixSettingsVo(new BigDecimal("0.350"), new BigDecimal("1.000"), 250),
                List.of(new NarrationDemoPresetSegmentVo(0, new BigDecimal("15.000"), new BigDecimal("28.000"), new BigDecimal("13.000"), "hidden", characterCount, null)),
                List.of("Safe summaries only.")
        );
    }

    private static NarrationWorkspaceVo workspace(String jobId, int segmentCount) {
        return new NarrationWorkspaceVo(
                jobId,
                segmentCount > 0 ? "READY" : "EMPTY",
                segmentCount,
                new BigDecimal("64.000"),
                segmentCount * 30,
                segmentCount > 0,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
    }

    private static NarrationScriptPackageVo scriptPackage(String jobId, String status, int segmentCount, int characterCount) {
        return new NarrationScriptPackageVo(
                jobId,
                "zh-CN",
                new BigDecimal("300.000"),
                status,
                segmentCount,
                characterCount,
                new BigDecimal("64.000"),
                0,
                BigDecimal.ZERO,
                false,
                "DEFAULT:demo-voice",
                "demo-voice",
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of("manifest.json"),
                List.of("No secrets.")
        );
    }

    private static NarrationEvidenceVo evidence(String jobId, String status, boolean audioReady, boolean videoReady) {
        return new NarrationEvidenceVo(
                jobId,
                status,
                4,
                128,
                new BigDecimal("64.000"),
                0,
                BigDecimal.ZERO,
                false,
                1,
                "DEFAULT:demo-voice",
                "demo-voice",
                audioReady,
                audioReady ? 1 : 0,
                audioReady ? "TIMED_AUDIO_BED" : "",
                audioReady,
                videoReady,
                videoReady ? 1 : 0,
                videoReady ? "DUCKED_ORIGINAL_AUDIO" : "",
                new BigDecimal("0.350"),
                new BigDecimal("1.000"),
                250,
                "SAVED",
                0,
                "none",
                List.of(),
                List.of(),
                List.of("manifest.json"),
                List.of("Metadata only.")
        );
    }

    private static JobArtifactVo artifact(String id, JobArtifactType type) {
        return new JobArtifactVo(
                id,
                "job-preflight",
                type,
                type.name().toLowerCase() + ".bin",
                "application/octet-stream",
                3L,
                "sha-" + id,
                false,
                null,
                Instant.parse("2026-06-30T00:00:00Z")
        );
    }

    private record StaticPresetService(Optional<NarrationDemoPresetVo> preset) implements NarrationDemoPresetService {

        @Override
        public List<NarrationDemoPresetVo> listPresets() {
            return preset.map(List::of).orElseGet(List::of);
        }

        @Override
        public Optional<NarrationDemoPresetVo> findByProfileId(String profileId) {
            return preset;
        }

        @Override
        public Optional<NarrationDemoPresetVo> findById(String presetId) {
            return preset.filter(value -> value.id().equals(presetId));
        }
    }

    private record StaticWorkspaceService(NarrationWorkspaceVo workspace) implements NarrationWorkspaceService {

        @Override
        public NarrationWorkspaceVo getWorkspace(String jobId) {
            return workspace;
        }

        @Override
        public NarrationWorkspaceVo saveWorkspace(String jobId, SaveNarrationSegmentsRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public NarrationWorkspaceVo updateMixSettings(String jobId, UpdateNarrationMixSettingsDto request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public NarrationWorkspaceVo clearWorkspace(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticScriptPackageService(NarrationScriptPackageVo scriptPackage) implements NarrationScriptPackageService {

        @Override
        public NarrationScriptPackageVo getPackage(String jobId) {
            return scriptPackage;
        }

        @Override
        public String renderMarkdown(String jobId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.linguaframe.job.domain.bo.StoredNarrationScriptPackageBo openPackage(String jobId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.linguaframe.job.domain.vo.NarrationScriptPackageImportVo importPackage(
                String jobId,
                com.linguaframe.job.domain.dto.ImportNarrationScriptPackageDto request
        ) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticEvidenceService(NarrationEvidenceVo evidence) implements NarrationEvidenceService {

        @Override
        public NarrationEvidenceVo getEvidence(String jobId) {
            return evidence;
        }

        @Override
        public String renderMarkdown(String jobId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.linguaframe.job.domain.bo.StoredNarrationEvidencePackageBo openPackage(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticArtifactService(List<JobArtifactVo> artifacts) implements JobArtifactService {

        @Override
        public JobArtifactVo createArtifact(CreateJobArtifactCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JobArtifactVo createReusedArtifact(String jobId, com.linguaframe.job.domain.entity.JobArtifactRecord source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<JobArtifactVo> listArtifacts(String jobId) {
            return artifacts;
        }

        @Override
        public StoredObjectResourceBo openArtifact(String jobId, String artifactId) {
            throw new UnsupportedOperationException();
        }
    }
}
