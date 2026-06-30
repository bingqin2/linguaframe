package com.linguaframe.job.service;

import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.entity.NarrationMixKeyframeRecord;
import com.linguaframe.job.domain.entity.NarrationSegmentRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.NarrationMixLane;
import com.linguaframe.job.domain.vo.NarrationSceneBoardVo;
import com.linguaframe.job.repository.JobArtifactRepository;
import com.linguaframe.job.repository.NarrationMixKeyframeRepository;
import com.linguaframe.job.repository.NarrationSegmentRepository;
import com.linguaframe.job.service.impl.NarrationSceneBoardServiceImpl;
import com.linguaframe.job.service.impl.NarrationVoiceCatalogServiceImpl;
import com.linguaframe.common.config.LinguaFrameProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NarrationSceneBoardServiceTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-30T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void summarizesSavedNarrationRowsWithoutExposingTextInMarkdown() {
        FakeNarrationSegmentRepository segmentRepository = new FakeNarrationSegmentRepository(List.of(
                segment(0, "15.000", "28.000", "Explain the first private scene with exact details.", "demo-voice", "0.250", null, 125),
                segment(1, "55.000", "70.000", "Explain the second private scene.", null, null, "1.250", null)
        ));
        FakeNarrationMixKeyframeRepository keyframeRepository = new FakeNarrationMixKeyframeRepository(List.of(
                new NarrationMixKeyframeRecord("keyframe-1", "job-scene", NarrationMixLane.DUCKING_VOLUME, bd("15.000"), bd("0.300"), CLOCK.instant(), CLOCK.instant()),
                new NarrationMixKeyframeRecord("keyframe-2", "job-scene", NarrationMixLane.NARRATION_VOLUME, bd("55.000"), bd("1.250"), CLOCK.instant(), CLOCK.instant())
        ));
        FakeJobArtifactRepository artifactRepository = new FakeJobArtifactRepository(List.of(
                artifact("audio-1", JobArtifactType.NARRATION_AUDIO),
                artifact("video-1", JobArtifactType.NARRATED_VIDEO)
        ));
        NarrationSceneBoardService service = new NarrationSceneBoardServiceImpl(
                segmentRepository,
                keyframeRepository,
                artifactRepository,
                new NarrationVoiceCatalogServiceImpl(new LinguaFrameProperties()),
                CLOCK
        );

        NarrationSceneBoardVo board = service.getSceneBoard("job-scene");

        assertThat(board.status()).isEqualTo("READY");
        assertThat(board.segmentCount()).isEqualTo(2);
        assertThat(board.totalNarrationSeconds()).isEqualByComparingTo("28.000");
        assertThat(board.totalSpanSeconds()).isEqualByComparingTo("55.000");
        assertThat(board.coveragePercent()).isEqualByComparingTo("50.91");
        assertThat(board.gapCount()).isEqualTo(1);
        assertThat(board.hasOverlap()).isFalse();
        assertThat(board.voiceCount()).isEqualTo(1);
        assertThat(board.mixOverrideCount()).isEqualTo(2);
        assertThat(board.mixKeyframeCount()).isEqualTo(2);
        assertThat(board.audioReady()).isTrue();
        assertThat(board.videoReady()).isTrue();
        assertThat(board.segments())
                .extracting(segment -> segment.index() + ":" + segment.windowLabel() + ":" + segment.voiceState() + ":" + segment.timingStatus() + ":" + segment.mixState() + ":" + segment.readiness())
                .containsExactly(
                        "0:00:15.000-00:28.000:demo-voice:READY:OVERRIDE:READY",
                        "1:00:55.000-01:10.000:Inherit default:READY:OVERRIDE:READY"
                );
        assertThat(board.checks())
                .extracting(check -> check.key() + ":" + check.status())
                .contains("audio:READY", "video:READY");
        assertThat(board.recommendedActions())
                .extracting(action -> action.key())
                .contains("render-preflight");

        String markdown = service.renderMarkdown("job-scene");

        assertThat(markdown)
                .contains("# Narration Scene Board")
                .contains("- Job: job-scene")
                .contains("- Status: READY")
                .contains("00:15.000-00:28.000")
                .doesNotContain("Explain the first private scene")
                .doesNotContain("Explain the second private scene");
    }

    @Test
    void reportsBlockedWhenRowsAreMissingAndAttentionWhenMediaIsMissing() {
        NarrationSceneBoardService emptyService = new NarrationSceneBoardServiceImpl(
                new FakeNarrationSegmentRepository(List.of()),
                new FakeNarrationMixKeyframeRepository(List.of()),
                new FakeJobArtifactRepository(List.of()),
                new NarrationVoiceCatalogServiceImpl(new LinguaFrameProperties()),
                CLOCK
        );

        NarrationSceneBoardVo emptyBoard = emptyService.getSceneBoard("job-empty");

        assertThat(emptyBoard.status()).isEqualTo("EMPTY");
        assertThat(emptyBoard.checks())
                .extracting(check -> check.key() + ":" + check.status())
                .contains("segments:BLOCKED");

        NarrationSceneBoardService partialService = new NarrationSceneBoardServiceImpl(
                new FakeNarrationSegmentRepository(List.of(
                        segment(0, "0.000", "8.000", "Visible only in browser workspace.", null, null, null, null)
                )),
                new FakeNarrationMixKeyframeRepository(List.of()),
                new FakeJobArtifactRepository(List.of()),
                new NarrationVoiceCatalogServiceImpl(new LinguaFrameProperties()),
                CLOCK
        );

        NarrationSceneBoardVo partialBoard = partialService.getSceneBoard("job-partial");

        assertThat(partialBoard.status()).isEqualTo("ATTENTION");
        assertThat(partialBoard.checks())
                .extracting(check -> check.key() + ":" + check.status())
                .contains("audio:ATTENTION", "video:ATTENTION");
    }

    private static NarrationSegmentRecord segment(
            int index,
            String start,
            String end,
            String text,
            String voice,
            String duckingVolume,
            String narrationVolume,
            Integer fadeDurationMs
    ) {
        return new NarrationSegmentRecord(
                "segment-" + index,
                "job-scene",
                index,
                bd(start),
                bd(end),
                text,
                voice,
                duckingVolume == null ? null : bd(duckingVolume),
                narrationVolume == null ? null : bd(narrationVolume),
                fadeDurationMs,
                CLOCK.instant(),
                CLOCK.instant()
        );
    }

    private static JobArtifactRecord artifact(String id, JobArtifactType type) {
        return new JobArtifactRecord(
                id,
                "job-scene",
                type,
                "hidden/object/key",
                type.name().toLowerCase() + ".bin",
                "application/octet-stream",
                100L,
                id + "-sha",
                false,
                null,
                CLOCK.instant()
        );
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static final class FakeNarrationSegmentRepository implements NarrationSegmentRepository {

        private final List<NarrationSegmentRecord> records;

        private FakeNarrationSegmentRepository(List<NarrationSegmentRecord> records) {
            this.records = new ArrayList<>(records);
        }

        @Override
        public void replaceSegments(String jobId, List<NarrationSegmentRecord> segments) {
            throw new UnsupportedOperationException("read only");
        }

        @Override
        public List<NarrationSegmentRecord> findByJobId(String jobId) {
            return records;
        }

        @Override
        public void deleteByJobId(String jobId) {
            throw new UnsupportedOperationException("read only");
        }
    }

    private static final class FakeNarrationMixKeyframeRepository implements NarrationMixKeyframeRepository {

        private final List<NarrationMixKeyframeRecord> records;

        private FakeNarrationMixKeyframeRepository(List<NarrationMixKeyframeRecord> records) {
            this.records = records;
        }

        @Override
        public void replaceKeyframes(String jobId, List<NarrationMixKeyframeRecord> keyframes) {
            throw new UnsupportedOperationException("read only");
        }

        @Override
        public List<NarrationMixKeyframeRecord> findByJobId(String jobId) {
            return records;
        }

        @Override
        public void deleteByJobId(String jobId) {
            throw new UnsupportedOperationException("read only");
        }
    }

    private static final class FakeJobArtifactRepository extends JobArtifactRepository {

        private final List<JobArtifactRecord> records;

        private FakeJobArtifactRepository(List<JobArtifactRecord> records) {
            super((JdbcClient) null);
            this.records = records;
        }

        @Override
        public List<JobArtifactRecord> findByJobId(String jobId) {
            return records;
        }
    }
}
