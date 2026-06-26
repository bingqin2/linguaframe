package com.linguaframe.job.controller;

import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.service.JobArtifactService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class LocalizationJobController {

    private final LocalizationJobQueryService queryService;
    private final LocalizationJobRetryService retryService;
    private final JobArtifactService artifactService;
    private final TranscriptService transcriptService;
    private final SubtitleService subtitleService;

    public LocalizationJobController(
            LocalizationJobQueryService queryService,
            LocalizationJobRetryService retryService,
            JobArtifactService artifactService,
            TranscriptService transcriptService,
            SubtitleService subtitleService
    ) {
        this.queryService = queryService;
        this.retryService = retryService;
        this.artifactService = artifactService;
        this.transcriptService = transcriptService;
        this.subtitleService = subtitleService;
    }

    @GetMapping("/{jobId}")
    public LocalizationJobVo getJob(@PathVariable String jobId) {
        return queryService.getJob(jobId);
    }

    @PostMapping("/{jobId}/retry")
    public LocalizationJobVo retryJob(@PathVariable String jobId) {
        return retryService.retryFailedJob(jobId);
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
