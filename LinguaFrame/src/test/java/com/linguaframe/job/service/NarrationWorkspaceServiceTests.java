package com.linguaframe.job.service;

import com.linguaframe.job.domain.dto.SaveNarrationSegmentsRequest;
import com.linguaframe.job.domain.dto.SaveNarrationMixKeyframeDto;
import com.linguaframe.job.domain.dto.UpdateNarrationMixSettingsDto;
import com.linguaframe.job.domain.entity.NarrationMixKeyframeRecord;
import com.linguaframe.job.domain.entity.NarrationMixSettingsRecord;
import com.linguaframe.job.domain.entity.NarrationSegmentRecord;
import com.linguaframe.job.domain.enums.NarrationMixLane;
import com.linguaframe.job.domain.vo.NarrationWorkspaceVo;
import com.linguaframe.job.repository.NarrationMixKeyframeRepository;
import com.linguaframe.job.repository.NarrationMixSettingsRepository;
import com.linguaframe.job.repository.NarrationSegmentRepository;
import com.linguaframe.job.service.impl.NarrationWorkspaceServiceImpl;
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

class NarrationWorkspaceServiceTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-29T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void savesValidTimeCodedNarrationSegments() {
        FakeNarrationSegmentRepository repository = new FakeNarrationSegmentRepository();
        FakeNarrationMixKeyframeRepository keyframeRepository = new FakeNarrationMixKeyframeRepository();
        NarrationWorkspaceService service = new NarrationWorkspaceServiceImpl(
                repository,
                new FakeNarrationMixSettingsRepository(),
                keyframeRepository,
                CLOCK
        );

        NarrationWorkspaceVo workspace = service.saveWorkspace("job-narration", new SaveNarrationSegmentsRequest(List.of(
                new SaveNarrationSegmentsRequest.Segment(0, new BigDecimal("15.000"), new BigDecimal("28.000"), "Explain the first scene.", "demo-voice", new BigDecimal("0.250"), new BigDecimal("1.500"), 125),
                new SaveNarrationSegmentsRequest.Segment(1, new BigDecimal("55.000"), new BigDecimal("70.500"), "Explain the second scene.", "demo-voice")
        ), List.of(
                new SaveNarrationMixKeyframeDto(NarrationMixLane.DUCKING_VOLUME, new BigDecimal("0.000"), new BigDecimal("0.600")),
                new SaveNarrationMixKeyframeDto(NarrationMixLane.DUCKING_VOLUME, new BigDecimal("20.000"), new BigDecimal("0.250")),
                new SaveNarrationMixKeyframeDto(NarrationMixLane.NARRATION_VOLUME, new BigDecimal("20.000"), new BigDecimal("1.400")),
                new SaveNarrationMixKeyframeDto(NarrationMixLane.FADE_DURATION_MS, new BigDecimal("20.000"), new BigDecimal("500.000"))
        )));

        assertThat(workspace.status()).isEqualTo("DRAFT_READY");
        assertThat(workspace.segmentCount()).isEqualTo(2);
        assertThat(workspace.totalDurationSeconds()).isEqualByComparingTo("28.500");
        assertThat(workspace.totalCharacterCount()).isEqualTo(49);
        assertThat(workspace.generationReady()).isTrue();
        assertThat(workspace.mixSettings().duckingVolume()).isEqualByComparingTo("0.350");
        assertThat(workspace.mixSettings().narrationVolume()).isEqualByComparingTo("1.000");
        assertThat(workspace.mixSettings().fadeDurationMs()).isEqualTo(250);
        assertThat(workspace.mixSettings().updatedAt()).isNull();
        assertThat(workspace.voiceCatalog().provider()).isEqualTo("demo");
        assertThat(workspace.voiceCatalog().defaultVoice()).isEqualTo("demo-voice");
        assertThat(workspace.voiceCatalog().presets())
                .extracting(preset -> preset.voice() + ":" + preset.defaultPreset())
                .containsExactly("demo-voice:true");
        assertThat(workspace.timeline().startSeconds()).isEqualByComparingTo("15.000");
        assertThat(workspace.timeline().endSeconds()).isEqualByComparingTo("70.500");
        assertThat(workspace.timeline().totalSpanSeconds()).isEqualByComparingTo("55.500");
        assertThat(workspace.timeline().coveredSeconds()).isEqualByComparingTo("28.500");
        assertThat(workspace.timeline().gapSeconds()).isEqualByComparingTo("27.000");
        assertThat(workspace.timeline().gapCount()).isEqualTo(1);
        assertThat(workspace.timeline().hasOverlap()).isFalse();
        assertThat(workspace.timeline().generationReady()).isTrue();
        assertThat(workspace.timeline().segments())
                .extracting(segment -> segment.index() + ":" + segment.leftPercent() + ":" + segment.widthPercent() + ":" + segment.status())
                .containsExactly(
                        "0:0.00:23.42:READY",
                        "1:72.07:27.93:READY"
                );
        assertThat(workspace.mixAutomation().keyframeCount()).isEqualTo(4);
        assertThat(workspace.mixAutomation().duckingKeyframeCount()).isEqualTo(2);
        assertThat(workspace.mixAutomation().narrationKeyframeCount()).isEqualTo(1);
        assertThat(workspace.mixAutomation().fadeKeyframeCount()).isEqualTo(1);
        assertThat(workspace.mixAutomation().keyframes())
                .extracting(keyframe -> keyframe.lane() + ":" + keyframe.timeSeconds() + ":" + keyframe.value())
                .containsExactly(
                        "DUCKING_VOLUME:0.000:0.600",
                        "DUCKING_VOLUME:20.000:0.250",
                        "NARRATION_VOLUME:20.000:1.400",
                        "FADE_DURATION_MS:20.000:500.000"
                );
        assertThat(workspace.segments())
                .extracting(segment -> segment.index() + ":" + segment.startSeconds() + ":" + segment.endSeconds() + ":" + segment.text() + ":" + segment.voice() + ":" + segment.duckingVolume() + ":" + segment.narrationVolume() + ":" + segment.fadeDurationMs())
                .containsExactly(
                        "0:15.000:28.000:Explain the first scene.:demo-voice:0.250:1.500:125",
                        "1:55.000:70.500:Explain the second scene.:demo-voice:null:null:null"
                );
        assertThat(repository.records)
                .extracting(record -> record.segmentIndex() + ":" + record.text() + ":" + record.voice() + ":" + record.duckingVolume() + ":" + record.narrationVolume() + ":" + record.fadeDurationMs())
                .containsExactly("0:Explain the first scene.:demo-voice:0.250:1.500:125", "1:Explain the second scene.:demo-voice:null:null:null");
        assertThat(keyframeRepository.records)
                .extracting(record -> record.lane() + ":" + record.timeSeconds() + ":" + record.value())
                .containsExactly(
                        "DUCKING_VOLUME:0.000:0.600",
                        "DUCKING_VOLUME:20.000:0.250",
                        "NARRATION_VOLUME:20.000:1.400",
                        "FADE_DURATION_MS:20.000:500.000"
                );
    }

    @Test
    void timelineSummaryHandlesEmptyAndContiguousNarrationSegments() {
        NarrationWorkspaceService emptyService = new NarrationWorkspaceServiceImpl(new FakeNarrationSegmentRepository(), new FakeNarrationMixSettingsRepository(), CLOCK);

        NarrationWorkspaceVo emptyWorkspace = emptyService.getWorkspace("job-empty");

        assertThat(emptyWorkspace.timeline().startSeconds()).isEqualByComparingTo("0.000");
        assertThat(emptyWorkspace.timeline().endSeconds()).isEqualByComparingTo("0.000");
        assertThat(emptyWorkspace.timeline().totalSpanSeconds()).isEqualByComparingTo("0.000");
        assertThat(emptyWorkspace.timeline().coveredSeconds()).isEqualByComparingTo("0.000");
        assertThat(emptyWorkspace.timeline().gapSeconds()).isEqualByComparingTo("0.000");
        assertThat(emptyWorkspace.timeline().gapCount()).isZero();
        assertThat(emptyWorkspace.timeline().hasOverlap()).isFalse();
        assertThat(emptyWorkspace.timeline().generationReady()).isFalse();
        assertThat(emptyWorkspace.timeline().segments()).isEmpty();

        NarrationWorkspaceService service = new NarrationWorkspaceServiceImpl(new FakeNarrationSegmentRepository(), new FakeNarrationMixSettingsRepository(), CLOCK);

        NarrationWorkspaceVo workspace = service.saveWorkspace("job-contiguous", new SaveNarrationSegmentsRequest(List.of(
                new SaveNarrationSegmentsRequest.Segment(0, new BigDecimal("0.000"), new BigDecimal("5.000"), "First beat.", null),
                new SaveNarrationSegmentsRequest.Segment(1, new BigDecimal("5.000"), new BigDecimal("10.000"), "Second beat.", "demo-voice")
        )));

        assertThat(workspace.timeline().startSeconds()).isEqualByComparingTo("0.000");
        assertThat(workspace.timeline().endSeconds()).isEqualByComparingTo("10.000");
        assertThat(workspace.timeline().totalSpanSeconds()).isEqualByComparingTo("10.000");
        assertThat(workspace.timeline().coveredSeconds()).isEqualByComparingTo("10.000");
        assertThat(workspace.timeline().gapSeconds()).isEqualByComparingTo("0.000");
        assertThat(workspace.timeline().gapCount()).isZero();
        assertThat(workspace.timeline().segments())
                .extracting(segment -> segment.index() + ":" + segment.leftPercent() + ":" + segment.widthPercent() + ":" + segment.voice())
                .containsExactly(
                        "0:0.00:50.00:",
                        "1:50.00:50.00:demo-voice"
                );
    }

    @Test
    void rejectsOverlappingSegmentsAndInvalidRanges() {
        NarrationWorkspaceService service = new NarrationWorkspaceServiceImpl(new FakeNarrationSegmentRepository(), new FakeNarrationMixSettingsRepository(), CLOCK);

        assertThatThrownBy(() -> service.saveWorkspace("job-narration", new SaveNarrationSegmentsRequest(List.of(
                new SaveNarrationSegmentsRequest.Segment(0, new BigDecimal("10.000"), new BigDecimal("20.000"), "First", null),
                new SaveNarrationSegmentsRequest.Segment(1, new BigDecimal("19.500"), new BigDecimal("25.000"), "Overlap", null)
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Narration segments must not overlap");

        assertThatThrownBy(() -> service.saveWorkspace("job-narration", new SaveNarrationSegmentsRequest(List.of(
                new SaveNarrationSegmentsRequest.Segment(0, new BigDecimal("12.000"), new BigDecimal("12.000"), "Invalid", null)
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endSeconds must be greater than startSeconds");
    }

    @Test
    void rejectsTooLongTextAndNonContiguousIndexes() {
        NarrationWorkspaceService service = new NarrationWorkspaceServiceImpl(new FakeNarrationSegmentRepository(), new FakeNarrationMixSettingsRepository(), CLOCK);

        assertThatThrownBy(() -> service.saveWorkspace("job-narration", new SaveNarrationSegmentsRequest(List.of(
                new SaveNarrationSegmentsRequest.Segment(1, new BigDecimal("1.000"), new BigDecimal("2.000"), "Skipped index", null)
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Narration segment indexes must start at 0 and be contiguous");

        assertThatThrownBy(() -> service.saveWorkspace("job-narration", new SaveNarrationSegmentsRequest(List.of(
                new SaveNarrationSegmentsRequest.Segment(0, new BigDecimal("1.000"), new BigDecimal("2.000"), "x".repeat(1001), null)
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Narration text must be at most 1000 characters");
    }

    @Test
    void rejectsUnknownNarrationVoicePreset() {
        NarrationWorkspaceService service = new NarrationWorkspaceServiceImpl(new FakeNarrationSegmentRepository(), new FakeNarrationMixSettingsRepository(), CLOCK);

        assertThatThrownBy(() -> service.saveWorkspace("job-narration", new SaveNarrationSegmentsRequest(List.of(
                new SaveNarrationSegmentsRequest.Segment(0, new BigDecimal("1.000"), new BigDecimal("2.000"), "Unknown voice.", "custom-clone")
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Narration voice must be one of the configured presets");
    }

    @Test
    void clearsNarrationWorkspace() {
        FakeNarrationSegmentRepository repository = new FakeNarrationSegmentRepository();
        repository.records.add(new NarrationSegmentRecord(
                "narration-1",
                "job-narration",
                0,
                new BigDecimal("1.000"),
                new BigDecimal("2.000"),
                "Existing",
                "verse",
                null,
                null,
                null,
                Instant.parse("2026-06-29T09:00:00Z"),
                Instant.parse("2026-06-29T09:00:00Z")
        ));
        FakeNarrationMixSettingsRepository mixRepository = new FakeNarrationMixSettingsRepository();
        mixRepository.upsert(new NarrationMixSettingsRecord(
                "job-narration",
                new BigDecimal("0.200"),
                new BigDecimal("1.500"),
                100,
                Instant.parse("2026-06-29T09:30:00Z")
        ));
        NarrationWorkspaceService service = new NarrationWorkspaceServiceImpl(repository, mixRepository, CLOCK);

        NarrationWorkspaceVo workspace = service.clearWorkspace("job-narration");

        assertThat(workspace.status()).isEqualTo("EMPTY");
        assertThat(workspace.segmentCount()).isZero();
        assertThat(workspace.generationReady()).isFalse();
        assertThat(workspace.mixSettings().duckingVolume()).isEqualByComparingTo("0.200");
        assertThat(mixRepository.findByJobId("job-narration")).isPresent();
        assertThat(repository.records).isEmpty();
    }

    @Test
    void updatesMixSettingsIndependentlyFromNarrationSegments() {
        FakeNarrationSegmentRepository segmentRepository = new FakeNarrationSegmentRepository();
        FakeNarrationMixSettingsRepository mixRepository = new FakeNarrationMixSettingsRepository();
        NarrationWorkspaceService service = new NarrationWorkspaceServiceImpl(segmentRepository, mixRepository, CLOCK);

        NarrationWorkspaceVo workspace = service.updateMixSettings("job-narration", new UpdateNarrationMixSettingsDto(
                new BigDecimal("0.125"),
                new BigDecimal("1.750"),
                400
        ));

        assertThat(workspace.mixSettings().duckingVolume()).isEqualByComparingTo("0.125");
        assertThat(workspace.mixSettings().narrationVolume()).isEqualByComparingTo("1.750");
        assertThat(workspace.mixSettings().fadeDurationMs()).isEqualTo(400);
        assertThat(workspace.mixSettings().updatedAt()).isEqualTo(Instant.parse("2026-06-29T10:00:00Z"));
        assertThat(segmentRepository.records).isEmpty();
    }

    @Test
    void rejectsMixSettingsOutsideAllowedRanges() {
        NarrationWorkspaceService service = new NarrationWorkspaceServiceImpl(new FakeNarrationSegmentRepository(), new FakeNarrationMixSettingsRepository(), CLOCK);

        assertThatThrownBy(() -> service.updateMixSettings("job-narration", new UpdateNarrationMixSettingsDto(
                new BigDecimal("1.001"),
                new BigDecimal("1.000"),
                250
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duckingVolume must be between 0.00 and 1.00");

        assertThatThrownBy(() -> service.updateMixSettings("job-narration", new UpdateNarrationMixSettingsDto(
                new BigDecimal("0.350"),
                new BigDecimal("2.001"),
                250
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("narrationVolume must be between 0.00 and 2.00");

        assertThatThrownBy(() -> service.updateMixSettings("job-narration", new UpdateNarrationMixSettingsDto(
                new BigDecimal("0.350"),
                new BigDecimal("1.000"),
                5001
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fadeDurationMs must be between 0 and 5000");
    }

    @Test
    void rejectsSegmentMixOverridesOutsideAllowedRanges() {
        NarrationWorkspaceService service = new NarrationWorkspaceServiceImpl(new FakeNarrationSegmentRepository(), new FakeNarrationMixSettingsRepository(), CLOCK);

        assertThatThrownBy(() -> service.saveWorkspace("job-narration", new SaveNarrationSegmentsRequest(List.of(
                new SaveNarrationSegmentsRequest.Segment(0, new BigDecimal("1.000"), new BigDecimal("2.000"), "Too loud.", null, new BigDecimal("1.001"), null, null)
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duckingVolume must be between 0.00 and 1.00");

        assertThatThrownBy(() -> service.saveWorkspace("job-narration", new SaveNarrationSegmentsRequest(List.of(
                new SaveNarrationSegmentsRequest.Segment(0, new BigDecimal("1.000"), new BigDecimal("2.000"), "Too loud.", null, null, new BigDecimal("2.001"), null)
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("narrationVolume must be between 0.00 and 2.00");

        assertThatThrownBy(() -> service.saveWorkspace("job-narration", new SaveNarrationSegmentsRequest(List.of(
                new SaveNarrationSegmentsRequest.Segment(0, new BigDecimal("1.000"), new BigDecimal("2.000"), "Too slow.", null, null, null, 5001)
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fadeDurationMs must be between 0 and 5000");
    }

    @Test
    void rejectsInvalidMixAutomationKeyframes() {
        NarrationWorkspaceService service = new NarrationWorkspaceServiceImpl(
                new FakeNarrationSegmentRepository(),
                new FakeNarrationMixSettingsRepository(),
                new FakeNarrationMixKeyframeRepository(),
                CLOCK
        );

        assertThatThrownBy(() -> service.saveWorkspace("job-narration", new SaveNarrationSegmentsRequest(List.of(
                new SaveNarrationSegmentsRequest.Segment(0, new BigDecimal("1.000"), new BigDecimal("2.000"), "Valid segment.", null)
        ), List.of(
                new SaveNarrationMixKeyframeDto(NarrationMixLane.DUCKING_VOLUME, new BigDecimal("-0.001"), new BigDecimal("0.500"))
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Narration mix keyframe timeSeconds must be greater than or equal to 0");

        assertThatThrownBy(() -> service.saveWorkspace("job-narration", new SaveNarrationSegmentsRequest(List.of(
                new SaveNarrationSegmentsRequest.Segment(0, new BigDecimal("1.000"), new BigDecimal("2.000"), "Valid segment.", null)
        ), List.of(
                new SaveNarrationMixKeyframeDto(NarrationMixLane.NARRATION_VOLUME, new BigDecimal("1.000"), new BigDecimal("2.001"))
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("narrationVolume must be between 0.00 and 2.00");

        assertThatThrownBy(() -> service.saveWorkspace("job-narration", new SaveNarrationSegmentsRequest(List.of(
                new SaveNarrationSegmentsRequest.Segment(0, new BigDecimal("1.000"), new BigDecimal("2.000"), "Valid segment.", null)
        ), List.of(
                new SaveNarrationMixKeyframeDto(NarrationMixLane.FADE_DURATION_MS, new BigDecimal("1.000"), new BigDecimal("5000.001"))
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fadeDurationMs must be between 0 and 5000");

        assertThatThrownBy(() -> service.saveWorkspace("job-narration", new SaveNarrationSegmentsRequest(List.of(
                new SaveNarrationSegmentsRequest.Segment(0, new BigDecimal("1.000"), new BigDecimal("2.000"), "Valid segment.", null)
        ), List.of(
                new SaveNarrationMixKeyframeDto(NarrationMixLane.DUCKING_VOLUME, new BigDecimal("1.000"), new BigDecimal("0.500")),
                new SaveNarrationMixKeyframeDto(NarrationMixLane.DUCKING_VOLUME, new BigDecimal("1.0004"), new BigDecimal("0.250"))
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Narration mix keyframes must not duplicate lane and timeSeconds");
    }

    private static final class FakeNarrationSegmentRepository implements NarrationSegmentRepository {

        private final List<NarrationSegmentRecord> records = new ArrayList<>();

        @Override
        public void replaceSegments(String jobId, List<NarrationSegmentRecord> segments) {
            records.removeIf(record -> record.jobId().equals(jobId));
            records.addAll(segments);
        }

        @Override
        public List<NarrationSegmentRecord> findByJobId(String jobId) {
            return records.stream()
                    .filter(record -> record.jobId().equals(jobId))
                    .toList();
        }

        @Override
        public void deleteByJobId(String jobId) {
            records.removeIf(record -> record.jobId().equals(jobId));
        }
    }

    private static final class FakeNarrationMixSettingsRepository implements NarrationMixSettingsRepository {

        private final List<NarrationMixSettingsRecord> records = new ArrayList<>();

        @Override
        public Optional<NarrationMixSettingsRecord> findByJobId(String jobId) {
            return records.stream()
                    .filter(record -> record.jobId().equals(jobId))
                    .findFirst();
        }

        @Override
        public NarrationMixSettingsRecord upsert(NarrationMixSettingsRecord settings) {
            deleteByJobId(settings.jobId());
            records.add(settings);
            return settings;
        }

        @Override
        public void deleteByJobId(String jobId) {
            records.removeIf(record -> record.jobId().equals(jobId));
        }
    }

    private static final class FakeNarrationMixKeyframeRepository implements NarrationMixKeyframeRepository {

        private final List<NarrationMixKeyframeRecord> records = new ArrayList<>();

        @Override
        public void replaceKeyframes(String jobId, List<NarrationMixKeyframeRecord> keyframes) {
            records.removeIf(record -> record.jobId().equals(jobId));
            records.addAll(keyframes);
        }

        @Override
        public List<NarrationMixKeyframeRecord> findByJobId(String jobId) {
            return records.stream()
                    .filter(record -> record.jobId().equals(jobId))
                    .toList();
        }

        @Override
        public void deleteByJobId(String jobId) {
            records.removeIf(record -> record.jobId().equals(jobId));
        }
    }
}
