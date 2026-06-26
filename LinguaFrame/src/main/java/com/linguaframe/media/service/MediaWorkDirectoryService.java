package com.linguaframe.media.service;

import java.nio.file.Path;

public interface MediaWorkDirectoryService {

    Path createJobWorkDirectory(String jobId);

    void deleteRecursively(Path directory);
}
