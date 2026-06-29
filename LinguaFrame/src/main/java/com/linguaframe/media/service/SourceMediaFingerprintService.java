package com.linguaframe.media.service;

import org.springframework.web.multipart.MultipartFile;

public interface SourceMediaFingerprintService {

    String sha256(MultipartFile file);
}
