package com.linguaframe.media.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.TtsResultBo;
import com.linguaframe.media.domain.bo.CreateTimedAudioBedCommand;
import com.linguaframe.media.domain.bo.TimedAudioSegmentBo;
import com.linguaframe.media.service.FfmpegTimedAudioBedService;
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
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class FfmpegTimedAudioBedServiceImpl implements FfmpegTimedAudioBedService {

    private static final int MAX_ERROR_SUMMARY_LENGTH = 240;

    private final LinguaFrameProperties properties;
    private final CommandRunner commandRunner;

    @Autowired
    public FfmpegTimedAudioBedServiceImpl(LinguaFrameProperties properties) {
        this(properties, new ProcessCommandRunner());
    }

    public FfmpegTimedAudioBedServiceImpl(LinguaFrameProperties properties, CommandRunner commandRunner) {
        this.properties = properties;
        this.commandRunner = commandRunner;
    }

    @Override
    public TtsResultBo createAudioBed(CreateTimedAudioBedCommand command) {
        List<TimedAudioSegmentBo> segments = sortedSegments(command);
        List<String> ffmpegCommand = new ArrayList<>();
        ffmpegCommand.add(properties.getFfmpeg().getBinaryPath());
        ffmpegCommand.add("-y");
        for (TimedAudioSegmentBo segment : segments) {
            ffmpegCommand.add("-i");
            ffmpegCommand.add(segment.inputAudioPath().toString());
        }
        ffmpegCommand.add("-filter_complex");
        ffmpegCommand.add(filterGraph(segments));
        ffmpegCommand.add("-map");
        ffmpegCommand.add("[aout]");
        ffmpegCommand.add("-c:a");
        ffmpegCommand.add("libmp3lame");
        ffmpegCommand.add("-q:a");
        ffmpegCommand.add("4");
        ffmpegCommand.add(command.outputAudioPath().toString());

        CommandResult result;
        try {
            result = commandRunner.run(
                    ffmpegCommand,
                    command.outputAudioPath(),
                    properties.getFfmpeg().getAudioTimeoutSeconds()
            );
        } catch (InterruptedException ex) {
            commandRunner.destroy();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("FFmpeg timed narration audio failed.", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("FFmpeg timed narration audio failed.", ex);
        }
        if (!result.completed()) {
            commandRunner.destroy();
            throw new IllegalStateException("FFmpeg timed narration audio timed out.");
        }
        if (result.exitCode() != 0) {
            throw new IllegalStateException("FFmpeg timed narration audio failed: " + safeErrorSummary(result.stderr()));
        }
        try {
            return new TtsResultBo(
                    Files.readAllBytes(command.outputAudioPath()),
                    command.outputFilename(),
                    "audio/mpeg"
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read timed narration audio.", ex);
        }
    }

    private List<TimedAudioSegmentBo> sortedSegments(CreateTimedAudioBedCommand command) {
        if (command.segments() == null || command.segments().isEmpty()) {
            throw new IllegalArgumentException("Timed narration audio requires at least one segment.");
        }
        return command.segments().stream()
                .sorted(Comparator.comparing(TimedAudioSegmentBo::startSeconds))
                .toList();
    }

    private String filterGraph(List<TimedAudioSegmentBo> segments) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            long delayMs = milliseconds(segments.get(i).startSeconds());
            builder.append('[')
                    .append(i)
                    .append(":a]adelay=")
                    .append(delayMs)
                    .append('|')
                    .append(delayMs)
                    .append("[a")
                    .append(i)
                    .append("];");
        }
        String inputs = java.util.stream.IntStream.range(0, segments.size())
                .mapToObj(index -> "[a" + index + "]")
                .collect(Collectors.joining());
        builder.append(inputs)
                .append("amix=inputs=")
                .append(segments.size())
                .append(":normalize=0:duration=longest[aout]");
        return builder.toString();
    }

    private long milliseconds(BigDecimal seconds) {
        return seconds.movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValueExact();
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

        CommandResult run(List<String> command, Path outputAudioPath, int timeoutSeconds)
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
        public CommandResult run(List<String> command, Path outputAudioPath, int timeoutSeconds)
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
