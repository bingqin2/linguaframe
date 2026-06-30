package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.StoredNarrationScriptPackageBo;
import com.linguaframe.job.domain.dto.ImportNarrationScriptPackageDto;
import com.linguaframe.job.domain.dto.ImportNarrationMixKeyframeDto;
import com.linguaframe.job.domain.dto.ImportNarrationScriptPackageSegmentDto;
import com.linguaframe.job.domain.dto.UpdateNarrationMixSettingsDto;
import com.linguaframe.job.domain.entity.NarrationMixKeyframeRecord;
import com.linguaframe.job.domain.entity.NarrationMixSettingsRecord;
import com.linguaframe.job.domain.entity.NarrationSegmentRecord;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.NarrationMixLane;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.NarrationScriptPackageVo;
import com.linguaframe.job.domain.vo.NarrationScriptPackageImportVo;
import com.linguaframe.job.domain.vo.NarrationVoiceCatalogVo;
import com.linguaframe.job.domain.vo.NarrationVoicePresetVo;
import com.linguaframe.job.repository.NarrationMixKeyframeRepository;
import com.linguaframe.job.repository.NarrationMixSettingsRepository;
import com.linguaframe.job.repository.NarrationSegmentRepository;
import com.linguaframe.job.service.impl.NarrationScriptPackageServiceImpl;
import com.linguaframe.job.service.impl.NarrationWorkspaceServiceImpl;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.repository.VideoRepository;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NarrationScriptPackageServiceTests {

    @Test
    void exportsBlockedPackageForEmptyWorkspaceWithoutScriptText() throws Exception {
        NarrationScriptPackageService service = service(List.of(), null, List.of(), job("job-empty"));

        NarrationScriptPackageVo scriptPackage = service.getPackage("job-empty");

        assertThat(scriptPackage.jobId()).isEqualTo("job-empty");
        assertThat(scriptPackage.targetLanguage()).isEqualTo("zh-CN");
        assertThat(scriptPackage.durationSeconds()).isNull();
        assertThat(scriptPackage.status()).isEqualTo("BLOCKED");
        assertThat(scriptPackage.segmentCount()).isZero();
        assertThat(scriptPackage.totalCharacterCount()).isZero();
        assertThat(scriptPackage.voiceSummary()).isEqualTo("DEFAULT:demo-voice");
        assertThat(scriptPackage.defaultVoice()).isEqualTo("demo-voice");
        assertThat(scriptPackage.segments()).isEmpty();
        assertThat(scriptPackage.checks())
                .extracting(check -> check.key() + ":" + check.status())
                .contains("SCRIPT_SEGMENTS:BLOCKED");
        assertThat(scriptPackage.safeLinks())
                .extracting(link -> link.href())
                .contains("/api/jobs/job-empty/narration-script-package/download");
        assertThat(scriptPackage.packageEntries())
                .contains("manifest.json", "narration-script-package.json", "narration-script-package.md", "README.md");

        String markdown = service.renderMarkdown("job-empty");
        assertThat(markdown)
                .contains("# Narration Script Package")
                .contains("- Status: BLOCKED")
                .contains("- Segment count: 0")
                .contains("- Includes narration text bodies: true")
                .doesNotContain("source-videos/")
                .doesNotContain("/Users/")
                .doesNotContain("provider request payload")
                .doesNotContain("sk-");

        StoredNarrationScriptPackageBo packageBo = service.openPackage("job-empty");
        assertThat(packageBo.filename()).isEqualTo("linguaframe-job-job-empty-narration-script-package.zip");
        assertThat(packageBo.contentType()).isEqualTo("application/zip");
        assertThat(zipEntries(packageBo.inputStream()))
                .containsExactlyInAnyOrder(
                        "manifest.json",
                        "narration-script-package.json",
                        "narration-script-package.md",
                        "README.md"
                );
    }

    @Test
    void exportsReadyPackageWithSegmentsVoicesGapsAndSavedMixSettings() throws Exception {
        NarrationScriptPackageService service = service(segments(), savedMixSettings(), mixKeyframes(), job("job-narration"));

        NarrationScriptPackageVo scriptPackage = service.getPackage("job-narration");

        assertThat(scriptPackage.status()).isEqualTo("READY");
        assertThat(scriptPackage.durationSeconds()).isNull();
        assertThat(scriptPackage.segmentCount()).isEqualTo(2);
        assertThat(scriptPackage.totalCharacterCount()).isEqualTo(49);
        assertThat(scriptPackage.totalTimelineDurationSeconds()).isEqualByComparingTo("28.500");
        assertThat(scriptPackage.timelineGapCount()).isEqualTo(1);
        assertThat(scriptPackage.timelineGapSeconds()).isEqualByComparingTo("27.000");
        assertThat(scriptPackage.timelineHasOverlap()).isFalse();
        assertThat(scriptPackage.voiceSummary()).isEqualTo("PRESET:demo-voice");
        assertThat(scriptPackage.defaultVoice()).isEqualTo("demo-voice");
        assertThat(scriptPackage.mixSettings().duckingVolume()).isEqualByComparingTo("0.125");
        assertThat(scriptPackage.mixSettings().narrationVolume()).isEqualByComparingTo("1.750");
        assertThat(scriptPackage.mixSettings().fadeDurationMs()).isEqualTo(400);
        assertThat(scriptPackage.mixKeyframes())
                .extracting(keyframe -> keyframe.lane() + ":" + keyframe.timeSeconds() + ":" + keyframe.value())
                .containsExactly(
                        "DUCKING_VOLUME:0.000:0.600",
                        "DUCKING_VOLUME:20.000:0.250",
                        "NARRATION_VOLUME:20.000:1.400",
                        "FADE_DURATION_MS:20.000:500.000"
                );
        assertThat(scriptPackage.segments())
                .extracting(segment -> segment.index() + ":" + segment.startSeconds() + ":" + segment.endSeconds() + ":" + segment.durationSeconds() + ":" + segment.voice() + ":" + segment.duckingVolume() + ":" + segment.narrationVolume() + ":" + segment.fadeDurationMs() + ":" + segment.text())
                .containsExactly(
                        "0:15.000:28.000:13.000:demo-voice:0.250:1.500:125:Explain the first scene.",
                        "1:55.000:70.500:15.500:demo-voice:null:null:null:Explain the second scene."
                );
        assertThat(scriptPackage.checks())
                .extracting(check -> check.key() + ":" + check.status())
                .contains("SCRIPT_SEGMENTS:READY", "SCRIPT_TIMELINE:READY", "VOICE_PRESETS:READY");
        assertThat(scriptPackage.safetyNotes())
                .anySatisfy(note -> assertThat(note).contains("operator-authored narration script"));

        String markdown = service.renderMarkdown("job-narration");
        assertThat(markdown)
                .contains("Explain the first scene.")
                .contains("Explain the second scene.")
                .contains("ducking=0.250")
                .contains("ducking=INHERIT_MIX")
                .contains("- Voice summary: PRESET:demo-voice")
                .contains("- Timeline gap count: 1")
                .contains("- Mix keyframe count: 4")
                .contains("- Mix keyframe lane summary: DUCKING_VOLUME=2,NARRATION_VOLUME=1,FADE_DURATION_MS=1")
                .doesNotContain("source-videos/")
                .doesNotContain("/Users/")
                .doesNotContain("provider request payload")
                .doesNotContain("sk-");

        String packageText = zipText(service.openPackage("job-narration").inputStream());
        assertThat(packageText)
                .contains("\"jobId\":\"job-narration\"")
                .contains("\"includesNarrationTextBodies\":true")
                .contains("\"voiceSummary\":\"PRESET:demo-voice\"")
                .contains("\"timelineGapCount\":1")
                .contains("\"duckingVolume\":0.250")
                .contains("\"narrationVolume\":1.500")
                .contains("\"fadeDurationMs\":125")
                .contains("\"mixKeyframes\"")
                .contains("\"lane\":\"DUCKING_VOLUME\"")
                .contains("\"value\":0.600")
                .contains("Explain the first scene.")
                .doesNotContain("source-videos/")
                .doesNotContain("/Users/")
                .doesNotContain("provider request payload")
                .doesNotContain("sk-");
    }

    @Test
    void importsPackageReplacingExistingWorkspaceAndMixSettings() {
        MutableNarrationSegmentRepository segmentRepository = new MutableNarrationSegmentRepository(
                List.of(segment(0, "1.000", "2.000", "Old script.", "demo-voice"))
        );
        MutableNarrationMixSettingsRepository mixSettingsRepository = new MutableNarrationMixSettingsRepository(savedMixSettings());
        MutableNarrationMixKeyframeRepository mixKeyframeRepository = new MutableNarrationMixKeyframeRepository(List.of(
                keyframe(NarrationMixLane.DUCKING_VOLUME, "0.000", "0.900")
        ));
        NarrationScriptPackageService service = importService(
                segmentRepository,
                mixSettingsRepository,
                mixKeyframeRepository,
                job("job-narration"),
                90
        );

        NarrationScriptPackageImportVo result = service.importPackage("job-narration", validImportRequest());

        assertThat(result.jobId()).isEqualTo("job-narration");
        assertThat(result.importedSegmentCount()).isEqualTo(2);
        assertThat(result.totalCharacterCount()).isEqualTo(49);
        assertThat(result.voiceSummary()).isEqualTo("PRESET:demo-voice");
        assertThat(result.replacedExisting()).isTrue();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.workspace().segments())
                .extracting(segment -> segment.index() + ":" + segment.startSeconds() + ":" + segment.endSeconds() + ":" + segment.text() + ":" + segment.duckingVolume() + ":" + segment.narrationVolume() + ":" + segment.fadeDurationMs())
                .containsExactly(
                        "0:15.000:28.000:Explain the first scene.:0.250:1.500:125",
                        "1:55.000:70.500:Explain the second scene.:null:null:null"
                );
        assertThat(result.workspace().mixSettings().duckingVolume()).isEqualByComparingTo("0.125");
        assertThat(result.workspace().mixSettings().narrationVolume()).isEqualByComparingTo("1.750");
        assertThat(result.workspace().mixSettings().fadeDurationMs()).isEqualTo(400);
        assertThat(result.workspace().mixAutomation().keyframeCount()).isEqualTo(4);
        assertThat(segmentRepository.records())
                .extracting(record -> record.text() + ":" + record.duckingVolume() + ":" + record.narrationVolume() + ":" + record.fadeDurationMs())
                .containsExactly("Explain the first scene.:0.250:1.500:125", "Explain the second scene.:null:null:null")
                .doesNotContain("Old script.:null:null:null");
        assertThat(mixSettingsRepository.settings().duckingVolume()).isEqualByComparingTo("0.125");
        assertThat(mixKeyframeRepository.records())
                .extracting(record -> record.lane() + ":" + record.timeSeconds() + ":" + record.value())
                .containsExactly(
                        "DUCKING_VOLUME:0.000:0.600",
                        "DUCKING_VOLUME:20.000:0.250",
                        "NARRATION_VOLUME:20.000:1.400",
                        "FADE_DURATION_MS:20.000:500.000"
                );
    }

    @Test
    void rejectsInvalidImportWithoutReplacingExistingWorkspace() {
        assertRejectedImportLeavesWorkspaceUnchanged(
                new ImportNarrationScriptPackageDto(false, validImportSegments(), validMixSettings(), validImportKeyframes()),
                "Narration script package import requires replaceExisting=true."
        );
        assertRejectedImportLeavesWorkspaceUnchanged(
                new ImportNarrationScriptPackageDto(true, List.of(
                        importSegment(0, "15.000", "28.000", "Explain the first scene.", "demo-voice"),
                        importSegment(1, "20.000", "30.000", "Explain the second scene.", "demo-voice")
                ), validMixSettings(), validImportKeyframes()),
                "Narration script package segments must not overlap."
        );
        assertRejectedImportLeavesWorkspaceUnchanged(
                new ImportNarrationScriptPackageDto(true, List.of(
                        importSegment(0, "15.000", "91.000", "Explain the first scene.", "demo-voice")
                ), validMixSettings(), validImportKeyframes()),
                "Narration script package segment endSeconds must be within source duration."
        );
        assertRejectedImportLeavesWorkspaceUnchanged(
                new ImportNarrationScriptPackageDto(true, List.of(
                        importSegment(0, "15.000", "28.000", "Explain the first scene.", "missing-voice")
                ), validMixSettings(), validImportKeyframes()),
                "Narration voice must be one of the configured presets."
        );
        assertRejectedImportLeavesWorkspaceUnchanged(
                new ImportNarrationScriptPackageDto(true, List.of(
                        importSegment(0, "28.000", "15.000", "Explain the first scene.", "demo-voice")
                ), validMixSettings(), validImportKeyframes()),
                "Narration endSeconds must be greater than startSeconds."
        );
        assertRejectedImportLeavesWorkspaceUnchanged(
                new ImportNarrationScriptPackageDto(true, List.of(
                        importSegment(0, "15.000", "28.000", " ", "demo-voice")
                ), validMixSettings(), validImportKeyframes()),
                "Narration text must not be blank."
        );
        assertRejectedImportLeavesWorkspaceUnchanged(
                new ImportNarrationScriptPackageDto(true, List.of(
                        importSegment(0, "15.000", "28.000", "x".repeat(1001), "demo-voice")
                ), validMixSettings(), validImportKeyframes()),
                "Narration text must be at most 1000 characters."
        );
        assertRejectedImportLeavesWorkspaceUnchanged(
                new ImportNarrationScriptPackageDto(true, validImportSegments(),
                        new UpdateNarrationMixSettingsDto(new BigDecimal("1.001"), new BigDecimal("1.000"), 250), validImportKeyframes()),
                "duckingVolume must be between 0.00 and 1.00."
        );
        assertRejectedImportLeavesWorkspaceUnchanged(
                new ImportNarrationScriptPackageDto(true, List.of(
                        importSegment(0, "15.000", "28.000", "Explain the first scene.", "demo-voice", "1.001", null, null)
                ), validMixSettings(), validImportKeyframes()),
                "duckingVolume must be between 0.00 and 1.00."
        );
        assertRejectedImportLeavesWorkspaceUnchanged(
                new ImportNarrationScriptPackageDto(true, validImportSegments(), validMixSettings(), List.of(
                        new ImportNarrationMixKeyframeDto(NarrationMixLane.DUCKING_VOLUME, new BigDecimal("1.000"), new BigDecimal("0.500")),
                        new ImportNarrationMixKeyframeDto(NarrationMixLane.DUCKING_VOLUME, new BigDecimal("1.000"), new BigDecimal("0.250"))
                )),
                "Narration mix keyframes must not duplicate lane and timeSeconds."
        );
    }

    private NarrationScriptPackageService service(
            List<NarrationSegmentRecord> segments,
            NarrationMixSettingsRecord settings,
            List<NarrationMixKeyframeRecord> keyframes,
            LocalizationJobVo job
    ) {
        return new NarrationScriptPackageServiceImpl(
                new StaticNarrationSegmentRepository(segments),
                new StaticNarrationMixSettingsRepository(settings),
                new StaticNarrationMixKeyframeRepository(keyframes),
                new StaticLocalizationJobQueryService(job),
                new StaticNarrationVoiceCatalogService()
        );
    }

    private NarrationScriptPackageService importService(
            NarrationSegmentRepository segmentRepository,
            NarrationMixSettingsRepository mixSettingsRepository,
            NarrationMixKeyframeRepository mixKeyframeRepository,
            LocalizationJobVo job,
            Integer durationSeconds
    ) {
        StaticNarrationVoiceCatalogService voiceCatalogService = new StaticNarrationVoiceCatalogService();
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
        return new NarrationScriptPackageServiceImpl(
                segmentRepository,
                mixSettingsRepository,
                mixKeyframeRepository,
                new StaticLocalizationJobQueryService(job),
                voiceCatalogService,
                new NarrationWorkspaceServiceImpl(
                        segmentRepository,
                        mixSettingsRepository,
                        mixKeyframeRepository,
                        voiceCatalogService,
                        Clock.fixed(Instant.parse("2026-06-29T11:00:00Z"), ZoneOffset.UTC)
                ),
                videoRepository
        );
    }

    private void assertRejectedImportLeavesWorkspaceUnchanged(
            ImportNarrationScriptPackageDto request,
            String message
    ) {
        MutableNarrationSegmentRepository segmentRepository = new MutableNarrationSegmentRepository(
                List.of(segment(0, "1.000", "2.000", "Old script.", "demo-voice"))
        );
        MutableNarrationMixSettingsRepository mixSettingsRepository = new MutableNarrationMixSettingsRepository(savedMixSettings());
        MutableNarrationMixKeyframeRepository mixKeyframeRepository = new MutableNarrationMixKeyframeRepository(List.of(keyframe(NarrationMixLane.DUCKING_VOLUME, "0.000", "0.900")));
        NarrationScriptPackageService service = importService(
                segmentRepository,
                mixSettingsRepository,
                mixKeyframeRepository,
                job("job-narration"),
                90
        );

        assertThatThrownBy(() -> service.importPackage("job-narration", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);

        assertThat(segmentRepository.records())
                .extracting(NarrationSegmentRecord::text)
                .containsExactly("Old script.");
        assertThat(mixSettingsRepository.settings().duckingVolume()).isEqualByComparingTo("0.125");
        assertThat(mixKeyframeRepository.records())
                .extracting(NarrationMixKeyframeRecord::value)
                .containsExactly(new BigDecimal("0.900"));
    }

    private ImportNarrationScriptPackageDto validImportRequest() {
        return new ImportNarrationScriptPackageDto(true, validImportSegments(), validMixSettings(), validImportKeyframes());
    }

    private List<ImportNarrationMixKeyframeDto> validImportKeyframes() {
        return List.of(
                new ImportNarrationMixKeyframeDto(NarrationMixLane.DUCKING_VOLUME, new BigDecimal("0.000"), new BigDecimal("0.600")),
                new ImportNarrationMixKeyframeDto(NarrationMixLane.DUCKING_VOLUME, new BigDecimal("20.000"), new BigDecimal("0.250")),
                new ImportNarrationMixKeyframeDto(NarrationMixLane.NARRATION_VOLUME, new BigDecimal("20.000"), new BigDecimal("1.400")),
                new ImportNarrationMixKeyframeDto(NarrationMixLane.FADE_DURATION_MS, new BigDecimal("20.000"), new BigDecimal("500.000"))
        );
    }

    private List<ImportNarrationScriptPackageSegmentDto> validImportSegments() {
        return List.of(
                importSegment(0, "15.000", "28.000", "Explain the first scene.", "demo-voice", "0.250", "1.500", 125),
                importSegment(1, "55.000", "70.500", "Explain the second scene.", "demo-voice")
        );
    }

    private ImportNarrationScriptPackageSegmentDto importSegment(
            int index,
            String start,
            String end,
            String text,
            String voice
    ) {
        return new ImportNarrationScriptPackageSegmentDto(
                index,
                new BigDecimal(start),
                new BigDecimal(end),
                text,
                voice
        );
    }

    private ImportNarrationScriptPackageSegmentDto importSegment(
            int index,
            String start,
            String end,
            String text,
            String voice,
            String duckingVolume,
            String narrationVolume,
            Integer fadeDurationMs
    ) {
        return new ImportNarrationScriptPackageSegmentDto(
                index,
                new BigDecimal(start),
                new BigDecimal(end),
                text,
                voice,
                duckingVolume == null ? null : new BigDecimal(duckingVolume),
                narrationVolume == null ? null : new BigDecimal(narrationVolume),
                fadeDurationMs
        );
    }

    private UpdateNarrationMixSettingsDto validMixSettings() {
        return new UpdateNarrationMixSettingsDto(new BigDecimal("0.125"), new BigDecimal("1.750"), 400);
    }

    private List<NarrationSegmentRecord> segments() {
        return List.of(
                segment(0, "15.000", "28.000", "Explain the first scene.", "demo-voice"),
                segment(1, "55.000", "70.500", "Explain the second scene.", "demo-voice")
        );
    }

    private List<NarrationMixKeyframeRecord> mixKeyframes() {
        return List.of(
                keyframe(NarrationMixLane.DUCKING_VOLUME, "0.000", "0.600"),
                keyframe(NarrationMixLane.DUCKING_VOLUME, "20.000", "0.250"),
                keyframe(NarrationMixLane.NARRATION_VOLUME, "20.000", "1.400"),
                keyframe(NarrationMixLane.FADE_DURATION_MS, "20.000", "500.000")
        );
    }

    private NarrationMixKeyframeRecord keyframe(NarrationMixLane lane, String timeSeconds, String value) {
        return new NarrationMixKeyframeRecord(
                "keyframe-" + lane + "-" + timeSeconds,
                "job-narration",
                lane,
                new BigDecimal(timeSeconds),
                new BigDecimal(value),
                Instant.parse("2026-06-29T10:20:00Z"),
                Instant.parse("2026-06-29T10:20:00Z")
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
                index == 0 ? new BigDecimal("0.250") : null,
                index == 0 ? new BigDecimal("1.500") : null,
                index == 0 ? 125 : null,
                Instant.parse("2026-06-29T10:00:00Z"),
                Instant.parse("2026-06-29T10:00:00Z")
        );
    }

    private NarrationMixSettingsRecord savedMixSettings() {
        return new NarrationMixSettingsRecord(
                "job-narration",
                new BigDecimal("0.125"),
                new BigDecimal("1.750"),
                400,
                Instant.parse("2026-06-29T10:15:00Z")
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

    private List<String> zipEntries(InputStream inputStream) throws Exception {
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream, StandardCharsets.UTF_8)) {
            java.util.ArrayList<String> entries = new java.util.ArrayList<>();
            java.util.zip.ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
            return entries;
        }
    }

    private String zipText(InputStream inputStream) throws Exception {
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream, StandardCharsets.UTF_8)) {
            StringBuilder content = new StringBuilder();
            java.util.zip.ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                content.append(new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
            }
            return content.toString();
        }
    }

    private record StaticNarrationSegmentRepository(List<NarrationSegmentRecord> records)
            implements NarrationSegmentRepository {

        @Override
        public void replaceSegments(String jobId, List<NarrationSegmentRecord> segments) {
        }

        @Override
        public List<NarrationSegmentRecord> findByJobId(String jobId) {
            return records;
        }

        @Override
        public void deleteByJobId(String jobId) {
        }
    }

    private record StaticNarrationMixSettingsRepository(NarrationMixSettingsRecord settings)
            implements NarrationMixSettingsRepository {

        @Override
        public Optional<NarrationMixSettingsRecord> findByJobId(String jobId) {
            return Optional.ofNullable(settings)
                    .filter(record -> record.jobId().equals(jobId));
        }

        @Override
        public NarrationMixSettingsRecord upsert(NarrationMixSettingsRecord settings) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteByJobId(String jobId) {
        }
    }

    private record StaticNarrationMixKeyframeRepository(List<NarrationMixKeyframeRecord> records)
            implements NarrationMixKeyframeRepository {

        @Override
        public void replaceKeyframes(String jobId, List<NarrationMixKeyframeRecord> keyframes) {
        }

        @Override
        public List<NarrationMixKeyframeRecord> findByJobId(String jobId) {
            return records.stream()
                    .filter(record -> record.jobId().equals(jobId))
                    .toList();
        }

        @Override
        public void deleteByJobId(String jobId) {
        }
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

    private static final class MutableNarrationMixKeyframeRepository implements NarrationMixKeyframeRepository {

        private List<NarrationMixKeyframeRecord> records;

        private MutableNarrationMixKeyframeRepository(List<NarrationMixKeyframeRecord> records) {
            this.records = new ArrayList<>(records);
        }

        @Override
        public void replaceKeyframes(String jobId, List<NarrationMixKeyframeRecord> keyframes) {
            this.records = new ArrayList<>(keyframes);
        }

        @Override
        public List<NarrationMixKeyframeRecord> findByJobId(String jobId) {
            return records.stream()
                    .filter(record -> record.jobId().equals(jobId))
                    .toList();
        }

        @Override
        public void deleteByJobId(String jobId) {
            this.records = List.of();
        }

        private List<NarrationMixKeyframeRecord> records() {
            return List.copyOf(records);
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

    private static final class StaticNarrationVoiceCatalogService implements NarrationVoiceCatalogService {

        @Override
        public NarrationVoiceCatalogVo catalog() {
            return new NarrationVoiceCatalogVo(
                    "demo",
                    "demo-voice",
                    List.of(new NarrationVoicePresetVo("demo-voice", "Demo voice", "demo", true, "Deterministic local demo TTS voice.")),
                    List.of("Voice presets are provider identifiers, not uploaded reference audio.")
            );
        }
    }
}
