package com.linguaframe.media.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.media.domain.bo.CreateTimedAudioBedCommand;
import com.linguaframe.media.domain.bo.TimedAudioSegmentBo;
import com.linguaframe.media.service.impl.FfmpegTimedAudioBedServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FfmpegTimedAudioBedServiceTests {

    @TempDir
    private Path tempDir;

    @Test
    void createsTimedAudioBedWithDelayedSegmentsAndAmix() throws IOException {
        Path first = tempDir.resolve("segment-1.mp3");
        Path second = tempDir.resolve("segment-2.mp3");
        Path output = tempDir.resolve("narration-audio.mp3");
        Files.write(first, new byte[] {1, 2, 3});
        Files.write(second, new byte[] {4, 5, 6});
        RecordingCommandRunner runner = new RecordingCommandRunner(0, "", false);
        FfmpegTimedAudioBedService service = new FfmpegTimedAudioBedServiceImpl(properties(), runner);

        var result = service.createAudioBed(new CreateTimedAudioBedCommand(
                "job-narration",
                List.of(
                        new TimedAudioSegmentBo(second, new BigDecimal("55.000"), new BigDecimal("70.500")),
                        new TimedAudioSegmentBo(first, new BigDecimal("15.250"), new BigDecimal("28.000"))
                ),
                output,
                "narration-audio.mp3"
        ));

        assertThat(runner.lastCommand).containsExactly(
                "ffmpeg",
                "-y",
                "-i",
                first.toString(),
                "-i",
                second.toString(),
                "-filter_complex",
                "[0:a]adelay=15250|15250[a0];[1:a]adelay=55000|55000[a1];[a0][a1]amix=inputs=2:normalize=0:duration=longest[aout]",
                "-map",
                "[aout]",
                "-c:a",
                "libmp3lame",
                "-q:a",
                "4",
                output.toString()
        );
        assertThat(result.filename()).isEqualTo("narration-audio.mp3");
        assertThat(result.contentType()).isEqualTo("audio/mpeg");
        assertThat(result.audioContent()).containsExactly(7, 8, 9);
    }

    @Test
    void rejectsEmptySegmentList() {
        FfmpegTimedAudioBedService service = new FfmpegTimedAudioBedServiceImpl(
                properties(),
                new RecordingCommandRunner(0, "", false)
        );

        assertThatThrownBy(() -> service.createAudioBed(new CreateTimedAudioBedCommand(
                "job-empty",
                List.of(),
                tempDir.resolve("narration-audio.mp3"),
                "narration-audio.mp3"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Timed narration audio requires at least one segment.");
    }

    @Test
    void failsWithSafeErrorSummaryWhenFfmpegReturnsNonZero() throws IOException {
        Path first = tempDir.resolve("segment-1.mp3");
        Path output = tempDir.resolve("narration-audio.mp3");
        Files.write(first, new byte[] {1});
        FfmpegTimedAudioBedService service = new FfmpegTimedAudioBedServiceImpl(
                properties(),
                new RecordingCommandRunner(1, "very long ffmpeg stderr ".repeat(50), false)
        );

        assertThatThrownBy(() -> service.createAudioBed(new CreateTimedAudioBedCommand(
                "job-failure",
                List.of(new TimedAudioSegmentBo(first, new BigDecimal("1.000"), new BigDecimal("2.000"))),
                output,
                "narration-audio.mp3"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FFmpeg timed narration audio failed")
                .hasMessageNotContaining("repeat");
    }

    @Test
    void failsSafelyWhenFfmpegTimesOut() throws IOException {
        Path first = tempDir.resolve("segment-1.mp3");
        Path output = tempDir.resolve("narration-audio.mp3");
        Files.write(first, new byte[] {1});
        RecordingCommandRunner runner = new RecordingCommandRunner(0, "", true);
        FfmpegTimedAudioBedService service = new FfmpegTimedAudioBedServiceImpl(properties(), runner);

        assertThatThrownBy(() -> service.createAudioBed(new CreateTimedAudioBedCommand(
                "job-timeout",
                List.of(new TimedAudioSegmentBo(first, new BigDecimal("1.000"), new BigDecimal("2.000"))),
                output,
                "narration-audio.mp3"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("FFmpeg timed narration audio timed out.");
        assertThat(runner.destroyed).isTrue();
    }

    private LinguaFrameProperties properties() {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getFfmpeg().setBinaryPath("ffmpeg");
        properties.getFfmpeg().setAudioTimeoutSeconds(2);
        return properties;
    }

    private static class RecordingCommandRunner implements FfmpegTimedAudioBedServiceImpl.CommandRunner {

        private final int exitCode;
        private final String stderr;
        private final boolean timeout;
        private List<String> lastCommand;
        private boolean destroyed;

        private RecordingCommandRunner(int exitCode, String stderr, boolean timeout) {
            this.exitCode = exitCode;
            this.stderr = stderr;
            this.timeout = timeout;
        }

        @Override
        public FfmpegTimedAudioBedServiceImpl.CommandResult run(
                List<String> command,
                Path outputAudioPath,
                int timeoutSeconds
        ) throws IOException {
            this.lastCommand = command;
            if (timeout) {
                return new FfmpegTimedAudioBedServiceImpl.CommandResult(false, -1, "");
            }
            if (exitCode == 0) {
                Files.write(outputAudioPath, new byte[] {7, 8, 9});
            }
            return new FfmpegTimedAudioBedServiceImpl.CommandResult(true, exitCode, stderr);
        }

        @Override
        public void destroy() {
            destroyed = true;
        }
    }
}
