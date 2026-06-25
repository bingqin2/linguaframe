package com.linguaframe.media.controller;

import com.linguaframe.media.domain.vo.MediaUploadDetailVo;
import com.linguaframe.media.domain.vo.MediaUploadValidationVo;
import com.linguaframe.media.domain.vo.MediaUploadVo;
import com.linguaframe.media.service.MediaUploadService;
import com.linguaframe.media.service.MediaUploadValidationService;
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
public class MediaUploadController {

    private final MediaUploadValidationService validationService;
    private final MediaUploadService uploadService;

    public MediaUploadController(
            MediaUploadValidationService validationService,
            MediaUploadService uploadService
    ) {
        this.validationService = validationService;
        this.uploadService = uploadService;
    }

    @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaUploadValidationVo> validateUpload(
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        MediaUploadValidationVo result = validationService.validate(file);
        if (result.valid()) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaUploadVo> createUpload(
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "targetLanguage", required = false) String targetLanguage
    ) {
        MediaUploadVo upload = uploadService.createUpload(file, targetLanguage);
        return ResponseEntity.status(HttpStatus.CREATED).body(upload);
    }

    @GetMapping("/{videoId}")
    public MediaUploadDetailVo getUpload(@PathVariable String videoId) {
        return uploadService.getUpload(videoId);
    }
}
