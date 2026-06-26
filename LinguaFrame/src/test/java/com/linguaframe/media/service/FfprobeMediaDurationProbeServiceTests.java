package com.linguaframe.media.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.media.domain.bo.MediaDurationProbeCommand;
import com.linguaframe.media.domain.exception.UnreadableMediaException;
import com.linguaframe.media.service.impl.FfprobeMediaDurationProbeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FfprobeMediaDurationProbeServiceTests {

    @TempDir
    private Path tempDir;

    @Test
    void roundsDetectedDurationUpToWholeSeconds() throws IOException {
        Path input = videoFile("sample.mp4");
        RecordingCommandRunner runner = new RecordingCommandRunner("299.001\n", "", 0, false);
        FfprobeMediaDurationProbeService service = new FfprobeMediaDurationProbeService(properties("ffmpeg"), runner);

        var result = service.probeDuration(new MediaDurationProbeCommand("sample.mp4", input));

        assertThat(result.durationSeconds()).isEqualTo(299.001);
        assertThat(result.durationSecondsRoundedUp()).isEqualTo(300);
        assertThat(runner.lastCommand).containsExactly(
                "ffprobe",
                "-v",
                "error",
                "-show_entries",
                "format=duration",
                "-of",
                "default=noprint_wrappers=1:nokey=1",
                input.toString()
        );
        assertThat(runner.timeoutSeconds).isEqualTo(30);
    }

    @Test
    void roundsJustOverLimitUpToNextSecond() throws IOException {
        Path input = videoFile("sample.mp4");
        RecordingCommandRunner runner = new RecordingCommandRunner("300.001\n", "", 0, false);
        FfprobeMediaDurationProbeService service = new FfprobeMediaDurationProbeService(properties("ffmpeg"), runner);

        var result = service.probeDuration(new MediaDurationProbeCommand("sample.mp4", input));

        assertThat(result.durationSecondsRoundedUp()).isEqualTo(301);
    }

    @Test
    void derivesFfprobePathBesideConfiguredFfmpegBinary() throws IOException {
        Path input = videoFile("sample.mp4");
        RecordingCommandRunner runner = new RecordingCommandRunner("12\n", "", 0, false);
        FfprobeMediaDurationProbeService service = new FfprobeMediaDurationProbeService(
                properties("/opt/bin/ffmpeg"),
                runner
        );

        service.probeDuration(new MediaDurationProbeCommand("sample.mp4", input));

        assertThat(runner.lastCommand.getFirst()).isEqualTo("/opt/bin/ffprobe");
    }

    @Test
    void timesOutSafely() throws IOException {
        Path input = videoFile("slow.mp4");
        RecordingCommandRunner runner = new RecordingCommandRunner("", "", 0, true);
        FfprobeMediaDurationProbeService service = new FfprobeMediaDurationProbeService(properties("ffmpeg"), runner);

        assertThatThrownBy(() -> service.probeDuration(new MediaDurationProbeCommand("slow.mp4", input)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("FFprobe duration probe timed out.");
        assertThat(runner.destroyed).isTrue();
    }

    @Test
    void failsUnreadableMediaWithoutLeakingFfprobeStderr() throws IOException {
        Path input = videoFile("bad.mp4");
        RecordingCommandRunner runner = new RecordingCommandRunner(
                "",
                "failed to inspect /Users/wangbingqin/Downloads/private.mp4 with sk-test-secret",
                1,
                false
        );
        FfprobeMediaDurationProbeService service = new FfprobeMediaDurationProbeService(properties("ffmpeg"), runner);

        assertThatThrownBy(() -> service.probeDuration(new MediaDurationProbeCommand("bad.mp4", input)))
                .isInstanceOf(UnreadableMediaException.class)
                .hasMessage("The uploaded video could not be inspected.");
    }

    @Test
    void failsUnreadableMediaWhenDurationOutputIsMissing() throws IOException {
        Path input = videoFile("empty-output.mp4");
        RecordingCommandRunner runner = new RecordingCommandRunner("", "", 0, false);
        FfprobeMediaDurationProbeService service = new FfprobeMediaDurationProbeService(properties("ffmpeg"), runner);

        assertThatThrownBy(() -> service.probeDuration(new MediaDurationProbeCommand("empty-output.mp4", input)))
                .isInstanceOf(UnreadableMediaException.class)
                .hasMessage("The uploaded video could not be inspected.");
    }

    @Test
    void failsUnreadableMediaWhenDurationOutputIsInvalid() throws IOException {
        Path input = videoFile("invalid-output.mp4");
        RecordingCommandRunner runner = new RecordingCommandRunner("not-a-duration\n", "", 0, false);
        FfprobeMediaDurationProbeService service = new FfprobeMediaDurationProbeService(properties("ffmpeg"), runner);

        assertThatThrownBy(() -> service.probeDuration(new MediaDurationProbeCommand("invalid-output.mp4", input)))
                .isInstanceOf(UnreadableMediaException.class)
                .hasMessage("The uploaded video could not be inspected.");
    }

    private Path videoFile(String filename) throws IOException {
        Path input = tempDir.resolve(filename);
        Files.write(input, new byte[] {1, 2, 3});
        return input;
    }

    private LinguaFrameProperties properties(String binaryPath) {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getFfmpeg().setBinaryPath(binaryPath);
        return properties;
    }

    private static class RecordingCommandRunner implements FfprobeMediaDurationProbeService.CommandRunner {

        private final String stdout;
        private final String stderr;
        private final int exitCode;
        private final boolean timeout;
        private List<String> lastCommand;
        private int timeoutSeconds;
        private boolean destroyed;

        private RecordingCommandRunner(String stdout, String stderr, int exitCode, boolean timeout) {
            this.stdout = stdout;
            this.stderr = stderr;
            this.exitCode = exitCode;
            this.timeout = timeout;
        }

        @Override
        public FfprobeMediaDurationProbeService.CommandResult run(List<String> command, int timeoutSeconds) {
            this.lastCommand = command;
            this.timeoutSeconds = timeoutSeconds;
            if (timeout) {
                return new FfprobeMediaDurationProbeService.CommandResult(false, -1, "", "");
            }
            return new FfprobeMediaDurationProbeService.CommandResult(true, exitCode, stdout, stderr);
        }

        @Override
        public void destroy() {
            destroyed = true;
        }
    }
}
