package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.StoredNarrationScriptPackageBo;
import com.linguaframe.job.domain.entity.NarrationMixSettingsRecord;
import com.linguaframe.job.domain.entity.NarrationSegmentRecord;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.NarrationScriptPackageVo;
import com.linguaframe.job.domain.vo.NarrationVoiceCatalogVo;
import com.linguaframe.job.domain.vo.NarrationVoicePresetVo;
import com.linguaframe.job.repository.NarrationMixSettingsRepository;
import com.linguaframe.job.repository.NarrationSegmentRepository;
import com.linguaframe.job.service.impl.NarrationScriptPackageServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class NarrationScriptPackageServiceTests {

    @Test
    void exportsBlockedPackageForEmptyWorkspaceWithoutScriptText() throws Exception {
        NarrationScriptPackageService service = service(List.of(), null, job("job-empty"));

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
        NarrationScriptPackageService service = service(segments(), savedMixSettings(), job("job-narration"));

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
        assertThat(scriptPackage.segments())
                .extracting(segment -> segment.index() + ":" + segment.startSeconds() + ":" + segment.endSeconds() + ":" + segment.durationSeconds() + ":" + segment.voice() + ":" + segment.text())
                .containsExactly(
                        "0:15.000:28.000:13.000:demo-voice:Explain the first scene.",
                        "1:55.000:70.500:15.500:demo-voice:Explain the second scene."
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
                .contains("- Voice summary: PRESET:demo-voice")
                .contains("- Timeline gap count: 1")
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
                .contains("Explain the first scene.")
                .doesNotContain("source-videos/")
                .doesNotContain("/Users/")
                .doesNotContain("provider request payload")
                .doesNotContain("sk-");
    }

    private NarrationScriptPackageService service(
            List<NarrationSegmentRecord> segments,
            NarrationMixSettingsRecord settings,
            LocalizationJobVo job
    ) {
        return new NarrationScriptPackageServiceImpl(
                new StaticNarrationSegmentRepository(segments),
                new StaticNarrationMixSettingsRepository(settings),
                new StaticLocalizationJobQueryService(job),
                new StaticNarrationVoiceCatalogService()
        );
    }

    private List<NarrationSegmentRecord> segments() {
        return List.of(
                segment(0, "15.000", "28.000", "Explain the first scene.", "demo-voice"),
                segment(1, "55.000", "70.500", "Explain the second scene.", "demo-voice")
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
