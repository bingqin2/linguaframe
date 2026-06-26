package com.linguaframe.media.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.media.domain.bo.ExtractAudioCommand;
import com.linguaframe.media.service.impl.FfmpegAudioExtractionServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FfmpegAudioExtractionServiceTests {

    @TempDir
    private Path tempDir;

    @Test
    void extractsAudioWithFixedFfmpegArguments() throws IOException {
        Path input = tempDir.resolve("source.mp4");
        Path output = tempDir.resolve("audio.wav");
        Files.write(input, new byte[] {1, 2, 3});
        RecordingCommandRunner runner = new RecordingCommandRunner(0, "", false);
        FfmpegAudioExtractionService service = new FfmpegAudioExtractionServiceImpl(properties(), runner);

        var result = service.extractAudio(new ExtractAudioCommand("job-1", input, output));

        assertThat(runner.lastCommand).containsExactly(
                "ffmpeg",
                "-y",
                "-i",
                input.toString(),
                "-vn",
                "-acodec",
                "pcm_s16le",
                "-ar",
                "16000",
                "-ac",
                "1",
                output.toString()
        );
        assertThat(result.filename()).isEqualTo("audio.wav");
        assertThat(result.contentType()).isEqualTo("audio/wav");
        assertThat(result.content()).containsExactly(4, 5, 6);
    }

    @Test
    void failsWithSafeErrorSummaryWhenFfmpegReturnsNonZero() throws IOException {
        Path input = tempDir.resolve("bad.mp4");
        Path output = tempDir.resolve("audio.wav");
        Files.write(input, new byte[] {1});
        FfmpegAudioExtractionService service = new FfmpegAudioExtractionServiceImpl(
                properties(),
                new RecordingCommandRunner(1, "very long ffmpeg stderr ".repeat(50), false)
        );

        assertThatThrownBy(() -> service.extractAudio(new ExtractAudioCommand("job-2", input, output)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FFmpeg audio extraction failed")
                .hasMessageNotContaining("repeat");
    }

    @Test
    void failsSafelyWhenFfmpegTimesOut() throws IOException {
        Path input = tempDir.resolve("slow.mp4");
        Path output = tempDir.resolve("audio.wav");
        Files.write(input, new byte[] {1});
        RecordingCommandRunner runner = new RecordingCommandRunner(0, "", true);
        FfmpegAudioExtractionService service = new FfmpegAudioExtractionServiceImpl(properties(), runner);

        assertThatThrownBy(() -> service.extractAudio(new ExtractAudioCommand("job-3", input, output)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("FFmpeg audio extraction timed out.");
        assertThat(runner.destroyed).isTrue();
    }

    @Test
    void destroysProcessAndRestoresInterruptWhenCommandRunnerIsInterrupted() throws IOException {
        Path input = tempDir.resolve("interrupted.mp4");
        Path output = tempDir.resolve("audio.wav");
        Files.write(input, new byte[] {1});
        RecordingCommandRunner runner = new RecordingCommandRunner(new InterruptedException("stop"));
        FfmpegAudioExtractionService service = new FfmpegAudioExtractionServiceImpl(properties(), runner);

        try {
            assertThatThrownBy(() -> service.extractAudio(new ExtractAudioCommand("job-4", input, output)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("FFmpeg audio extraction failed.");
            assertThat(runner.destroyed).isTrue();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private LinguaFrameProperties properties() {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getFfmpeg().setBinaryPath("ffmpeg");
        properties.getFfmpeg().setAudioTimeoutSeconds(2);
        return properties;
    }

    private static class RecordingCommandRunner implements FfmpegAudioExtractionServiceImpl.CommandRunner {

        private final int exitCode;
        private final String stderr;
        private final boolean timeout;
        private final InterruptedException interruptedException;
        private List<String> lastCommand;
        private boolean destroyed;

        private RecordingCommandRunner(int exitCode, String stderr, boolean timeout) {
            this.exitCode = exitCode;
            this.stderr = stderr;
            this.timeout = timeout;
            this.interruptedException = null;
        }

        private RecordingCommandRunner(InterruptedException interruptedException) {
            this.exitCode = 0;
            this.stderr = "";
            this.timeout = false;
            this.interruptedException = interruptedException;
        }

        @Override
        public FfmpegAudioExtractionServiceImpl.CommandResult run(
                List<String> command,
                Path outputAudioPath,
                int timeoutSeconds
        ) throws IOException, InterruptedException {
            this.lastCommand = command;
            if (interruptedException != null) {
                throw interruptedException;
            }
            if (timeout) {
                return new FfmpegAudioExtractionServiceImpl.CommandResult(false, -1, "");
            }
            if (exitCode == 0) {
                Files.write(outputAudioPath, new byte[] {4, 5, 6});
            }
            return new FfmpegAudioExtractionServiceImpl.CommandResult(true, exitCode, stderr);
        }

        @Override
        public void destroy() {
            destroyed = true;
        }
    }
}
