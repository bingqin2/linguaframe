package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.service.LocalizationJobProgressStreamService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class LocalizationJobProgressStreamServiceImpl implements LocalizationJobProgressStreamService {

    private static final long STREAM_TIMEOUT_MS = 60_000L;
    private static final long SNAPSHOT_INTERVAL_MS = 1_000L;
    private static final int MAX_SNAPSHOTS = 60;
    private static final Set<LocalizationJobStatus> TERMINAL_STATUSES =
            Set.of(LocalizationJobStatus.COMPLETED, LocalizationJobStatus.FAILED, LocalizationJobStatus.CANCELLED);

    private final LocalizationJobQueryService queryService;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public LocalizationJobProgressStreamServiceImpl(LocalizationJobQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    public SseEmitter streamJob(String jobId) {
        LocalizationJobVo initialJob = queryService.getJob(jobId);
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        executorService.submit(() -> streamSnapshots(jobId, initialJob, emitter));
        return emitter;
    }

    private void streamSnapshots(String jobId, LocalizationJobVo initialJob, SseEmitter emitter) {
        try {
            LocalizationJobVo currentJob = initialJob;
            for (int index = 0; index < MAX_SNAPSHOTS; index++) {
                emitter.send(SseEmitter.event().name("job").data(currentJob));
                if (TERMINAL_STATUSES.contains(currentJob.status())) {
                    emitter.complete();
                    return;
                }
                Thread.sleep(SNAPSHOT_INTERVAL_MS);
                currentJob = queryService.getJob(jobId);
            }
            emitter.complete();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            emitter.completeWithError(ex);
        } catch (RuntimeException | IOException ex) {
            emitter.completeWithError(ex);
        }
    }
}
