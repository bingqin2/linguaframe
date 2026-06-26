package com.linguaframe.media.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.media.domain.bo.MediaDurationProbeCommand;
import com.linguaframe.media.domain.bo.MediaDurationProbeResult;
import com.linguaframe.media.service.MediaDurationProbeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class FfprobeMediaDurationProbeService implements MediaDurationProbeService {

    private static final int PROBE_TIMEOUT_SECONDS = 30;
    private static final int MAX_ERROR_SUMMARY_LENGTH = 240;

    private final LinguaFrameProperties properties;
    private final CommandRunner commandRunner;

    @Autowired
    public FfprobeMediaDurationProbeService(LinguaFrameProperties properties) {
        this(properties, new ProcessCommandRunner());
    }

    public FfprobeMediaDurationProbeService(LinguaFrameProperties properties, CommandRunner commandRunner) {
        this.properties = properties;
        this.commandRunner = commandRunner;
    }

    @Override
    public MediaDurationProbeResult probeDuration(MediaDurationProbeCommand command) {
        List<String> probeCommand = List.of(
                ffprobeBinaryPath(),
                "-v",
                "error",
                "-show_entries",
                "format=duration",
                "-of",
                "default=noprint_wrappers=1:nokey=1",
                command.inputVideoPath().toString()
        );

        CommandResult result;
        try {
            result = commandRunner.run(probeCommand, PROBE_TIMEOUT_SECONDS);
        } catch (InterruptedException ex) {
            commandRunner.destroy();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("FFprobe duration probe failed.", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("FFprobe duration probe failed.", ex);
        }

        if (!result.completed()) {
            commandRunner.destroy();
            throw new IllegalStateException("FFprobe duration probe timed out.");
        }
        if (result.exitCode() != 0) {
            throw new IllegalStateException("FFprobe duration probe failed: " + safeErrorSummary(result.stderr()));
        }

        return new MediaDurationProbeResult(parseDurationSeconds(result.stdout()));
    }

    private double parseDurationSeconds(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            throw new IllegalStateException("FFprobe duration probe failed: missing duration output.");
        }
        try {
            return Double.parseDouble(stdout.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("FFprobe duration probe failed: invalid duration output.", ex);
        }
    }

    private String ffprobeBinaryPath() {
        String binaryPath = properties.getFfmpeg().getBinaryPath();
        String normalized = binaryPath.toLowerCase(Locale.ROOT);
        if (normalized.equals("ffmpeg")) {
            return "ffprobe";
        }
        if (normalized.endsWith("/ffmpeg")) {
            return binaryPath.substring(0, binaryPath.length() - "ffmpeg".length()) + "ffprobe";
        }
        return "ffprobe";
    }

    private String safeErrorSummary(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return "Unknown FFprobe failure.";
        }
        String normalized = stderr.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_ERROR_SUMMARY_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_ERROR_SUMMARY_LENGTH);
    }

    public interface CommandRunner {

        CommandResult run(List<String> command, int timeoutSeconds) throws IOException, InterruptedException;

        void destroy();
    }

    public record CommandResult(
            boolean completed,
            int exitCode,
            String stdout,
            String stderr
    ) {
    }

    static class ProcessCommandRunner implements CommandRunner {

        private Process process;

        @Override
        public CommandResult run(List<String> command, int timeoutSeconds) throws IOException, InterruptedException {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            process = processBuilder.start();
            boolean completed = process.waitFor(Duration.ofSeconds(timeoutSeconds).toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                return new CommandResult(false, -1, "", "");
            }
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            return new CommandResult(true, process.exitValue(), stdout, stderr);
        }

        @Override
        public void destroy() {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
