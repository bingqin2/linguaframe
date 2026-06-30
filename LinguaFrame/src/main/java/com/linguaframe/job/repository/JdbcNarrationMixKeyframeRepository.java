package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.NarrationMixKeyframeRecord;
import com.linguaframe.job.domain.enums.NarrationMixLane;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class JdbcNarrationMixKeyframeRepository implements NarrationMixKeyframeRepository {

    private final JdbcClient jdbcClient;

    public JdbcNarrationMixKeyframeRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public void replaceKeyframes(String jobId, List<NarrationMixKeyframeRecord> keyframes) {
        deleteByJobId(jobId);
        for (NarrationMixKeyframeRecord keyframe : keyframes) {
            insert(keyframe);
        }
    }

    @Override
    public List<NarrationMixKeyframeRecord> findByJobId(String jobId) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            job_id,
                            lane,
                            time_seconds,
                            mix_value,
                            created_at,
                            updated_at
                        FROM narration_mix_keyframes
                        WHERE job_id = :jobId
                        ORDER BY
                            CASE lane
                                WHEN 'DUCKING_VOLUME' THEN 0
                                WHEN 'NARRATION_VOLUME' THEN 1
                                WHEN 'FADE_DURATION_MS' THEN 2
                                ELSE 99
                            END,
                            time_seconds
                        """)
                .param("jobId", jobId)
                .query(this::mapRow)
                .list();
    }

    @Override
    public void deleteByJobId(String jobId) {
        jdbcClient.sql("DELETE FROM narration_mix_keyframes WHERE job_id = :jobId")
                .param("jobId", jobId)
                .update();
    }

    private void insert(NarrationMixKeyframeRecord record) {
        jdbcClient.sql("""
                        INSERT INTO narration_mix_keyframes (
                            id,
                            job_id,
                            lane,
                            time_seconds,
                            mix_value,
                            created_at,
                            updated_at
                        )
                        VALUES (
                            :id,
                            :jobId,
                            :lane,
                            :timeSeconds,
                            :mixValue,
                            :createdAt,
                            :updatedAt
                        )
                        """)
                .param("id", record.id())
                .param("jobId", record.jobId())
                .param("lane", record.lane().name())
                .param("timeSeconds", record.timeSeconds())
                .param("mixValue", record.value())
                .param("createdAt", Timestamp.from(record.createdAt()))
                .param("updatedAt", Timestamp.from(record.updatedAt()))
                .update();
    }

    private NarrationMixKeyframeRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new NarrationMixKeyframeRecord(
                rs.getString("id"),
                rs.getString("job_id"),
                NarrationMixLane.valueOf(rs.getString("lane")),
                rs.getBigDecimal("time_seconds"),
                rs.getBigDecimal("mix_value"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
