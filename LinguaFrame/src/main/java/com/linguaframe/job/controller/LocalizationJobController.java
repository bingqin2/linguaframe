package com.linguaframe.job.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.bo.StoredArtifactArchiveBo;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Localization Jobs", description = "Inspect, stream, retry, cancel, and download localization job outputs.")
public class LocalizationJobController {

    private final LocalizationJobQueryService queryService;
    private final LocalizationJobRetryService retryService;
    private final LocalizationJobCancellationService cancellationService;
    private final LocalizationJobProgressStreamService progressStreamService;
    private final JobArtifactService artifactService;
    private final TranscriptService transcriptService;
    private final SubtitleService subtitleService;
    private final ObjectMapper objectMapper;

    public LocalizationJobController(
            LocalizationJobQueryService queryService,
            LocalizationJobRetryService retryService,
            LocalizationJobCancellationService cancellationService,
            LocalizationJobProgressStreamService progressStreamService,
            JobArtifactService artifactService,
            TranscriptService transcriptService,
            SubtitleService subtitleService,
            ObjectMapper objectMapper
    ) {
        this.queryService = queryService;
        this.retryService = retryService;
        this.cancellationService = cancellationService;
        this.progressStreamService = progressStreamService;
        this.artifactService = artifactService;
        this.transcriptService = transcriptService;
        this.subtitleService = subtitleService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @Operation(summary = "List localization jobs")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jobs were listed."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public LocalizationJobListVo listJobs(
            @Parameter(description = "Optional job status filter.")
            @RequestParam(required = false) LocalizationJobStatus status,
            @Parameter(description = "Maximum number of jobs to return.")
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "Number of jobs to skip.")
            @RequestParam(required = false) Integer offset
    ) {
        return queryService.listJobs(status, limit, offset);
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Get localization job detail")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job detail was found."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled."),
            @ApiResponse(responseCode = "404", description = "No localization job exists for the supplied job id.")
    })
    public LocalizationJobVo getJob(
            @Parameter(in = ParameterIn.PATH, description = "Localization job id.", required = true)
            @PathVariable String jobId
    ) {
        return queryService.getJob(jobId);
    }

    @GetMapping(value = "/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream localization job progress events")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Server-sent event stream was opened."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled."),
            @ApiResponse(responseCode = "404", description = "No localization job exists for the supplied job id.")
    })
    public SseEmitter streamJobEvents(
            @Parameter(in = ParameterIn.PATH, description = "Localization job id.", required = true)
            @PathVariable String jobId
    ) {
        return progressStreamService.streamJob(jobId);
    }

    @PostMapping("/{jobId}/retry")
    @Operation(summary = "Retry a failed localization job")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The failed job was accepted for retry."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled."),
            @ApiResponse(responseCode = "404", description = "No localization job exists for the supplied job id."),
            @ApiResponse(responseCode = "409", description = "The job cannot be retried from its current state.")
    })
    public LocalizationJobVo retryJob(
            @Parameter(in = ParameterIn.PATH, description = "Localization job id.", required = true)
            @PathVariable String jobId
    ) {
        return retryService.retryFailedJob(jobId);
    }

    @PostMapping("/{jobId}/cancel")
    @Operation(summary = "Cancel a queued or running localization job")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The job was canceled or already terminal."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled."),
            @ApiResponse(responseCode = "404", description = "No localization job exists for the supplied job id."),
            @ApiResponse(responseCode = "409", description = "The job cannot be canceled from its current state.")
    })
    public LocalizationJobVo cancelJob(
            @Parameter(in = ParameterIn.PATH, description = "Localization job id.", required = true)
            @PathVariable String jobId
    ) {
        return cancellationService.cancelJob(jobId);
    }

    @GetMapping("/{jobId}/artifacts")
    @Operation(summary = "List downloadable artifacts for a localization job")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Artifacts were listed."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled."),
            @ApiResponse(responseCode = "404", description = "No localization job exists for the supplied job id.")
    })
    public List<JobArtifactVo> listArtifacts(
            @Parameter(in = ParameterIn.PATH, description = "Localization job id.", required = true)
            @PathVariable String jobId
    ) {
        return artifactService.listArtifacts(jobId);
    }

    @GetMapping("/{jobId}/diagnostics/download")
    @Operation(summary = "Download a safe diagnostics report for a localization job")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Diagnostics JSON was generated."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled."),
            @ApiResponse(responseCode = "404", description = "No localization job exists for the supplied job id.")
    })
    public ResponseEntity<byte[]> downloadDiagnosticsReport(
            @Parameter(in = ParameterIn.PATH, description = "Localization job id.", required = true)
            @PathVariable String jobId
    ) throws JsonProcessingException {
        JobDiagnosticsReportVo report = queryService.getDiagnosticsReport(jobId);
        byte[] body = objectMapper.writeValueAsBytes(report);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(body.length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("linguaframe-job-" + jobId + "-diagnostics.json")
                                .build()
                                .toString()
                )
                .body(body);
    }

    @GetMapping("/{jobId}/transcript")
    @Operation(summary = "List transcript segments for a localization job")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transcript segments were listed."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled."),
            @ApiResponse(responseCode = "404", description = "No localization job exists for the supplied job id.")
    })
    public List<TranscriptSegmentVo> listTranscript(
            @Parameter(in = ParameterIn.PATH, description = "Localization job id.", required = true)
            @PathVariable String jobId
    ) {
        return transcriptService.listTranscript(jobId);
    }

    @GetMapping("/{jobId}/subtitles/{language}")
    @Operation(summary = "List subtitle segments for a localization job and language")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subtitle segments were listed."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled."),
            @ApiResponse(responseCode = "404", description = "No localization job or subtitle language exists for the supplied ids.")
    })
    public List<SubtitleSegmentVo> listSubtitles(
            @Parameter(in = ParameterIn.PATH, description = "Localization job id.", required = true)
            @PathVariable String jobId,
            @Parameter(in = ParameterIn.PATH, description = "Subtitle language code such as zh-CN.", required = true)
            @PathVariable String language
    ) {
        return subtitleService.listSubtitles(jobId, language);
    }

    @GetMapping("/{jobId}/artifacts/{artifactId}/download")
    @Operation(summary = "Download one localization job artifact")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Artifact bytes were opened for download."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled."),
            @ApiResponse(responseCode = "404", description = "No job or artifact exists for the supplied ids.")
    })
    public ResponseEntity<InputStreamResource> downloadArtifact(
            @Parameter(in = ParameterIn.PATH, description = "Localization job id.", required = true)
            @PathVariable String jobId,
            @Parameter(in = ParameterIn.PATH, description = "Artifact id.", required = true)
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

    @GetMapping("/{jobId}/artifacts/archive/download")
    @Operation(summary = "Download a ZIP archive of localization job artifacts")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Artifact archive bytes were opened for download."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled."),
            @ApiResponse(responseCode = "404", description = "No localization job exists for the supplied job id.")
    })
    public ResponseEntity<InputStreamResource> downloadArtifactArchive(
            @Parameter(in = ParameterIn.PATH, description = "Localization job id.", required = true)
            @PathVariable String jobId
    ) {
        StoredArtifactArchiveBo archive = artifactService.openArtifactArchive(jobId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archive.contentType()))
                .contentLength(archive.sizeBytes())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(archive.filename())
                                .build()
                                .toString()
                )
                .body(new InputStreamResource(archive.inputStream()));
    }
}
