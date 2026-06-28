package com.linguaframe.media.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.enums.SubtitleStylePreset;
import com.linguaframe.media.domain.bo.BurnInSubtitlesCommand;
import com.linguaframe.media.service.impl.FfmpegSubtitleBurnInServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FfmpegSubtitleBurnInServiceTests {

    @TempDir
    private Path tempDir;

    @Test
    void burnsSubtitlesWithFixedFfmpegArguments() throws IOException {
        Path input = tempDir.resolve("source.mp4");
        Path subtitle = tempDir.resolve("target-subtitles.srt");
        Path output = tempDir.resolve("burned-video.mp4");
        Files.write(input, new byte[] {1, 2, 3});
        Files.writeString(subtitle, "1\n00:00:00,000 --> 00:00:01,000\nHello\n");
        RecordingCommandRunner runner = new RecordingCommandRunner(0, "", false);
        FfmpegSubtitleBurnInService service = new FfmpegSubtitleBurnInServiceImpl(properties(), runner);

        var result = service.burnInSubtitles(new BurnInSubtitlesCommand("job-1", input, subtitle, output));

        assertThat(runner.lastCommand).containsExactly(
                "ffmpeg",
                "-y",
                "-i",
                input.toString(),
                "-vf",
                "subtitles='" + subtitle.toAbsolutePath() + "':force_style='Fontsize=20\\,Outline=2\\,Shadow=0\\,PrimaryColour=&H00FFFFFF\\,OutlineColour=&H00000000'",
                "-c:v",
                "libx264",
                "-preset",
                "veryfast",
                "-c:a",
                "copy",
                "-movflags",
                "+faststart",
                output.toString()
        );
        assertThat(result.filename()).isEqualTo("burned-video.mp4");
        assertThat(result.contentType()).isEqualTo("video/mp4");
        assertThat(result.content()).containsExactly(4, 5, 6);
    }

    @Test
    void appliesSubtitleStylePresetToFfmpegFilter() throws IOException {
        Path input = tempDir.resolve("source.mp4");
        Path subtitle = tempDir.resolve("target subtitles.srt");
        Path output = tempDir.resolve("burned-video.mp4");
        Files.write(input, new byte[] {1, 2, 3});
        Files.writeString(subtitle, "1\n00:00:00,000 --> 00:00:01,000\nHello\n");
        RecordingCommandRunner runner = new RecordingCommandRunner(0, "", false);
        FfmpegSubtitleBurnInService service = new FfmpegSubtitleBurnInServiceImpl(properties(), runner);

        service.burnInSubtitles(new BurnInSubtitlesCommand(
                "job-contrast",
                input,
                subtitle,
                output,
                SubtitleStylePreset.HIGH_CONTRAST.name()
        ));

        assertThat(runner.lastCommand)
                .containsSubsequence("-vf", "subtitles='" + subtitle.toAbsolutePath() + "':force_style='Fontsize=24\\,Outline=3\\,Shadow=1\\,PrimaryColour=&H00FFFFFF\\,OutlineColour=&H00000000\\,BackColour=&H80000000'");
    }

    @Test
    void failsWithSafeErrorSummaryWhenFfmpegReturnsNonZero() throws IOException {
        Path input = tempDir.resolve("bad.mp4");
        Path subtitle = tempDir.resolve("target-subtitles.srt");
        Path output = tempDir.resolve("burned-video.mp4");
        Files.write(input, new byte[] {1});
        Files.writeString(subtitle, "bad subtitle");
        FfmpegSubtitleBurnInService service = new FfmpegSubtitleBurnInServiceImpl(
                properties(),
                new RecordingCommandRunner(1, "very long ffmpeg stderr ".repeat(50), false)
        );

        assertThatThrownBy(() -> service.burnInSubtitles(new BurnInSubtitlesCommand("job-2", input, subtitle, output)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FFmpeg subtitle burn-in failed")
                .hasMessageNotContaining("repeat");
    }

    @Test
    void failsSafelyWhenFfmpegTimesOut() throws IOException {
        Path input = tempDir.resolve("slow.mp4");
        Path subtitle = tempDir.resolve("target-subtitles.srt");
        Path output = tempDir.resolve("burned-video.mp4");
        Files.write(input, new byte[] {1});
        Files.writeString(subtitle, "slow subtitle");
        RecordingCommandRunner runner = new RecordingCommandRunner(0, "", true);
        FfmpegSubtitleBurnInService service = new FfmpegSubtitleBurnInServiceImpl(properties(), runner);

        assertThatThrownBy(() -> service.burnInSubtitles(new BurnInSubtitlesCommand("job-3", input, subtitle, output)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("FFmpeg subtitle burn-in timed out.");
        assertThat(runner.destroyed).isTrue();
    }

    @Test
    void destroysProcessAndRestoresInterruptWhenCommandRunnerIsInterrupted() throws IOException {
        Path input = tempDir.resolve("interrupted.mp4");
        Path subtitle = tempDir.resolve("target-subtitles.srt");
        Path output = tempDir.resolve("burned-video.mp4");
        Files.write(input, new byte[] {1});
        Files.writeString(subtitle, "interrupted subtitle");
        RecordingCommandRunner runner = new RecordingCommandRunner(new InterruptedException("stop"));
        FfmpegSubtitleBurnInService service = new FfmpegSubtitleBurnInServiceImpl(properties(), runner);

        try {
            assertThatThrownBy(() -> service.burnInSubtitles(new BurnInSubtitlesCommand("job-4", input, subtitle, output)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("FFmpeg subtitle burn-in failed.");
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

    private static class RecordingCommandRunner implements FfmpegSubtitleBurnInServiceImpl.CommandRunner {

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
        public FfmpegSubtitleBurnInServiceImpl.CommandResult run(
                List<String> command,
                Path outputVideoPath,
                int timeoutSeconds
        ) throws IOException, InterruptedException {
            this.lastCommand = command;
            if (interruptedException != null) {
                throw interruptedException;
            }
            if (timeout) {
                return new FfmpegSubtitleBurnInServiceImpl.CommandResult(false, -1, "");
            }
            if (exitCode == 0) {
                Files.write(outputVideoPath, new byte[] {4, 5, 6});
            }
            return new FfmpegSubtitleBurnInServiceImpl.CommandResult(true, exitCode, stderr);
        }

        @Override
        public void destroy() {
            destroyed = true;
        }
    }
}
