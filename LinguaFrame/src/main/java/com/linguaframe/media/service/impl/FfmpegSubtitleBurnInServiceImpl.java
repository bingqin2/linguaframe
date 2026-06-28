package com.linguaframe.media.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.enums.SubtitleStylePreset;
import com.linguaframe.media.domain.bo.BurnInSubtitlesCommand;
import com.linguaframe.media.domain.bo.BurnedVideoBo;
import com.linguaframe.media.service.FfmpegSubtitleBurnInService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class FfmpegSubtitleBurnInServiceImpl implements FfmpegSubtitleBurnInService {

    private static final int MAX_ERROR_SUMMARY_LENGTH = 240;

    private final LinguaFrameProperties properties;
    private final CommandRunner commandRunner;

    @Autowired
    public FfmpegSubtitleBurnInServiceImpl(LinguaFrameProperties properties) {
        this(properties, new ProcessCommandRunner());
    }

    public FfmpegSubtitleBurnInServiceImpl(LinguaFrameProperties properties, CommandRunner commandRunner) {
        this.properties = properties;
        this.commandRunner = commandRunner;
    }

    @Override
    public BurnedVideoBo burnInSubtitles(BurnInSubtitlesCommand command) {
        List<String> ffmpegCommand = List.of(
                properties.getFfmpeg().getBinaryPath(),
                "-y",
                "-i",
                command.inputVideoPath().toString(),
                "-vf",
                subtitleFilter(command),
                "-c:v",
                "libx264",
                "-preset",
                "veryfast",
                "-c:a",
                "copy",
                "-movflags",
                "+faststart",
                command.outputVideoPath().toString()
        );
        CommandResult result;
        try {
            result = commandRunner.run(
                    ffmpegCommand,
                    command.outputVideoPath(),
                    properties.getFfmpeg().getBurnInTimeoutSeconds()
            );
        } catch (InterruptedException ex) {
            commandRunner.destroy();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("FFmpeg subtitle burn-in failed.", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("FFmpeg subtitle burn-in failed.", ex);
        }
        if (!result.completed()) {
            commandRunner.destroy();
            throw new IllegalStateException("FFmpeg subtitle burn-in timed out.");
        }
        if (result.exitCode() != 0) {
            throw new IllegalStateException("FFmpeg subtitle burn-in failed: " + safeErrorSummary(result.stderr()));
        }
        try {
            return new BurnedVideoBo(
                    "burned-video.mp4",
                    "video/mp4",
                    Files.readAllBytes(command.outputVideoPath())
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read burned video.", ex);
        }
    }

    private String safeErrorSummary(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return "Unknown FFmpeg failure.";
        }
        String normalized = stderr.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_ERROR_SUMMARY_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_ERROR_SUMMARY_LENGTH);
    }

    private String subtitleFilter(BurnInSubtitlesCommand command) {
        SubtitleStylePreset preset = SubtitleStylePreset.parse(command.subtitleStylePreset());
        return "subtitles=" + quoteFilterValue(command.subtitlePath().toAbsolutePath().toString())
                + ":force_style=" + quoteFilterValue(preset.ffmpegForceStyle());
    }

    private String quoteFilterValue(String value) {
        return "'" + value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace(":", "\\:")
                .replace(",", "\\,")
                + "'";
    }

    public interface CommandRunner {

        CommandResult run(List<String> command, Path outputVideoPath, int timeoutSeconds)
                throws IOException, InterruptedException;

        void destroy();
    }

    public record CommandResult(
            boolean completed,
            int exitCode,
            String stderr
    ) {
    }

    static class ProcessCommandRunner implements CommandRunner {

        private Process process;

        @Override
        public CommandResult run(List<String> command, Path outputVideoPath, int timeoutSeconds)
                throws IOException, InterruptedException {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            process = processBuilder.start();
            boolean completed = process.waitFor(Duration.ofSeconds(timeoutSeconds).toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                return new CommandResult(false, -1, "");
            }
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            return new CommandResult(true, process.exitValue(), stderr);
        }

        @Override
        public void destroy() {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
