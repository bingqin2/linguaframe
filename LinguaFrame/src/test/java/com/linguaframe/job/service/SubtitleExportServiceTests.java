package com.linguaframe.job.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.service.impl.SubtitleExportServiceImpl;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubtitleExportServiceTests {

    private final SubtitleExportService service = new SubtitleExportServiceImpl(new ObjectMapper());

    @Test
    void exportsTranscriptJsonWithSegments() throws Exception {
        byte[] content = service.exportTranscriptJson(sampleSegments());

        JsonNode root = new ObjectMapper().readTree(content);
        assertThat(root.get("segments")).hasSize(2);
        assertThat(root.get("segments").get(0).get("text").asText()).isEqualTo("First line");
        assertThat(root.get("segments").get(1).get("startMs").asLong()).isEqualTo(62_300L);
    }

    @Test
    void exportsSrtWithCommaTimestampsAndCueNumbers() {
        String content = new String(service.exportSrt(sampleSegments()), StandardCharsets.UTF_8);

        assertThat(content).isEqualTo("""
                1
                00:00:00,000 --> 00:00:01,200
                First line

                2
                00:01:02,300 --> 01:02:03,456
                Second line

                """);
    }

    @Test
    void exportsWebVttWithDotTimestamps() {
        String content = new String(service.exportVtt(sampleSegments()), StandardCharsets.UTF_8);

        assertThat(content).isEqualTo("""
                WEBVTT

                00:00:00.000 --> 00:00:01.200
                First line

                00:01:02.300 --> 01:02:03.456
                Second line

                """);
    }

    private List<TranscriptSegmentVo> sampleSegments() {
        return List.of(
                new TranscriptSegmentVo(0, 0L, 1_200L, "First line"),
                new TranscriptSegmentVo(1, 62_300L, 3_723_456L, "Second line")
        );
    }
}
