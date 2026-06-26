package com.linguaframe.job.controller;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.LocalizationJobCancellationService;
import com.linguaframe.job.service.LocalizationJobProgressStreamService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.job.service.LocalizationJobRetryService;
import com.linguaframe.job.service.SubtitleService;
import com.linguaframe.job.service.TranscriptService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class LocalizationJobController {

    private final LocalizationJobQueryService queryService;
    private final LocalizationJobRetryService retryService;
    private final LocalizationJobCancellationService cancellationService;
    private final LocalizationJobProgressStreamService progressStreamService;
    private final JobArtifactService artifactService;
    private final TranscriptService transcriptService;
    private final SubtitleService subtitleService;

    public LocalizationJobController(
            LocalizationJobQueryService queryService,
            LocalizationJobRetryService retryService,
            LocalizationJobCancellationService cancellationService,
            LocalizationJobProgressStreamService progressStreamService,
            JobArtifactService artifactService,
            TranscriptService transcriptService,
            SubtitleService subtitleService
    ) {
        this.queryService = queryService;
        this.retryService = retryService;
        this.cancellationService = cancellationService;
        this.progressStreamService = progressStreamService;
        this.artifactService = artifactService;
        this.transcriptService = transcriptService;
        this.subtitleService = subtitleService;
    }

    @GetMapping
    public LocalizationJobListVo listJobs(
            @RequestParam(required = false) LocalizationJobStatus status,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset
    ) {
        return queryService.listJobs(status, limit, offset);
    }

    @GetMapping("/{jobId}")
    public LocalizationJobVo getJob(@PathVariable String jobId) {
        return queryService.getJob(jobId);
    }

    @GetMapping(value = "/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamJobEvents(@PathVariable String jobId) {
        return progressStreamService.streamJob(jobId);
    }

    @PostMapping("/{jobId}/retry")
    public LocalizationJobVo retryJob(@PathVariable String jobId) {
        return retryService.retryFailedJob(jobId);
    }

    @PostMapping("/{jobId}/cancel")
    public LocalizationJobVo cancelJob(@PathVariable String jobId) {
        return cancellationService.cancelJob(jobId);
    }

    @GetMapping("/{jobId}/artifacts")
    public List<JobArtifactVo> listArtifacts(@PathVariable String jobId) {
        return artifactService.listArtifacts(jobId);
    }

    @GetMapping("/{jobId}/transcript")
    public List<TranscriptSegmentVo> listTranscript(@PathVariable String jobId) {
        return transcriptService.listTranscript(jobId);
    }

    @GetMapping("/{jobId}/subtitles/{language}")
    public List<SubtitleSegmentVo> listSubtitles(
            @PathVariable String jobId,
            @PathVariable String language
    ) {
        return subtitleService.listSubtitles(jobId, language);
    }

    @GetMapping("/{jobId}/artifacts/{artifactId}/download")
    public ResponseEntity<InputStreamResource> downloadArtifact(
            @PathVariable String jobId,
            @PathVariable String artifactId
    ) {
        StoredObjectResourceBo resource = artifactService.openArtifact(jobId, artifactId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(resource.contentType()))
                .contentLength(resource.sizeBytes())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(resource.filename())
                                .build()
                                .toString()
                )
                .body(new InputStreamResource(resource.inputStream()));
    }
}
