package com.linguaframe.media.controller;

import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.common.quota.OwnerQuotaPreflightService;
import com.linguaframe.common.quota.OwnerQuotaPreflightVo;
import com.linguaframe.media.domain.vo.MediaUploadDetailVo;
import com.linguaframe.media.domain.vo.MediaUploadValidationVo;
import com.linguaframe.media.domain.vo.MediaUploadVo;
import com.linguaframe.media.service.MediaUploadService;
import com.linguaframe.media.service.MediaUploadValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/media/uploads")
@Tag(name = "Media Uploads", description = "Validate, upload, and inspect source videos for localization jobs.")
public class MediaUploadController {

    private final MediaUploadValidationService validationService;
    private final MediaUploadService uploadService;
    private final OwnerQuotaPreflightService ownerQuotaPreflightService;

    public MediaUploadController(
            MediaUploadValidationService validationService,
            MediaUploadService uploadService,
            OwnerQuotaPreflightService ownerQuotaPreflightService
    ) {
        this.validationService = validationService;
        this.uploadService = uploadService;
        this.ownerQuotaPreflightService = ownerQuotaPreflightService;
    }

    @GetMapping("/preflight")
    @Operation(summary = "Inspect owner upload quota preflight")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Owner upload quota preflight metadata was returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public OwnerQuotaPreflightVo preflightUpload() {
        return ownerQuotaPreflightService.getPreflight();
    }

    @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Validate a source video before upload")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The file is valid for upload."),
            @ApiResponse(responseCode = "400", description = "The file is missing, unsupported, unreadable, too large, or too long."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public ResponseEntity<MediaUploadValidationVo> validateUpload(
            @Parameter(description = "Source video file to validate.", required = true)
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        MediaUploadValidationVo result = validationService.validate(file);
        if (result.valid()) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload media and create a localization job")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The upload was stored and a localization job was created."),
            @ApiResponse(responseCode = "400", description = "The file or requested localization options are invalid."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public ResponseEntity<MediaUploadVo> createUpload(
            @Parameter(description = "Source video file to process.", required = true)
            @RequestPart(value = "file", required = false) MultipartFile file,
            @Parameter(description = "BCP 47 target language code such as zh-CN.")
            @RequestParam(value = "targetLanguage", required = false) String targetLanguage,
            @Parameter(description = "Optional text-to-speech voice identifier.")
            @RequestParam(value = "ttsVoice", required = false) String ttsVoice,
            @Parameter(description = "Optional translation style: NATURAL, FORMAL, or CONCISE.")
            @RequestParam(value = "translationStyle", required = false) String translationStyle,
            @Parameter(description = "Optional subtitle burn-in style preset: STANDARD, LARGE, or HIGH_CONTRAST.")
            @RequestParam(value = "subtitleStylePreset", required = false) String subtitleStylePreset,
            @Parameter(description = "Optional translation glossary, one source-to-target mapping per line.")
            @RequestParam(value = "translationGlossary", required = false) String translationGlossary,
            @Parameter(description = "Optional subtitle polishing mode: OFF, BALANCED, or STRICT.")
            @RequestParam(value = "subtitlePolishingMode", required = false) String subtitlePolishingMode,
            @Parameter(description = "Optional built-in demo run profile id.")
            @RequestParam(value = "demoProfileId", required = false) String demoProfileId
    ) {
        MediaUploadVo upload = uploadService.createUpload(file, targetLanguage, ttsVoice, translationStyle, subtitleStylePreset, translationGlossary, subtitlePolishingMode, demoProfileId);
        return ResponseEntity.status(HttpStatus.CREATED).body(upload);
    }

    @GetMapping("/{videoId}")
    @Operation(summary = "Get uploaded media metadata")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Upload metadata was found."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled."),
            @ApiResponse(responseCode = "404", description = "No uploaded media exists for the supplied video id.")
    })
    public MediaUploadDetailVo getUpload(
            @Parameter(in = ParameterIn.PATH, description = "Uploaded video id.", required = true)
            @PathVariable String videoId
    ) {
        return uploadService.getUpload(videoId);
    }

    @GetMapping("/{videoId}/source/download")
    @Operation(summary = "Download the uploaded source video")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The uploaded source video was found."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled."),
            @ApiResponse(responseCode = "404", description = "No uploaded media exists for the supplied video id.")
    })
    public ResponseEntity<InputStreamResource> downloadSourceMedia(
            @Parameter(in = ParameterIn.PATH, description = "Uploaded video id.", required = true)
            @PathVariable String videoId
    ) {
        StoredObjectResourceBo sourceMedia = uploadService.openSourceMedia(videoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(sourceMedia.contentType()))
                .contentLength(sourceMedia.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(sourceMedia.filename()).build().toString())
                .body(new InputStreamResource(sourceMedia.inputStream()));
    }
}
