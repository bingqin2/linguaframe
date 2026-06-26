package com.linguaframe.media.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.media.service.MediaWorkDirectoryService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class DefaultMediaWorkDirectoryService implements MediaWorkDirectoryService {

    private final LinguaFrameProperties properties;

    public DefaultMediaWorkDirectoryService(LinguaFrameProperties properties) {
        this.properties = properties;
    }

    @Override
    public Path createJobWorkDirectory(String jobId) {
        Path directory = Path.of(properties.getFfmpeg().getWorkDir(), "jobs", jobId, UUID.randomUUID().toString());
        try {
            return Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create media work directory.", ex);
        }
    }

    @Override
    public void deleteRecursively(Path directory) {
        if (directory == null || Files.notExists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(this::deleteIfExists);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to clean media work directory.", ex);
        }
    }

    private void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to clean media work directory.", ex);
        }
    }
}
