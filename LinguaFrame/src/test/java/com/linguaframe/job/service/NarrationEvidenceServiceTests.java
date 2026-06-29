package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.StoredNarrationEvidencePackageBo;
import com.linguaframe.job.domain.entity.NarrationMixSettingsRecord;
import com.linguaframe.job.domain.entity.NarrationSegmentRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsArtifactVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.NarrationEvidenceVo;
import com.linguaframe.job.repository.NarrationMixSettingsRepository;
import com.linguaframe.job.repository.NarrationSegmentRepository;
import com.linguaframe.job.service.impl.NarrationEvidenceServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class NarrationEvidenceServiceTests {

    @Test
    void returnsReadyWhenSegmentsAndNarrationAudioExist() throws Exception {
        NarrationEvidenceService service = service(
                segments(),
                List.of(
                        artifact(JobArtifactType.NARRATION_AUDIO, "narration-audio.mp3"),
                        artifact(JobArtifactType.NARRATED_VIDEO, "narrated-video.mp4")
                ),
                savedMixSettings()
        );

        NarrationEvidenceVo evidence = service.getEvidence("job-narration");

        assertThat(evidence.status()).isEqualTo("READY");
        assertThat(evidence.segmentCount()).isEqualTo(2);
        assertThat(evidence.totalCharacterCount()).isEqualTo(49);
        assertThat(evidence.totalTimelineDurationSeconds()).isEqualByComparingTo("28.500");
        assertThat(evidence.timelineGapCount()).isEqualTo(1);
        assertThat(evidence.timelineGapSeconds()).isEqualByComparingTo("27.000");
        assertThat(evidence.timelineHasOverlap()).isFalse();
        assertThat(evidence.voicePresetCount()).isEqualTo(1);
        assertThat(evidence.voiceSummary()).isEqualTo("PRESET:alloy");
        assertThat(evidence.defaultVoice()).isEqualTo("demo-voice");
        assertThat(evidence.narrationAudioReady()).isTrue();
        assertThat(evidence.audioArtifactCount()).isEqualTo(1);
        assertThat(evidence.audioLayout()).isEqualTo("TIMED_AUDIO_BED");
        assertThat(evidence.timeAligned()).isTrue();
        assertThat(evidence.narratedVideoReady()).isTrue();
        assertThat(evidence.narratedVideoArtifactCount()).isEqualTo(1);
        assertThat(evidence.mixMode()).isEqualTo("DUCKED_ORIGINAL_AUDIO");
        assertThat(evidence.duckingVolume()).isEqualByComparingTo("0.125");
        assertThat(evidence.narrationVolume()).isEqualByComparingTo("1.750");
        assertThat(evidence.fadeDurationMs()).isEqualTo(400);
        assertThat(evidence.mixSettingsSource()).isEqualTo("SAVED");
        assertThat(evidence.safeLinks())
                .extracting(link -> link.href())
                .contains("/api/jobs/job-narration/narration-evidence/download");

        String markdown = service.renderMarkdown("job-narration");
        assertThat(markdown)
                .contains("# Narration Evidence")
                .contains("- Status: READY")
                .contains("- Segment count: 2")
                .contains("- Timeline gap count: 1")
                .contains("- Timeline gap seconds: 27.000")
                .contains("- Timeline has overlap: false")
                .contains("- Voice preset count: 1")
                .contains("- Voice summary: PRESET:alloy")
                .contains("- Default voice: demo-voice")
                .contains("- Narration audio artifacts: 1")
                .contains("- Audio layout: TIMED_AUDIO_BED")
                .contains("- Time aligned: true")
                .contains("- Narrated video artifacts: 1")
                .contains("- Mix mode: DUCKED_ORIGINAL_AUDIO")
                .contains("- Ducking volume: 0.125")
                .contains("- Narration volume: 1.750")
                .contains("- Fade duration ms: 400")
                .contains("- Mix settings source: SAVED")
                .doesNotContain("Explain the first scene")
                .doesNotContain("Explain the second scene")
                .doesNotContain("sk-")
                .doesNotContain("/Users/")
                .doesNotContain("provider request payload");

        StoredNarrationEvidencePackageBo packageBo = service.openPackage("job-narration");
        assertThat(packageBo.filename()).isEqualTo("linguaframe-job-job-narration-narration-evidence.zip");
        assertThat(zipEntries(packageBo.inputStream()))
                .containsExactlyInAnyOrder(
                        "manifest.json",
                        "narration-evidence.md",
                        "narration-summary.json",
                        "README.md"
                );
        String packageText = zipText(service.openPackage("job-narration").inputStream());
        assertThat(packageText)
                .contains("\"timelineGapCount\":1")
                .contains("\"timelineGapSeconds\":\"27.000\"")
                .contains("\"timelineHasOverlap\":false")
                .contains("\"voicePresetCount\":1")
                .contains("\"voiceSummary\":\"PRESET:alloy\"")
                .contains("\"defaultVoice\":\"demo-voice\"");
    }

    @Test
    void reportsDefaultMixSettingsWhenNoSavedSettingsExist() {
        NarrationEvidenceVo evidence = service(
                segments(),
                List.of(artifact(JobArtifactType.NARRATED_VIDEO, "narrated-video.mp4")),
                null
        ).getEvidence("job-narration");

        assertThat(evidence.duckingVolume()).isEqualByComparingTo("0.35");
        assertThat(evidence.narrationVolume()).isEqualByComparingTo("1.00");
        assertThat(evidence.fadeDurationMs()).isEqualTo(250);
        assertThat(evidence.mixSettingsSource()).isEqualTo("DEFAULTS");
    }

    @Test
    void returnsAttentionWhenSegmentsExistWithoutAudio() {
        NarrationEvidenceVo evidence = service(segments(), List.of(), savedMixSettings()).getEvidence("job-narration");

        assertThat(evidence.status()).isEqualTo("ATTENTION");
        assertThat(evidence.narrationAudioReady()).isFalse();
        assertThat(evidence.audioLayout()).isEqualTo("MISSING");
        assertThat(evidence.timeAligned()).isFalse();
        assertThat(evidence.mixMode()).isEqualTo("MISSING");
        assertThat(evidence.duckingVolume()).isNull();
        assertThat(evidence.narrationVolume()).isNull();
        assertThat(evidence.fadeDurationMs()).isEqualTo(0);
        assertThat(evidence.mixSettingsSource()).isNull();
        assertThat(evidence.checks())
                .extracting(check -> check.key() + ":" + check.status())
                .contains("NARRATION_AUDIO:ATTENTION", "NARRATED_VIDEO:ATTENTION");
    }

    @Test
    void returnsAttentionWhenAudioExistsWithoutNarratedVideo() {
        NarrationEvidenceVo evidence = service(
                segments(),
                List.of(artifact(JobArtifactType.NARRATION_AUDIO, "narration-audio.mp3")),
                savedMixSettings()
        ).getEvidence("job-narration");

        assertThat(evidence.status()).isEqualTo("ATTENTION");
        assertThat(evidence.narrationAudioReady()).isTrue();
        assertThat(evidence.narratedVideoReady()).isFalse();
        assertThat(evidence.checks())
                .extracting(check -> check.key() + ":" + check.status())
                .contains("NARRATION_AUDIO:READY", "NARRATED_VIDEO:ATTENTION");
    }

    @Test
    void returnsBlockedWhenNoSegmentsExist() {
        NarrationEvidenceVo evidence = service(List.of(), List.of(), savedMixSettings()).getEvidence("job-narration");

        assertThat(evidence.status()).isEqualTo("BLOCKED");
        assertThat(evidence.segmentCount()).isZero();
        assertThat(evidence.timelineGapCount()).isZero();
        assertThat(evidence.timelineGapSeconds()).isEqualByComparingTo("0.000");
        assertThat(evidence.timelineHasOverlap()).isFalse();
        assertThat(evidence.checks())
                .extracting(check -> check.key() + ":" + check.status())
                .contains("NARRATION_SEGMENTS:BLOCKED");
    }

    private NarrationEvidenceService service(
            List<NarrationSegmentRecord> segments,
            List<JobDiagnosticsArtifactVo> artifacts
    ) {
        return service(segments, artifacts, null);
    }

    private NarrationEvidenceService service(
            List<NarrationSegmentRecord> segments,
            List<JobDiagnosticsArtifactVo> artifacts,
            NarrationMixSettingsRecord settings
    ) {
        return new NarrationEvidenceServiceImpl(
                new StaticNarrationSegmentRepository(segments),
                new StaticLocalizationJobQueryService(artifacts),
                new StaticNarrationMixSettingsRepository(settings)
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

    private List<NarrationSegmentRecord> segments() {
        return List.of(
                segment(0, "15.000", "28.000", "Explain the first scene.", "alloy"),
                segment(1, "55.000", "70.500", "Explain the second scene.", "alloy")
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

    private JobDiagnosticsArtifactVo artifact(JobArtifactType type, String filename) {
        return new JobDiagnosticsArtifactVo(
                "artifact-" + type.name(),
                type,
                filename,
                "audio/mpeg",
                123L,
                "1234567890abcdef",
                false,
                null,
                Instant.parse("2026-06-29T10:30:00Z")
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

    private record StaticLocalizationJobQueryService(List<JobDiagnosticsArtifactVo> artifacts)
            implements LocalizationJobQueryService {

        @Override
        public com.linguaframe.job.domain.vo.LocalizationJobListVo listJobs(
                LocalizationJobStatus status,
                Integer limit,
                Integer offset
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LocalizationJobVo getJob(String jobId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JobDiagnosticsReportVo getDiagnosticsReport(String jobId) {
            LocalizationJobVo job = new LocalizationJobVo(
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
            return new JobDiagnosticsReportVo(Instant.parse("2026-06-29T10:40:00Z"), job, artifacts, artifacts.size());
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
}
