package com.linguaframe.job.service;

import com.linguaframe.demo.domain.vo.NarrationDemoPresetMixSettingsVo;
import com.linguaframe.demo.domain.vo.NarrationDemoPresetSegmentVo;
import com.linguaframe.demo.domain.vo.NarrationDemoPresetVo;
import com.linguaframe.demo.service.NarrationDemoPresetService;
import com.linguaframe.demo.service.impl.InMemoryNarrationDemoPresetService;
import com.linguaframe.job.domain.dto.ApplyNarrationDemoPresetDto;
import com.linguaframe.job.domain.entity.NarrationMixSettingsRecord;
import com.linguaframe.job.domain.entity.NarrationSegmentRecord;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.NarrationDemoPresetApplyVo;
import com.linguaframe.job.domain.vo.NarrationEvidenceVo;
import com.linguaframe.job.domain.vo.NarrationScriptPackageVo;
import com.linguaframe.job.domain.vo.NarrationVoiceCatalogVo;
import com.linguaframe.job.domain.vo.NarrationVoicePresetVo;
import com.linguaframe.job.repository.NarrationMixSettingsRepository;
import com.linguaframe.job.repository.NarrationSegmentRepository;
import com.linguaframe.job.service.impl.NarrationDemoPresetApplyServiceImpl;
import com.linguaframe.job.service.impl.NarrationScriptPackageServiceImpl;
import com.linguaframe.job.service.impl.NarrationWorkspaceServiceImpl;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.repository.VideoRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NarrationDemoPresetApplyServiceTests {

    @Test
    void appliesPresetThroughScriptPackageImportWithoutGeneratingMedia() {
        MutableNarrationSegmentRepository segmentRepository = new MutableNarrationSegmentRepository(
                List.of(segment(0, "1.000", "2.000", "Old script.", "demo-voice"))
        );
        MutableNarrationMixSettingsRepository mixSettingsRepository = new MutableNarrationMixSettingsRepository(null);
        NarrationDemoPresetApplyService service = service(
                segmentRepository,
                mixSettingsRepository,
                job("job-narration"),
                300,
                new StaticNarrationVoiceCatalogService("alloy")
        );

        NarrationDemoPresetApplyVo result = service.apply(
                "job-narration",
                new ApplyNarrationDemoPresetDto("tears-showcase-narration", true)
        );

        assertThat(result.jobId()).isEqualTo("job-narration");
        assertThat(result.presetId()).isEqualTo("tears-showcase-narration");
        assertThat(result.profileId()).isEqualTo("tears-showcase");
        assertThat(result.importedSegmentCount()).isEqualTo(4);
        assertThat(result.voiceSummary()).isEqualTo("DEFAULT:alloy");
        assertThat(result.workspace().segmentCount()).isEqualTo(4);
        assertThat(result.workspace().segments())
                .extracting(segment -> segment.index() + ":" + segment.voice())
                .containsExactly("0:null", "1:null", "2:null", "3:null");
        assertThat(result.scriptPackage().status()).isEqualTo("READY");
        assertThat(result.narrationEvidenceStatus()).isEqualTo("ATTENTION");
        assertThat(result.generatedMedia()).isFalse();
        assertThat(segmentRepository.records())
                .extracting(NarrationSegmentRecord::text)
                .doesNotContain("Old script.");
        assertThat(mixSettingsRepository.settings().duckingVolume()).isEqualByComparingTo("0.350");
    }

    @Test
    void rejectsMissingReplaceExistingWithoutChangingWorkspace() {
        assertRejectedApplyLeavesWorkspaceUnchanged(
                new ApplyNarrationDemoPresetDto("tears-showcase-narration", false),
                "Narration demo preset apply requires replaceExisting=true."
        );
    }

    @Test
    void rejectsUnknownPresetWithoutChangingWorkspace() {
        assertRejectedApplyLeavesWorkspaceUnchanged(
                new ApplyNarrationDemoPresetDto("missing-preset", true),
                "Unknown narration demo preset: missing-preset"
        );
    }

    @Test
    void rejectsPresetWhenSourceVideoIsTooShort() {
        assertRejectedApplyLeavesWorkspaceUnchanged(
                new ApplyNarrationDemoPresetDto("tears-showcase-narration", true),
                "Narration script package segment endSeconds must be within source duration.",
                60,
                new StaticNarrationVoiceCatalogService("alloy")
        );
    }

    @Test
    void rejectsPresetWhenVoiceIsNotConfigured() {
        assertRejectedApplyLeavesWorkspaceUnchanged(
                new ApplyNarrationDemoPresetDto("tears-showcase-narration", true),
                "Narration voice must be one of the configured presets.",
                300,
                new StaticNarrationVoiceCatalogService("demo-voice"),
                new InvalidVoicePresetService()
        );
    }

    private void assertRejectedApplyLeavesWorkspaceUnchanged(
            ApplyNarrationDemoPresetDto request,
            String message
    ) {
        assertRejectedApplyLeavesWorkspaceUnchanged(
                request,
                message,
                300,
                new StaticNarrationVoiceCatalogService("alloy"),
                new InMemoryNarrationDemoPresetService()
        );
    }

    private void assertRejectedApplyLeavesWorkspaceUnchanged(
            ApplyNarrationDemoPresetDto request,
            String message,
            int durationSeconds,
            NarrationVoiceCatalogService voiceCatalogService
    ) {
        assertRejectedApplyLeavesWorkspaceUnchanged(
                request,
                message,
                durationSeconds,
                voiceCatalogService,
                new InMemoryNarrationDemoPresetService()
        );
    }

    private void assertRejectedApplyLeavesWorkspaceUnchanged(
            ApplyNarrationDemoPresetDto request,
            String message,
            int durationSeconds,
            NarrationVoiceCatalogService voiceCatalogService,
            NarrationDemoPresetService presetService
    ) {
        MutableNarrationSegmentRepository segmentRepository = new MutableNarrationSegmentRepository(
                List.of(segment(0, "1.000", "2.000", "Old script.", "demo-voice"))
        );
        MutableNarrationMixSettingsRepository mixSettingsRepository = new MutableNarrationMixSettingsRepository(
                new NarrationMixSettingsRecord(
                        "job-narration",
                        new BigDecimal("0.125"),
                        new BigDecimal("1.750"),
                        400,
                        Instant.parse("2026-06-29T10:15:00Z")
                )
        );
        NarrationDemoPresetApplyService service = service(
                segmentRepository,
                mixSettingsRepository,
                job("job-narration"),
                durationSeconds,
                voiceCatalogService,
                presetService
        );

        assertThatThrownBy(() -> service.apply("job-narration", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);

        assertThat(segmentRepository.records())
                .extracting(NarrationSegmentRecord::text)
                .containsExactly("Old script.");
        assertThat(mixSettingsRepository.settings().duckingVolume()).isEqualByComparingTo("0.125");
    }

    private NarrationDemoPresetApplyService service(
            NarrationSegmentRepository segmentRepository,
            NarrationMixSettingsRepository mixSettingsRepository,
            LocalizationJobVo job,
            int durationSeconds,
            NarrationVoiceCatalogService voiceCatalogService
    ) {
        return service(
                segmentRepository,
                mixSettingsRepository,
                job,
                durationSeconds,
                voiceCatalogService,
                new InMemoryNarrationDemoPresetService()
        );
    }

    private NarrationDemoPresetApplyService service(
            NarrationSegmentRepository segmentRepository,
            NarrationMixSettingsRepository mixSettingsRepository,
            LocalizationJobVo job,
            int durationSeconds,
            NarrationVoiceCatalogService voiceCatalogService,
            NarrationDemoPresetService presetService
    ) {
        StaticLocalizationJobQueryService queryService = new StaticLocalizationJobQueryService(job);
        VideoRepository videoRepository = mock(VideoRepository.class);
        when(videoRepository.findById(job.videoId())).thenReturn(Optional.of(new VideoRecord(
                job.videoId(),
                "demo.mp4",
                "video/mp4",
                123L,
                durationSeconds,
                "source-videos/" + job.videoId() + "/demo.mp4",
                MediaUploadStatus.UPLOADED,
                Instant.parse("2026-06-29T09:00:00Z")
        )));
        NarrationWorkspaceService workspaceService = new NarrationWorkspaceServiceImpl(
                segmentRepository,
                mixSettingsRepository,
                voiceCatalogService,
                Clock.fixed(Instant.parse("2026-06-29T11:00:00Z"), ZoneOffset.UTC)
        );
        NarrationScriptPackageService scriptPackageService = new NarrationScriptPackageServiceImpl(
                segmentRepository,
                mixSettingsRepository,
                queryService,
                voiceCatalogService,
                workspaceService,
                videoRepository
        );
        return new NarrationDemoPresetApplyServiceImpl(
                presetService,
                scriptPackageService,
                workspaceService,
                new StaticNarrationEvidenceService(),
                queryService
        );
    }

    private NarrationSegmentRecord segment(int index, String start, String end, String text, String voice) {
        return new NarrationSegmentRecord(
                "narration-" + index,
                "job-narration",
                index,
                new BigDecimal(start),
                new BigDecimal(end),
                text,
                voice,
                Instant.parse("2026-06-29T10:00:00Z"),
                Instant.parse("2026-06-29T10:00:00Z")
        );
    }

    private LocalizationJobVo job(String jobId) {
        return new LocalizationJobVo(
                jobId,
                "video-" + jobId,
                "zh-CN",
                null,
                LocalizationJobStatus.COMPLETED,
                Instant.parse("2026-06-29T09:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                0,
                null,
                List.of(),
                new JobUsageSummaryVo(0, 0, 0, BigDecimal.ZERO, null, null, null, null),
                new JobCacheSummaryVo(0, 0, 0),
                List.of(),
                null,
                null,
                null
        );
    }

    private static final class MutableNarrationSegmentRepository implements NarrationSegmentRepository {

        private List<NarrationSegmentRecord> records;

        private MutableNarrationSegmentRepository(List<NarrationSegmentRecord> records) {
            this.records = new ArrayList<>(records);
        }

        @Override
        public void replaceSegments(String jobId, List<NarrationSegmentRecord> segments) {
            this.records = new ArrayList<>(segments);
        }

        @Override
        public List<NarrationSegmentRecord> findByJobId(String jobId) {
            return List.copyOf(records);
        }

        @Override
        public void deleteByJobId(String jobId) {
            this.records = List.of();
        }

        private List<NarrationSegmentRecord> records() {
            return List.copyOf(records);
        }
    }

    private static final class MutableNarrationMixSettingsRepository implements NarrationMixSettingsRepository {

        private NarrationMixSettingsRecord settings;

        private MutableNarrationMixSettingsRepository(NarrationMixSettingsRecord settings) {
            this.settings = settings;
        }

        @Override
        public Optional<NarrationMixSettingsRecord> findByJobId(String jobId) {
            return Optional.ofNullable(settings)
                    .filter(record -> record.jobId().equals(jobId));
        }

        @Override
        public NarrationMixSettingsRecord upsert(NarrationMixSettingsRecord settings) {
            this.settings = settings;
            return settings;
        }

        @Override
        public void deleteByJobId(String jobId) {
            this.settings = null;
        }

        private NarrationMixSettingsRecord settings() {
            return settings;
        }
    }

    private record StaticLocalizationJobQueryService(LocalizationJobVo job) implements LocalizationJobQueryService {

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

    private record StaticNarrationVoiceCatalogService(String allowedVoice) implements NarrationVoiceCatalogService {

        @Override
        public NarrationVoiceCatalogVo catalog() {
            return new NarrationVoiceCatalogVo(
                    "demo",
                    allowedVoice,
                    List.of(new NarrationVoicePresetVo(allowedVoice, "Allowed voice", "demo", true, "Test voice.")),
                    List.of("Voice presets are provider identifiers, not uploaded reference audio.")
            );
        }
    }

    private static final class StaticNarrationEvidenceService implements NarrationEvidenceService {

        @Override
        public NarrationEvidenceVo getEvidence(String jobId) {
            return new NarrationEvidenceVo(
                    jobId,
                    "ATTENTION",
                    4,
                    100,
                    new BigDecimal("64.000"),
                    3,
                    new BigDecimal("80.000"),
                    false,
                    1,
                    "DEFAULT:alloy",
                    "alloy",
                    false,
                    0,
                    "TIMED_BED",
                    true,
                    false,
                    0,
                    "DUCK_ORIGINAL_AUDIO",
                    new BigDecimal("0.350"),
                    new BigDecimal("1.000"),
                    250,
                    "SAVED",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );
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

    private static final class InvalidVoicePresetService implements NarrationDemoPresetService {

        @Override
        public List<NarrationDemoPresetVo> listPresets() {
            return List.of(preset());
        }

        @Override
        public Optional<NarrationDemoPresetVo> findByProfileId(String profileId) {
            return Optional.empty();
        }

        @Override
        public Optional<NarrationDemoPresetVo> findById(String presetId) {
            return "tears-showcase-narration".equals(presetId) ? Optional.of(preset()) : Optional.empty();
        }

        private NarrationDemoPresetVo preset() {
            return new NarrationDemoPresetVo(
                    "tears-showcase-narration",
                    "Invalid voice preset",
                    "Test preset with a missing voice.",
                    "tears-showcase",
                    "tears-of-steel-casting",
                    "zh-CN",
                    "PRESET:missing-voice",
                    1,
                    16,
                    new BigDecimal("13.000"),
                    new NarrationDemoPresetMixSettingsVo(new BigDecimal("0.350"), new BigDecimal("1.000"), 250),
                    List.of(new NarrationDemoPresetSegmentVo(
                            0,
                            new BigDecimal("15.000"),
                            new BigDecimal("28.000"),
                            new BigDecimal("13.000"),
                            "Invalid voice row",
                            16,
                            "missing-voice"
                    )),
                    List.of("Test only.")
            );
        }
    }
}
