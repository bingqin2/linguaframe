package com.linguaframe.media.service;

import com.linguaframe.media.service.impl.Sha256SourceMediaFingerprintService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

class SourceMediaFingerprintServiceTests {

    private final SourceMediaFingerprintService service = new Sha256SourceMediaFingerprintService();

    @Test
    void computesLowercaseSha256FromMultipartBytes() {
        MockMultipartFile file = new MockMultipartFile("file", "demo.mp4", "video/mp4", new byte[] {1, 2, 3});

        String fingerprint = service.sha256(file);

        assertThat(fingerprint).isEqualTo("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81");
    }
}
