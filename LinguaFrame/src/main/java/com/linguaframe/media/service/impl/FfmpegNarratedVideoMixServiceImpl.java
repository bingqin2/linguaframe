package com.linguaframe.media.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.media.domain.bo.DubbedVideoBo;
import com.linguaframe.media.domain.bo.MixNarratedVideoCommand;
import com.linguaframe.media.domain.bo.NarrationWindowBo;
import com.linguaframe.media.service.FfmpegNarratedVideoMixService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class FfmpegNarratedVideoMixServiceImpl implements FfmpegNarratedVideoMixService {

    private static final int MAX_ERROR_SUMMARY_LENGTH = 240;

    private final LinguaFrameProperties properties;
    private final CommandRunner commandRunner;

    @Autowired
    public FfmpegNarratedVideoMixServiceImpl(LinguaFrameProperties properties) {
        this(properties, new ProcessCommandRunner());
    }

    public FfmpegNarratedVideoMixServiceImpl(LinguaFrameProperties properties, CommandRunner commandRunner) {
        this.properties = properties;
        this.commandRunner = commandRunner;
    }

    @Override
    public DubbedVideoBo mixNarration(MixNarratedVideoCommand command) {
        CommandResult result = runCommand(mixedCommand(command), command.outputVideoPath());
        if (result.exitCode() != 0 && isMissingBaseAudio(result.stderr())) {
            result = runCommand(narrationOnlyCommand(command), command.outputVideoPath());
        }
        if (result.exitCode() != 0) {
            throw new IllegalStateException("FFmpeg narrated video mix failed: " + safeErrorSummary(result.stderr()));
        }
        try {
            return new DubbedVideoBo(
                    command.outputFilename(),
                    "video/mp4",
                    Files.readAllBytes(command.outputVideoPath())
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read narrated video.", ex);
        }
    }

    private CommandResult runCommand(List<String> ffmpegCommand, Path outputVideoPath) {
        CommandResult result;
        try {
            result = commandRunner.run(
                    ffmpegCommand,
                    outputVideoPath,
                    properties.getFfmpeg().getBurnInTimeoutSeconds()
            );
        } catch (InterruptedException ex) {
            commandRunner.destroy();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("FFmpeg narrated video mix failed.", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("FFmpeg narrated video mix failed.", ex);
        }
        if (!result.completed()) {
            commandRunner.destroy();
            throw new IllegalStateException("FFmpeg narrated video mix timed out.");
        }
        return result;
    }

    private List<String> mixedCommand(MixNarratedVideoCommand command) {
        List<String> ffmpegCommand = baseInputs(command);
        ffmpegCommand.add("-filter_complex");
        ffmpegCommand.add(mixFilter(command));
        ffmpegCommand.add("-map");
        ffmpegCommand.add("0:v:0");
        ffmpegCommand.add("-map");
        ffmpegCommand.add("[aout]");
        ffmpegCommand.add("-c:v");
        ffmpegCommand.add("copy");
        ffmpegCommand.add("-c:a");
        ffmpegCommand.add("aac");
        ffmpegCommand.add("-movflags");
        ffmpegCommand.add("+faststart");
        ffmpegCommand.add(command.outputVideoPath().toString());
        return ffmpegCommand;
    }

    private List<String> narrationOnlyCommand(MixNarratedVideoCommand command) {
        List<String> ffmpegCommand = baseInputs(command);
        ffmpegCommand.add("-map");
        ffmpegCommand.add("0:v:0");
        ffmpegCommand.add("-map");
        ffmpegCommand.add("1:a:0");
        ffmpegCommand.add("-c:v");
        ffmpegCommand.add("copy");
        ffmpegCommand.add("-c:a");
        ffmpegCommand.add("aac");
        ffmpegCommand.add("-shortest");
        ffmpegCommand.add("-movflags");
        ffmpegCommand.add("+faststart");
        ffmpegCommand.add(command.outputVideoPath().toString());
        return ffmpegCommand;
    }

    private List<String> baseInputs(MixNarratedVideoCommand command) {
        List<String> ffmpegCommand = new ArrayList<>();
        ffmpegCommand.add(properties.getFfmpeg().getBinaryPath());
        ffmpegCommand.add("-y");
        ffmpegCommand.add("-i");
        ffmpegCommand.add(command.inputVideoPath().toString());
        ffmpegCommand.add("-i");
        ffmpegCommand.add(command.narrationAudioPath().toString());
        return ffmpegCommand;
    }

    private String mixFilter(MixNarratedVideoCommand command) {
        return "[0:a]volume='if("
                + duckingCondition(command.narrationWindows())
                + ","
                + formatDecimal(command.duckingVolume())
                + ",1.0)'[base];[1:a]"
                + narrationFilter(command)
                + "[narration];[base][narration]amix=inputs=2:duration=longest:normalize=0[aout]";
    }

    private String narrationFilter(MixNarratedVideoCommand command) {
        List<String> filters = new ArrayList<>();
        filters.addAll(fadeFilters(command.narrationWindows(), command.fadeDurationMs()));
        filters.add("volume=" + formatDecimal(command.narrationVolume()));
        return String.join(",", filters);
    }

    private List<String> fadeFilters(List<NarrationWindowBo> windows, int fadeDurationMs) {
        if (fadeDurationMs <= 0 || windows == null || windows.isEmpty()) {
            return List.of();
        }
        BigDecimal requestedSeconds = BigDecimal.valueOf(fadeDurationMs)
                .divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
        List<String> filters = new ArrayList<>();
        for (NarrationWindowBo window : windows) {
            BigDecimal windowDuration = window.endSeconds().subtract(window.startSeconds());
            if (windowDuration.signum() <= 0) {
                continue;
            }
            BigDecimal halfWindow = windowDuration.divide(BigDecimal.valueOf(2), 3, RoundingMode.HALF_UP);
            BigDecimal fadeSeconds = requestedSeconds.min(halfWindow);
            if (fadeSeconds.signum() <= 0) {
                continue;
            }
            BigDecimal fadeOutStart = window.endSeconds().subtract(fadeSeconds);
            filters.add("afade=t=in:st=" + formatSeconds(window.startSeconds()) + ":d=" + formatSeconds(fadeSeconds));
            filters.add("afade=t=out:st=" + formatSeconds(fadeOutStart) + ":d=" + formatSeconds(fadeSeconds));
        }
        return filters;
    }

    private String duckingCondition(List<NarrationWindowBo> windows) {
        if (windows == null || windows.isEmpty()) {
            return "0";
        }
        return windows.stream()
                .map(window -> "between(t," + formatSeconds(window.startSeconds()) + "," + formatSeconds(window.endSeconds()) + ")")
                .reduce((left, right) -> left + "+" + right)
                .orElse("0");
    }

    private String formatSeconds(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatDecimal(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private boolean isMissingBaseAudio(String stderr) {
        return stderr != null && stderr.contains("Stream map '0:a' matches no streams");
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
