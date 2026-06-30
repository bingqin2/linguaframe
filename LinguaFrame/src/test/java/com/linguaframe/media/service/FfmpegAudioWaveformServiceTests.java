package com.linguaframe.media.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.media.domain.bo.AudioWaveformAnalyzeCommand;
import com.linguaframe.media.service.impl.FfmpegAudioWaveformServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FfmpegAudioWaveformServiceTests {

    @TempDir
    private Path tempDir;

    @Test
    void decodesRawPcmIntoBoundedBuckets() throws IOException {
        Path input = tempDir.resolve("source.mp4");
        Files.write(input, new byte[] {1, 2, 3});
        RecordingCommandRunner runner = new RecordingCommandRunner(0, "", false, pcm(
                0, 16384, -32768, 32767,
                8192, -8192, 4096, -4096
        ));
        FfmpegAudioWaveformService service = new FfmpegAudioWaveformServiceImpl(properties(), runner);

        var waveform = service.analyze(new AudioWaveformAnalyzeCommand(input, 2, 4.0));

        assertThat(runner.lastCommand).containsExactly(
                "ffmpeg",
                "-v",
                "error",
                "-i",
                input.toString(),
                "-vn",
                "-ac",
                "1",
                "-ar",
                "8000",
                "-f",
                "s16le",
                "pipe:1"
        );
        assertThat(waveform.bucketCount()).isEqualTo(2);
        assertThat(waveform.durationSeconds()).isEqualByComparingTo(new BigDecimal("4.000"));
        assertThat(waveform.buckets()).hasSize(2);
        assertThat(waveform.buckets().get(0).startSeconds()).isEqualByComparingTo(new BigDecimal("0.000"));
        assertThat(waveform.buckets().get(0).endSeconds()).isEqualByComparingTo(new BigDecimal("2.000"));
        assertThat(waveform.buckets().get(0).peak()).isEqualByComparingTo(new BigDecimal("1.000"));
        assertThat(waveform.buckets().get(0).rms()).isEqualByComparingTo(new BigDecimal("0.750"));
        assertThat(waveform.buckets().get(1).startSeconds()).isEqualByComparingTo(new BigDecimal("2.000"));
        assertThat(waveform.buckets().get(1).endSeconds()).isEqualByComparingTo(new BigDecimal("4.000"));
        assertThat(waveform.buckets().get(1).peak()).isEqualByComparingTo(new BigDecimal("0.250"));
        assertThat(waveform.buckets().get(1).rms()).isBetween(new BigDecimal("0.190"), new BigDecimal("0.200"));
    }

    @Test
    void clampsInvalidAmplitudeValues() throws IOException {
        Path input = tempDir.resolve("source.wav");
        Files.write(input, new byte[] {1});
        FfmpegAudioWaveformService service = new FfmpegAudioWaveformServiceImpl(
                properties(),
                new RecordingCommandRunner(0, "", false, pcm(Short.MIN_VALUE, Short.MAX_VALUE))
        );

        var waveform = service.analyze(new AudioWaveformAnalyzeCommand(input, 1, 1.0));

        assertThat(waveform.buckets().get(0).peak()).isEqualByComparingTo(new BigDecimal("1.000"));
        assertThat(waveform.buckets().get(0).rms()).isBetween(new BigDecimal("0.999"), new BigDecimal("1.000"));
    }

    @Test
    void failsSafelyWhenFfmpegReturnsNonZero() throws IOException {
        Path input = tempDir.resolve("private-source.mp4");
        Files.write(input, new byte[] {1});
        FfmpegAudioWaveformService service = new FfmpegAudioWaveformServiceImpl(
                properties(),
                new RecordingCommandRunner(1, "bad input at /Users/private/source.mp4 ".repeat(20), false, new byte[0])
        );

        assertThatThrownBy(() -> service.analyze(new AudioWaveformAnalyzeCommand(input, 96, 10.0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FFmpeg waveform analysis failed")
                .hasMessageNotContaining("/Users/private/source.mp4");
    }

    @Test
    void failsSafelyWhenFfmpegTimesOut() throws IOException {
        Path input = tempDir.resolve("slow-source.mp4");
        Files.write(input, new byte[] {1});
        RecordingCommandRunner runner = new RecordingCommandRunner(0, "", true, new byte[0]);
        FfmpegAudioWaveformService service = new FfmpegAudioWaveformServiceImpl(properties(), runner);

        assertThatThrownBy(() -> service.analyze(new AudioWaveformAnalyzeCommand(input, 96, 10.0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("FFmpeg waveform analysis timed out.");
        assertThat(runner.destroyed).isTrue();
    }

    private LinguaFrameProperties properties() {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getFfmpeg().setBinaryPath("ffmpeg");
        properties.getFfmpeg().setAudioTimeoutSeconds(2);
        return properties;
    }

    private static byte[] pcm(int... samples) {
        ByteBuffer buffer = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int sample : samples) {
            buffer.putShort((short) sample);
        }
        return buffer.array();
    }

    private static class RecordingCommandRunner implements FfmpegAudioWaveformServiceImpl.CommandRunner {

        private final int exitCode;
        private final String stderr;
        private final boolean timeout;
        private final byte[] stdout;
        private List<String> lastCommand;
        private boolean destroyed;

        private RecordingCommandRunner(int exitCode, String stderr, boolean timeout, byte[] stdout) {
            this.exitCode = exitCode;
            this.stderr = stderr;
            this.timeout = timeout;
            this.stdout = stdout;
        }

        @Override
        public FfmpegAudioWaveformServiceImpl.CommandResult run(List<String> command, int timeoutSeconds) {
            this.lastCommand = command;
            if (timeout) {
                return new FfmpegAudioWaveformServiceImpl.CommandResult(false, -1, new byte[0], "");
            }
            return new FfmpegAudioWaveformServiceImpl.CommandResult(true, exitCode, stdout, stderr);
        }

        @Override
        public void destroy() {
            destroyed = true;
        }
    }
}
