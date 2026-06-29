package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.NarrationVoiceCatalogVo;

public interface NarrationVoiceCatalogService {

    NarrationVoiceCatalogVo catalog();

    default boolean containsVoice(String voice) {
        if (voice == null || voice.isBlank()) {
            return true;
        }
        String normalized = voice.trim();
        return catalog().presets().stream()
                .anyMatch(preset -> preset.voice().equals(normalized));
    }

    default String defaultVoice() {
        return catalog().defaultVoice();
    }
}
