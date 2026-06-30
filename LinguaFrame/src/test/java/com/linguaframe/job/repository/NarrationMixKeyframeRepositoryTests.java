package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.NarrationMixKeyframeRecord;
import com.linguaframe.job.domain.enums.NarrationMixLane;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class NarrationMixKeyframeRepositoryTests {

    @Autowired
    private NarrationMixKeyframeRepository repository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM narration_mix_keyframes").update();
        jdbcClient.sql("DELETE FROM localization_jobs").update();
        jdbcClient.sql("DELETE FROM videos").update();
        insertJob("job-keyframes");
        insertJob("other-job");
    }

    @Test
    void replacesAndReadsKeyframesByJobIdInLaneAndTimeOrder() {
        repository.replaceKeyframes("job-keyframes", List.of(
                keyframe("keyframe-3", "job-keyframes", NarrationMixLane.NARRATION_VOLUME, "12.000", "1.400"),
                keyframe("keyframe-1", "job-keyframes", NarrationMixLane.DUCKING_VOLUME, "10.000", "0.250"),
                keyframe("keyframe-2", "job-keyframes", NarrationMixLane.DUCKING_VOLUME, "2.500", "0.600"),
                keyframe("keyframe-4", "job-keyframes", NarrationMixLane.FADE_DURATION_MS, "12.000", "500.000")
        ));

        List<NarrationMixKeyframeRecord> records = repository.findByJobId("job-keyframes");

        assertThat(records)
                .extracting(record -> record.lane()
                        + ":" + record.timeSeconds()
                        + ":" + record.value())
                .containsExactly(
                        "DUCKING_VOLUME:2.500:0.600",
                        "DUCKING_VOLUME:10.000:0.250",
                        "NARRATION_VOLUME:12.000:1.400",
                        "FADE_DURATION_MS:12.000:500.000"
                );
    }

    @Test
    void replaceDeletesOldRowsForSameJobOnly() {
        repository.replaceKeyframes("job-keyframes", List.of(
                keyframe("keyframe-old", "job-keyframes", NarrationMixLane.DUCKING_VOLUME, "1.000", "0.500")
        ));
        repository.replaceKeyframes("other-job", List.of(
                keyframe("keyframe-other", "other-job", NarrationMixLane.NARRATION_VOLUME, "1.000", "1.500")
        ));

        repository.replaceKeyframes("job-keyframes", List.of(
                keyframe("keyframe-new", "job-keyframes", NarrationMixLane.FADE_DURATION_MS, "3.000", "250.000")
        ));

        assertThat(repository.findByJobId("job-keyframes"))
                .extracting(NarrationMixKeyframeRecord::lane)
                .containsExactly(NarrationMixLane.FADE_DURATION_MS);
        assertThat(repository.findByJobId("other-job"))
                .extracting(NarrationMixKeyframeRecord::lane)
                .containsExactly(NarrationMixLane.NARRATION_VOLUME);
    }

    @Test
    void deletesKeyframesForJobOnly() {
        repository.replaceKeyframes("job-keyframes", List.of(
                keyframe("keyframe-delete", "job-keyframes", NarrationMixLane.DUCKING_VOLUME, "1.000", "0.500")
        ));
        repository.replaceKeyframes("other-job", List.of(
                keyframe("keyframe-keep", "other-job", NarrationMixLane.DUCKING_VOLUME, "1.000", "0.700")
        ));

        repository.deleteByJobId("job-keyframes");

        assertThat(repository.findByJobId("job-keyframes")).isEmpty();
        assertThat(repository.findByJobId("other-job")).hasSize(1);
    }

    private NarrationMixKeyframeRecord keyframe(
            String id,
            String jobId,
            NarrationMixLane lane,
            String timeSeconds,
            String value
    ) {
        return new NarrationMixKeyframeRecord(
                id,
                jobId,
                lane,
                new BigDecimal(timeSeconds),
                new BigDecimal(value),
                Instant.parse("2026-06-30T10:00:00Z"),
                Instant.parse("2026-06-30T10:01:00Z")
        );
    }

    private void insertJob(String jobId) {
        String videoId = "video-" + jobId;
        jdbcClient.sql("""
                        INSERT INTO videos (
                            id,
                            original_filename,
                            content_type,
                            file_size_bytes,
                            source_object_key,
                            status,
                            created_at
                        )
                        VALUES (
                            :videoId,
                            :filename,
                            'video/mp4',
                            1024,
                            :objectKey,
                            'UPLOADED',
                            :createdAt
                        )
                        """)
                .param("videoId", videoId)
                .param("filename", videoId + ".mp4")
                .param("objectKey", "uploads/" + videoId + ".mp4")
                .param("createdAt", Instant.parse("2026-06-30T09:00:00Z"))
                .update();
        jdbcClient.sql("""
                        INSERT INTO localization_jobs (
                            id,
                            video_id,
                            target_language,
                            status,
                            created_at,
                            updated_at
                        )
                        VALUES (
                            :jobId,
                            :videoId,
                            'zh-CN',
                            'UPLOADED',
                            :createdAt,
                            :createdAt
                        )
                        """)
                .param("jobId", jobId)
                .param("videoId", videoId)
                .param("createdAt", Instant.parse("2026-06-30T09:00:00Z"))
                .update();
    }
}
