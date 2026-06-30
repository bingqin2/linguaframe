package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.bo.TtsRequestBo;
import com.linguaframe.job.domain.bo.TtsResultBo;
import com.linguaframe.job.domain.dto.PreviewNarrationSegmentRequestDto;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.NarrationSegmentPreviewVo;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.job.service.NarrationSegmentPreviewService;
import com.linguaframe.job.service.NarrationVoiceCatalogService;
import com.linguaframe.job.service.TtsProvider;
import org.springframework.stereotype.Service;

@Service
public class NarrationSegmentPreviewServiceImpl implements NarrationSegmentPreviewService {

    private static final String PREVIEW_FILENAME = "narration-segment-preview.mp3";
    private static final String DEFAULT_CONTENT_TYPE = "audio/mpeg";
    private static final String SAFETY_NOTE = "Preview calls the configured TTS provider but does not create artifacts.";

    private final LocalizationJobQueryService queryService;
    private final TtsProvider ttsProvider;
    private final NarrationVoiceCatalogService voiceCatalogService;

    public NarrationSegmentPreviewServiceImpl(
            LocalizationJobQueryService queryService,
            TtsProvider ttsProvider,
            NarrationVoiceCatalogService voiceCatalogService
    ) {
        this.queryService = queryService;
        this.ttsProvider = ttsProvider;
        this.voiceCatalogService = voiceCatalogService;
    }

    @Override
    public NarrationSegmentPreviewVo previewSegment(String jobId, PreviewNarrationSegmentRequestDto request) {
        String text = normalize(request == null ? null : request.text());
        if (text == null) {
            throw new IllegalArgumentException("Narration preview text is required.");
        }

        String requestedVoice = normalize(request.voice());
        if (requestedVoice != null && requestedVoice.length() > 64) {
            throw new IllegalArgumentException("Narration preview voice must be 64 characters or fewer.");
        }
        if (requestedVoice != null && !voiceCatalogService.containsVoice(requestedVoice)) {
            throw new IllegalArgumentException("Narration preview voice must be one of the configured presets.");
        }

        LocalizationJobVo job = queryService.getJob(jobId);
        String voice = requestedVoice != null ? requestedVoice : effectiveDefaultVoice(job);
        TtsResultBo result = ttsProvider.synthesize(new TtsRequestBo(
                jobId,
                job.targetLanguage(),
                voice,
                text
        ));

        return new NarrationSegmentPreviewVo(
                result.audioContent(),
                PREVIEW_FILENAME,
                normalize(result.contentType()) == null ? DEFAULT_CONTENT_TYPE : result.contentType().trim(),
                voice,
                text.length(),
                SAFETY_NOTE
        );
    }

    private String effectiveDefaultVoice(LocalizationJobVo job) {
        String jobVoice = normalize(job.ttsVoice());
        return jobVoice == null ? voiceCatalogService.defaultVoice() : jobVoice;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
