package com.linguaframe.media.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.media.domain.bo.AudioWaveformAnalyzeCommand;
import com.linguaframe.media.domain.bo.AudioWaveformBo;
import com.linguaframe.media.domain.bo.AudioWaveformBucketBo;
import com.linguaframe.media.service.FfmpegAudioWaveformService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class FfmpegAudioWaveformServiceImpl implements FfmpegAudioWaveformService {

    private static final int SAMPLE_RATE = 8000;
    private static final int MAX_ERROR_SUMMARY_LENGTH = 240;

    private final LinguaFrameProperties properties;
    private final CommandRunner commandRunner;

    @Autowired
    public FfmpegAudioWaveformServiceImpl(LinguaFrameProperties properties) {
        this(properties, new ProcessCommandRunner());
    }

    public FfmpegAudioWaveformServiceImpl(LinguaFrameProperties properties, CommandRunner commandRunner) {
        this.properties = properties;
        this.commandRunner = commandRunner;
    }

    @Override
    public AudioWaveformBo analyze(AudioWaveformAnalyzeCommand command) {
        int bucketCount = Math.max(1, command.bucketCount());
        double durationSeconds = Math.max(0.001, command.durationSeconds());
        List<String> ffmpegCommand = List.of(
                properties.getFfmpeg().getBinaryPath(),
                "-v",
                "error",
                "-i",
                command.inputMediaPath().toString(),
                "-vn",
                "-ac",
                "1",
                "-ar",
                String.valueOf(SAMPLE_RATE),
                "-f",
                "s16le",
                "pipe:1"
        );
        CommandResult result;
        try {
            result = commandRunner.run(ffmpegCommand, properties.getFfmpeg().getAudioTimeoutSeconds());
        } catch (InterruptedException ex) {
            commandRunner.destroy();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("FFmpeg waveform analysis failed.", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("FFmpeg waveform analysis failed.", ex);
        }
        if (!result.completed()) {
            commandRunner.destroy();
            throw new IllegalStateException("FFmpeg waveform analysis timed out.");
        }
        if (result.exitCode() != 0) {
            throw new IllegalStateException("FFmpeg waveform analysis failed: " + safeErrorSummary(result.stderr()));
        }
        return new AudioWaveformBo(
                bucketCount,
                decimal(durationSeconds),
                buckets(result.stdout(), bucketCount, durationSeconds)
        );
    }

    private List<AudioWaveformBucketBo> buckets(byte[] rawPcm, int bucketCount, double durationSeconds) {
        short[] samples = samples(rawPcm);
        int samplesPerBucket = Math.max(1, (int) Math.ceil(samples.length / (double) bucketCount));
        List<AudioWaveformBucketBo> buckets = new ArrayList<>(bucketCount);
        for (int index = 0; index < bucketCount; index++) {
            int start = index * samplesPerBucket;
            int end = Math.min(samples.length, start + samplesPerBucket);
            double peak = 0.0;
            double sumSquares = 0.0;
            int count = 0;
            for (int sampleIndex = start; sampleIndex < end; sampleIndex++) {
                double normalized = clamp(Math.abs(samples[sampleIndex]) / 32768.0);
                peak = Math.max(peak, normalized);
                sumSquares += normalized * normalized;
                count++;
            }
            double rms = count == 0 ? 0.0 : Math.sqrt(sumSquares / count);
            double bucketStart = durationSeconds * index / bucketCount;
            double bucketEnd = durationSeconds * (index + 1) / bucketCount;
            buckets.add(new AudioWaveformBucketBo(
                    index,
                    decimal(bucketStart),
                    decimal(bucketEnd),
                    decimal(clamp(peak)),
                    decimal(clamp(rms))
            ));
        }
        return List.copyOf(buckets);
    }

    private short[] samples(byte[] rawPcm) {
        ByteBuffer buffer = ByteBuffer.wrap(rawPcm).order(ByteOrder.LITTLE_ENDIAN);
        short[] samples = new short[rawPcm.length / 2];
        for (int index = 0; index < samples.length; index++) {
            samples[index] = buffer.getShort();
        }
        return samples;
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP);
    }

    private double clamp(double value) {
        if (Double.isNaN(value) || value < 0.0) {
            return 0.0;
        }
        return Math.min(1.0, value);
    }

    private String safeErrorSummary(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return "Unknown FFmpeg failure.";
        }
        String normalized = stderr.replaceAll("\\s+", " ").trim();
        normalized = normalized.replaceAll("(/[A-Za-z0-9._ -]+)+", "[path]");
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
            byte[] stdout,
            String stderr
    ) {
    }

    static class ProcessCommandRunner implements CommandRunner {

        private Process process;

        @Override
        public CommandResult run(List<String> command, int timeoutSeconds) throws IOException, InterruptedException {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            process = processBuilder.start();
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                return new CommandResult(false, -1, new byte[0], "");
            }
            byte[] stdout = process.getInputStream().readAllBytes();
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
