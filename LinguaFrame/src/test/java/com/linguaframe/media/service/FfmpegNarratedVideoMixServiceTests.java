package com.linguaframe.media.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.media.domain.bo.MixNarratedVideoCommand;
import com.linguaframe.media.domain.bo.NarrationWindowBo;
import com.linguaframe.media.service.impl.FfmpegNarratedVideoMixServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FfmpegNarratedVideoMixServiceTests {

    @TempDir
    private Path tempDir;

    @Test
    void mixesNarrationWithDuckedOriginalAudioWindows() throws IOException {
        Path inputVideo = tempDir.resolve("base-video.mp4");
        Path narrationAudio = tempDir.resolve("narration-audio.mp3");
        Path output = tempDir.resolve("narrated-video.mp4");
        Files.write(inputVideo, new byte[] {1, 2, 3});
        Files.write(narrationAudio, new byte[] {4, 5, 6});
        RecordingCommandRunner runner = new RecordingCommandRunner(0, "", false);
        FfmpegNarratedVideoMixService service = new FfmpegNarratedVideoMixServiceImpl(properties(), runner);

        var result = service.mixNarration(new MixNarratedVideoCommand(
                "job-narrated",
                inputVideo,
                narrationAudio,
                output,
                "narrated-video.mp4",
                new BigDecimal("0.35"),
                List.of(
                        new NarrationWindowBo(new BigDecimal("15.000"), new BigDecimal("28.000")),
                        new NarrationWindowBo(new BigDecimal("55.000"), new BigDecimal("70.500"))
                )
        ));

        assertThat(runner.lastCommand).containsExactly(
                "ffmpeg",
                "-y",
                "-i",
                inputVideo.toString(),
                "-i",
                narrationAudio.toString(),
                "-filter_complex",
                "[0:a]volume='if(between(t,15.000,28.000)+between(t,55.000,70.500),0.35,1.0)'[base];[1:a]volume=1.0[narration];[base][narration]amix=inputs=2:duration=longest:normalize=0[aout]",
                "-map",
                "0:v:0",
                "-map",
                "[aout]",
                "-c:v",
                "copy",
                "-c:a",
                "aac",
                "-movflags",
                "+faststart",
                output.toString()
        );
        assertThat(result.filename()).isEqualTo("narrated-video.mp4");
        assertThat(result.contentType()).isEqualTo("video/mp4");
        assertThat(result.content()).containsExactly(7, 8, 9);
    }

    @Test
    void fallsBackToNarrationAudioWhenBaseVideoHasNoAudioTrack() throws IOException {
        Path inputVideo = tempDir.resolve("silent-video.mp4");
        Path narrationAudio = tempDir.resolve("narration-audio.mp3");
        Path output = tempDir.resolve("narrated-video.mp4");
        Files.write(inputVideo, new byte[] {1});
        Files.write(narrationAudio, new byte[] {2});
        RecordingCommandRunner runner = new RecordingCommandRunner(1, "Stream map '0:a' matches no streams.", false);
        FfmpegNarratedVideoMixService service = new FfmpegNarratedVideoMixServiceImpl(properties(), runner);

        var result = service.mixNarration(new MixNarratedVideoCommand(
                "job-silent",
                inputVideo,
                narrationAudio,
                output,
                "narrated-video.mp4",
                new BigDecimal("0.35"),
                List.of(new NarrationWindowBo(new BigDecimal("1.000"), new BigDecimal("2.000")))
        ));

        assertThat(runner.commands).hasSize(2);
        assertThat(runner.commands.get(1)).containsExactly(
                "ffmpeg",
                "-y",
                "-i",
                inputVideo.toString(),
                "-i",
                narrationAudio.toString(),
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
        assertThat(result.content()).containsExactly(7, 8, 9);
    }

    @Test
    void failsWithSafeErrorSummaryWhenFfmpegReturnsNonZero() throws IOException {
        Path inputVideo = tempDir.resolve("bad-video.mp4");
        Path narrationAudio = tempDir.resolve("bad-audio.mp3");
        Path output = tempDir.resolve("narrated-video.mp4");
        Files.write(inputVideo, new byte[] {1});
        Files.write(narrationAudio, new byte[] {2});
        FfmpegNarratedVideoMixService service = new FfmpegNarratedVideoMixServiceImpl(
                properties(),
                new RecordingCommandRunner(1, "very long ffmpeg stderr ".repeat(50), false)
        );

        assertThatThrownBy(() -> service.mixNarration(new MixNarratedVideoCommand(
                "job-failure",
                inputVideo,
                narrationAudio,
                output,
                "narrated-video.mp4",
                new BigDecimal("0.35"),
                List.of(new NarrationWindowBo(new BigDecimal("1.000"), new BigDecimal("2.000")))
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FFmpeg narrated video mix failed")
                .hasMessageNotContaining("repeat");
    }

    @Test
    void failsSafelyWhenFfmpegTimesOut() throws IOException {
        Path inputVideo = tempDir.resolve("slow-video.mp4");
        Path narrationAudio = tempDir.resolve("slow-audio.mp3");
        Path output = tempDir.resolve("narrated-video.mp4");
        Files.write(inputVideo, new byte[] {1});
        Files.write(narrationAudio, new byte[] {2});
        RecordingCommandRunner runner = new RecordingCommandRunner(0, "", true);
        FfmpegNarratedVideoMixService service = new FfmpegNarratedVideoMixServiceImpl(properties(), runner);

        assertThatThrownBy(() -> service.mixNarration(new MixNarratedVideoCommand(
                "job-timeout",
                inputVideo,
                narrationAudio,
                output,
                "narrated-video.mp4",
                new BigDecimal("0.35"),
                List.of(new NarrationWindowBo(new BigDecimal("1.000"), new BigDecimal("2.000")))
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("FFmpeg narrated video mix timed out.");
        assertThat(runner.destroyed).isTrue();
    }

    private LinguaFrameProperties properties() {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getFfmpeg().setBinaryPath("ffmpeg");
        properties.getFfmpeg().setBurnInTimeoutSeconds(2);
        return properties;
    }

    private static class RecordingCommandRunner implements FfmpegNarratedVideoMixServiceImpl.CommandRunner {

        private final int firstExitCode;
        private final String stderr;
        private final boolean timeout;
        private final List<List<String>> commands = new java.util.ArrayList<>();
        private List<String> lastCommand;
        private boolean destroyed;

        private RecordingCommandRunner(int firstExitCode, String stderr, boolean timeout) {
            this.firstExitCode = firstExitCode;
            this.stderr = stderr;
            this.timeout = timeout;
        }

        @Override
        public FfmpegNarratedVideoMixServiceImpl.CommandResult run(
                List<String> command,
                Path outputVideoPath,
                int timeoutSeconds
        ) throws IOException {
            this.lastCommand = command;
            this.commands.add(command);
            if (timeout) {
                return new FfmpegNarratedVideoMixServiceImpl.CommandResult(false, -1, "");
            }
            int exitCode = commands.size() == 1 ? firstExitCode : 0;
            if (exitCode == 0) {
                Files.write(outputVideoPath, new byte[] {7, 8, 9});
            }
            return new FfmpegNarratedVideoMixServiceImpl.CommandResult(true, exitCode, stderr);
        }

        @Override
        public void destroy() {
            destroyed = true;
        }
    }
}
