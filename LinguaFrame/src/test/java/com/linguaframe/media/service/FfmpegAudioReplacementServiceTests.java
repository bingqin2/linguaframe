package com.linguaframe.media.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.media.domain.bo.ReplaceVideoAudioCommand;
import com.linguaframe.media.service.impl.FfmpegAudioReplacementServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FfmpegAudioReplacementServiceTests {

    @TempDir
    private Path tempDir;

    @Test
    void replacesVideoAudioWithFixedFfmpegArguments() throws IOException {
        Path inputVideo = tempDir.resolve("burned-video.mp4");
        Path inputAudio = tempDir.resolve("dubbing-audio.mp3");
        Path output = tempDir.resolve("dubbed-video.mp4");
        Files.write(inputVideo, new byte[] {1, 2, 3});
        Files.write(inputAudio, new byte[] {4, 5, 6});
        RecordingCommandRunner runner = new RecordingCommandRunner(0, "", false);
        FfmpegAudioReplacementService service = new FfmpegAudioReplacementServiceImpl(properties(), runner);

        var result = service.replaceAudio(new ReplaceVideoAudioCommand(
                "job-1",
                inputVideo,
                inputAudio,
                output,
                "dubbed-video.mp4"
        ));

        assertThat(runner.lastCommand).containsExactly(
                "ffmpeg",
                "-y",
                "-i",
                inputVideo.toString(),
                "-i",
                inputAudio.toString(),
                "-map",
                "0:v:0",
                "-map",
                "1:a:0",
                "-c:v",
                "copy",
                "-c:a",
                "aac",
                "-shortest",
                "-movflags",
                "+faststart",
                output.toString()
        );
        assertThat(result.filename()).isEqualTo("dubbed-video.mp4");
        assertThat(result.contentType()).isEqualTo("video/mp4");
        assertThat(result.content()).containsExactly(7, 8, 9);
    }

    @Test
    void returnsCallerRequestedOutputFilename() throws IOException {
        Path inputVideo = tempDir.resolve("base-video.mp4");
        Path inputAudio = tempDir.resolve("narration-audio.mp3");
        Path output = tempDir.resolve("narrated-video.mp4");
        Files.write(inputVideo, new byte[] {1, 2, 3});
        Files.write(inputAudio, new byte[] {4, 5, 6});
        RecordingCommandRunner runner = new RecordingCommandRunner(0, "", false);
        FfmpegAudioReplacementService service = new FfmpegAudioReplacementServiceImpl(properties(), runner);

        var result = service.replaceAudio(new ReplaceVideoAudioCommand(
                "job-narrated",
                inputVideo,
                inputAudio,
                output,
                "narrated-video.mp4"
        ));

        assertThat(runner.lastCommand).endsWith(output.toString());
        assertThat(result.filename()).isEqualTo("narrated-video.mp4");
        assertThat(result.contentType()).isEqualTo("video/mp4");
        assertThat(result.content()).containsExactly(7, 8, 9);
    }

    @Test
    void failsWithSafeErrorSummaryWhenFfmpegReturnsNonZero() throws IOException {
        Path inputVideo = tempDir.resolve("bad-video.mp4");
        Path inputAudio = tempDir.resolve("bad-audio.mp3");
        Path output = tempDir.resolve("dubbed-video.mp4");
        Files.write(inputVideo, new byte[] {1});
        Files.write(inputAudio, new byte[] {2});
        FfmpegAudioReplacementService service = new FfmpegAudioReplacementServiceImpl(
                properties(),
                new RecordingCommandRunner(1, "very long ffmpeg stderr ".repeat(50), false)
        );

        assertThatThrownBy(() -> service.replaceAudio(new ReplaceVideoAudioCommand(
                "job-2",
                inputVideo,
                inputAudio,
                output,
                "dubbed-video.mp4"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FFmpeg audio replacement failed")
                .hasMessageNotContaining("repeat");
    }

    @Test
    void failsSafelyWhenFfmpegTimesOut() throws IOException {
        Path inputVideo = tempDir.resolve("slow-video.mp4");
        Path inputAudio = tempDir.resolve("slow-audio.mp3");
        Path output = tempDir.resolve("dubbed-video.mp4");
        Files.write(inputVideo, new byte[] {1});
        Files.write(inputAudio, new byte[] {2});
        RecordingCommandRunner runner = new RecordingCommandRunner(0, "", true);
        FfmpegAudioReplacementService service = new FfmpegAudioReplacementServiceImpl(properties(), runner);

        assertThatThrownBy(() -> service.replaceAudio(new ReplaceVideoAudioCommand(
                "job-3",
                inputVideo,
                inputAudio,
                output,
                "dubbed-video.mp4"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("FFmpeg audio replacement timed out.");
        assertThat(runner.destroyed).isTrue();
    }

    @Test
    void destroysProcessAndRestoresInterruptWhenCommandRunnerIsInterrupted() throws IOException {
        Path inputVideo = tempDir.resolve("interrupted-video.mp4");
        Path inputAudio = tempDir.resolve("interrupted-audio.mp3");
        Path output = tempDir.resolve("dubbed-video.mp4");
        Files.write(inputVideo, new byte[] {1});
        Files.write(inputAudio, new byte[] {2});
        RecordingCommandRunner runner = new RecordingCommandRunner(new InterruptedException("stop"));
        FfmpegAudioReplacementService service = new FfmpegAudioReplacementServiceImpl(properties(), runner);

        try {
            assertThatThrownBy(() -> service.replaceAudio(new ReplaceVideoAudioCommand(
                    "job-4",
                    inputVideo,
                    inputAudio,
                    output,
                    "dubbed-video.mp4"
            )))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("FFmpeg audio replacement failed.");
            assertThat(runner.destroyed).isTrue();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private LinguaFrameProperties properties() {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getFfmpeg().setBinaryPath("ffmpeg");
        properties.getFfmpeg().setBurnInTimeoutSeconds(2);
        return properties;
    }

    private static class RecordingCommandRunner implements FfmpegAudioReplacementServiceImpl.CommandRunner {

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
        public FfmpegAudioReplacementServiceImpl.CommandResult run(
                List<String> command,
                Path outputVideoPath,
                int timeoutSeconds
        ) throws IOException, InterruptedException {
            this.lastCommand = command;
            if (interruptedException != null) {
                throw interruptedException;
            }
            if (timeout) {
                return new FfmpegAudioReplacementServiceImpl.CommandResult(false, -1, "");
            }
            if (exitCode == 0) {
                Files.write(outputVideoPath, new byte[] {7, 8, 9});
            }
            return new FfmpegAudioReplacementServiceImpl.CommandResult(true, exitCode, stderr);
        }

        @Override
        public void destroy() {
            destroyed = true;
        }
    }
}
