package com.linguaframe.job.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
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

    @Test
    void exportsTargetSubtitleJsonWithLanguageAndSegments() throws Exception {
        byte[] content = service.exportSubtitleJson(sampleSubtitleSegments());

        JsonNode root = new ObjectMapper().readTree(content);
        assertThat(root.get("language").asText()).isEqualTo("zh-CN");
        assertThat(root.get("segments")).hasSize(2);
        assertThat(root.get("segments").get(0).get("language").asText()).isEqualTo("zh-CN");
        assertThat(root.get("segments").get(0).get("index").asInt()).isEqualTo(0);
        assertThat(root.get("segments").get(0).get("startMs").asLong()).isEqualTo(0L);
        assertThat(root.get("segments").get(0).get("endMs").asLong()).isEqualTo(1_800L);
        assertThat(root.get("segments").get(0).get("text").asText()).isEqualTo("LinguaFrame 向你问好。");
    }

    @Test
    void exportsTargetSubtitleSrtWithTranslatedText() {
        String content = new String(service.exportSubtitleSrt(sampleSubtitleSegments()), StandardCharsets.UTF_8);

        assertThat(content).isEqualTo("""
                1
                00:00:00,000 --> 00:00:01,800
                LinguaFrame 向你问好。

                2
                00:00:01,800 --> 00:00:03,600
                这个演示字幕是确定性的。

                """);
    }

    @Test
    void exportsTargetSubtitleVttWithTranslatedText() {
        String content = new String(service.exportSubtitleVtt(sampleSubtitleSegments()), StandardCharsets.UTF_8);

        assertThat(content).isEqualTo("""
                WEBVTT

                00:00:00.000 --> 00:00:01.800
                LinguaFrame 向你问好。

                00:00:01.800 --> 00:00:03.600
                这个演示字幕是确定性的。

                """);
    }

    private List<TranscriptSegmentVo> sampleSegments() {
        return List.of(
                new TranscriptSegmentVo(0, 0L, 1_200L, "First line"),
                new TranscriptSegmentVo(1, 62_300L, 3_723_456L, "Second line")
        );
    }

    private List<SubtitleSegmentVo> sampleSubtitleSegments() {
        return List.of(
                new SubtitleSegmentVo("zh-CN", 0, 0L, 1_800L, "LinguaFrame 向你问好。"),
                new SubtitleSegmentVo("zh-CN", 1, 1_800L, 3_600L, "这个演示字幕是确定性的。")
        );
    }
}
