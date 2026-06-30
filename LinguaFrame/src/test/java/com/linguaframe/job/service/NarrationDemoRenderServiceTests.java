package com.linguaframe.job.service;

import com.linguaframe.job.domain.dto.ApplyNarrationDemoPresetDto;
import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.dto.RenderNarrationDemoDto;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.NarratedVideoGenerationVo;
import com.linguaframe.job.domain.vo.NarrationDemoPresetApplyVo;
import com.linguaframe.job.domain.vo.NarrationDemoRenderVo;
import com.linguaframe.job.domain.vo.NarrationEvidenceVo;
import com.linguaframe.job.domain.vo.NarrationGenerationVo;
import com.linguaframe.job.domain.vo.NarrationScriptPackageVo;
import com.linguaframe.job.domain.vo.NarrationWorkspaceVo;
import com.linguaframe.job.service.impl.NarrationDemoRenderServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NarrationDemoRenderServiceTests {

    @Test
    void rendersPresetAudioAndNarratedVideoInOrder() {
        RecordingPresetApplyService presetApplyService = new RecordingPresetApplyService();
        RecordingNarrationAudioService audioService = new RecordingNarrationAudioService();
        RecordingNarratedVideoService videoService = new RecordingNarratedVideoService(false);
        StaticNarrationScriptPackageService scriptPackageService = new StaticNarrationScriptPackageService();
        StaticNarrationEvidenceService evidenceService = new StaticNarrationEvidenceService(true, true);
        StaticJobArtifactService artifactService = new StaticJobArtifactService(List.of(
                artifact("audio-artifact", JobArtifactType.NARRATION_AUDIO),
                artifact("video-artifact", JobArtifactType.NARRATED_VIDEO)
        ));
        NarrationDemoRenderService service = service(
                presetApplyService,
                audioService,
                videoService,
                scriptPackageService,
                evidenceService,
                artifactService
        );

        NarrationDemoRenderVo result = service.render(
                "job-render",
                new RenderNarrationDemoDto("tears-showcase-narration", true, true)
        );

        assertThat(result.jobId()).isEqualTo("job-render");
        assertThat(result.presetId()).isEqualTo("tears-showcase-narration");
        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.presetApply().importedSegmentCount()).isEqualTo(4);
        assertThat(result.narrationAudio().filename()).isEqualTo("narration-audio.mp3");
        assertThat(result.narratedVideo().filename()).isEqualTo("narrated-video.mp4");
        assertThat(result.scriptPackage().status()).isEqualTo("READY");
        assertThat(result.narrationEvidence().status()).isEqualTo("READY");
        assertThat(result.generatedArtifactCount()).isEqualTo(2);
        assertThat(result.steps())
                .extracting(step -> step.key() + ":" + step.status())
                .containsExactly(
                        "PRESET_APPLY:SUCCEEDED",
                        "NARRATION_AUDIO:SUCCEEDED",
                        "NARRATED_VIDEO:SUCCEEDED",
                        "SCRIPT_PACKAGE:SUCCEEDED",
                        "NARRATION_EVIDENCE:SUCCEEDED"
                );
        assertThat(presetApplyService.calls()).containsExactly("job-render:tears-showcase-narration:true");
        assertThat(audioService.calls()).containsExactly("job-render");
        assertThat(videoService.calls()).containsExactly("job-render");
    }

    @Test
    void rejectsMissingReplaceExistingBeforeGeneratingMedia() {
        RecordingPresetApplyService presetApplyService = new RecordingPresetApplyService();
        RecordingNarrationAudioService audioService = new RecordingNarrationAudioService();
        RecordingNarratedVideoService videoService = new RecordingNarratedVideoService(false);
        NarrationDemoRenderService service = service(
                presetApplyService,
                audioService,
                videoService,
                new StaticNarrationScriptPackageService(),
                new StaticNarrationEvidenceService(false, false),
                new StaticJobArtifactService(List.of())
        );

        assertThatThrownBy(() -> service.render(
                "job-render",
                new RenderNarrationDemoDto("tears-showcase-narration", false, true)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Narration demo render requires replaceExisting=true.");

        assertThat(presetApplyService.calls()).isEmpty();
        assertThat(audioService.calls()).isEmpty();
        assertThat(videoService.calls()).isEmpty();
    }

    @Test
    void returnsFailedWhenAudioGenerationFailsBeforeVideo() {
        RecordingNarrationAudioService audioService = new RecordingNarrationAudioService();
        audioService.failWith(new IllegalArgumentException("No narration segments are available."));
        RecordingNarratedVideoService videoService = new RecordingNarratedVideoService(false);
        NarrationDemoRenderService service = service(
                new RecordingPresetApplyService(),
                audioService,
                videoService,
                new StaticNarrationScriptPackageService(),
                new StaticNarrationEvidenceService(false, false),
                new StaticJobArtifactService(List.of())
        );

        NarrationDemoRenderVo result = service.render(
                "job-render",
                new RenderNarrationDemoDto("tears-showcase-narration", true, true)
        );

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.narrationAudio()).isNull();
        assertThat(result.narratedVideo()).isNull();
        assertThat(result.generatedArtifactCount()).isZero();
        assertThat(result.steps())
                .extracting(step -> step.key() + ":" + step.status())
                .containsExactly(
                        "PRESET_APPLY:SUCCEEDED",
                        "NARRATION_AUDIO:FAILED",
                        "NARRATED_VIDEO:SKIPPED",
                        "SCRIPT_PACKAGE:SUCCEEDED",
                        "NARRATION_EVIDENCE:SUCCEEDED"
                );
        assertThat(result.steps().get(1).message()).isEqualTo("No narration segments are available.");
        assertThat(videoService.calls()).isEmpty();
    }

    @Test
    void returnsPartialWhenVideoGenerationFailsAfterAudioSuccess() {
        RecordingNarratedVideoService videoService = new RecordingNarratedVideoService(true);
        NarrationDemoRenderService service = service(
                new RecordingPresetApplyService(),
                new RecordingNarrationAudioService(),
                videoService,
                new StaticNarrationScriptPackageService(),
                new StaticNarrationEvidenceService(true, false),
                new StaticJobArtifactService(List.of(artifact("audio-artifact", JobArtifactType.NARRATION_AUDIO)))
        );

        NarrationDemoRenderVo result = service.render(
                "job-render",
                new RenderNarrationDemoDto("tears-showcase-narration", true, true)
        );

        assertThat(result.status()).isEqualTo("PARTIAL");
        assertThat(result.narrationAudio().artifactId()).isEqualTo("audio-artifact");
        assertThat(result.narratedVideo()).isNull();
        assertThat(result.generatedArtifactCount()).isEqualTo(1);
        assertThat(result.narrationEvidence().narrationAudioReady()).isTrue();
        assertThat(result.narrationEvidence().narratedVideoReady()).isFalse();
        assertThat(result.steps())
                .extracting(step -> step.key() + ":" + step.status())
                .containsExactly(
                        "PRESET_APPLY:SUCCEEDED",
                        "NARRATION_AUDIO:SUCCEEDED",
                        "NARRATED_VIDEO:FAILED",
                        "SCRIPT_PACKAGE:SUCCEEDED",
                        "NARRATION_EVIDENCE:SUCCEEDED"
                );
    }

    @Test
    void skipsVideoGenerationWhenRequestDisablesIt() {
        RecordingNarratedVideoService videoService = new RecordingNarratedVideoService(false);
        NarrationDemoRenderService service = service(
                new RecordingPresetApplyService(),
                new RecordingNarrationAudioService(),
                videoService,
                new StaticNarrationScriptPackageService(),
                new StaticNarrationEvidenceService(true, false),
                new StaticJobArtifactService(List.of(artifact("audio-artifact", JobArtifactType.NARRATION_AUDIO)))
        );

        NarrationDemoRenderVo result = service.render(
                "job-render",
                new RenderNarrationDemoDto("tears-showcase-narration", true, false)
        );

        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.narrationAudio().filename()).isEqualTo("narration-audio.mp3");
        assertThat(result.narratedVideo()).isNull();
        assertThat(result.generatedArtifactCount()).isEqualTo(1);
        assertThat(result.steps())
                .extracting(step -> step.key() + ":" + step.status())
                .containsExactly(
                        "PRESET_APPLY:SUCCEEDED",
                        "NARRATION_AUDIO:SUCCEEDED",
                        "NARRATED_VIDEO:SKIPPED",
                        "SCRIPT_PACKAGE:SUCCEEDED",
                        "NARRATION_EVIDENCE:SUCCEEDED"
                );
        assertThat(videoService.calls()).isEmpty();
    }

    private NarrationDemoRenderService service(
            NarrationDemoPresetApplyService presetApplyService,
            NarrationAudioService audioService,
            NarratedVideoService videoService,
            NarrationScriptPackageService scriptPackageService,
            NarrationEvidenceService evidenceService,
            JobArtifactService artifactService
    ) {
        return new NarrationDemoRenderServiceImpl(
                presetApplyService,
                audioService,
                videoService,
                scriptPackageService,
                evidenceService,
                artifactService
        );
    }

    private static JobArtifactVo artifact(String id, JobArtifactType type) {
        return new JobArtifactVo(
                id,
                "job-render",
                type,
                type == JobArtifactType.NARRATED_VIDEO ? "narrated-video.mp4" : "narration-audio.mp3",
                type == JobArtifactType.NARRATED_VIDEO ? "video/mp4" : "audio/mpeg",
                3L,
                "sha-" + id,
                false,
                null,
                Instant.parse("2026-06-30T00:00:00Z")
        );
    }

    private static NarrationDemoPresetApplyVo applyResult(String jobId, String presetId) {
        return new NarrationDemoPresetApplyVo(
                jobId,
                presetId,
                "tears-showcase",
                4,
                120,
                "DEFAULT:demo-voice",
                true,
                false,
                workspace(jobId),
                scriptPackage(jobId),
                "ATTENTION"
        );
    }

    private static NarrationWorkspaceVo workspace(String jobId) {
        return new NarrationWorkspaceVo(
                jobId,
                "READY",
                4,
                new BigDecimal("64.000"),
                120,
                true,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
    }

    private static NarrationScriptPackageVo scriptPackage(String jobId) {
        return new NarrationScriptPackageVo(
                jobId,
                "zh-CN",
                new BigDecimal("300.000"),
                "READY",
                4,
                120,
                new BigDecimal("64.000"),
                3,
                new BigDecimal("80.000"),
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

    private static NarrationEvidenceVo evidence(String jobId, boolean audioReady, boolean videoReady) {
        return new NarrationEvidenceVo(
                jobId,
                audioReady && videoReady ? "READY" : "ATTENTION",
                4,
                120,
                new BigDecimal("64.000"),
                3,
                new BigDecimal("80.000"),
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

    private static NarrationGenerationVo audioResult(String jobId) {
        return new NarrationGenerationVo(
                jobId,
                "audio-artifact",
                "narration-audio.mp3",
                "audio/mpeg",
                3L,
                4,
                120,
                new BigDecimal("64.000"),
                "DEFAULT:demo-voice",
                "TIMED_AUDIO_BED",
                true,
                4,
                "READY"
        );
    }

    private static NarratedVideoGenerationVo videoResult(String jobId) {
        return new NarratedVideoGenerationVo(
                jobId,
                "video-artifact",
                "narrated-video.mp4",
                "video/mp4",
                3L,
                "BURNED_VIDEO",
                "audio-artifact",
                "DUCKED_ORIGINAL_AUDIO",
                new BigDecimal("0.350"),
                new BigDecimal("1.000"),
                250,
                4,
                "READY"
        );
    }

    private static final class RecordingPresetApplyService implements NarrationDemoPresetApplyService {

        private final List<String> calls = new ArrayList<>();

        @Override
        public NarrationDemoPresetApplyVo apply(String jobId, ApplyNarrationDemoPresetDto request) {
            calls.add(jobId + ":" + request.presetId() + ":" + request.replaceExisting());
            return applyResult(jobId, request.presetId());
        }

        private List<String> calls() {
            return List.copyOf(calls);
        }
    }

    private static final class RecordingNarrationAudioService implements NarrationAudioService {

        private final List<String> calls = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public NarrationGenerationVo generateAudio(String jobId) {
            calls.add(jobId);
            if (failure != null) {
                throw failure;
            }
            return audioResult(jobId);
        }

        private void failWith(RuntimeException failure) {
            this.failure = failure;
        }

        private List<String> calls() {
            return List.copyOf(calls);
        }
    }

    private static final class RecordingNarratedVideoService implements NarratedVideoService {

        private final boolean fail;
        private final List<String> calls = new ArrayList<>();

        private RecordingNarratedVideoService(boolean fail) {
            this.fail = fail;
        }

        @Override
        public NarratedVideoGenerationVo generateVideo(String jobId) {
            calls.add(jobId);
            if (fail) {
                throw new IllegalArgumentException("Narration audio or a usable base video is missing.");
            }
            return videoResult(jobId);
        }

        private List<String> calls() {
            return List.copyOf(calls);
        }
    }

    private static final class StaticNarrationScriptPackageService implements NarrationScriptPackageService {

        @Override
        public NarrationScriptPackageVo getPackage(String jobId) {
            return scriptPackage(jobId);
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

    private static final class StaticNarrationEvidenceService implements NarrationEvidenceService {

        private final boolean audioReady;
        private final boolean videoReady;

        private StaticNarrationEvidenceService(boolean audioReady, boolean videoReady) {
            this.audioReady = audioReady;
            this.videoReady = videoReady;
        }

        @Override
        public NarrationEvidenceVo getEvidence(String jobId) {
            return evidence(jobId, audioReady, videoReady);
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

    private record StaticJobArtifactService(List<JobArtifactVo> artifacts) implements JobArtifactService {

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
