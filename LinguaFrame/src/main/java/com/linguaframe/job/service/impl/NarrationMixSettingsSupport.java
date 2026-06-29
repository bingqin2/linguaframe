package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.entity.NarrationMixSettingsRecord;
import com.linguaframe.job.repository.NarrationMixSettingsRepository;

import java.math.BigDecimal;

final class NarrationMixSettingsSupport {

    static final BigDecimal DEFAULT_DUCKING_VOLUME = new BigDecimal("0.35");
    static final BigDecimal DEFAULT_NARRATION_VOLUME = new BigDecimal("1.00");
    static final int DEFAULT_FADE_DURATION_MS = 250;
    static final String SOURCE_DEFAULTS = "DEFAULTS";
    static final String SOURCE_SAVED = "SAVED";

    private NarrationMixSettingsSupport() {
    }

    static ResolvedNarrationMixSettings resolve(NarrationMixSettingsRepository repository, String jobId) {
        return repository.findByJobId(jobId)
                .map(record -> new ResolvedNarrationMixSettings(
                        record.duckingVolume(),
                        record.narrationVolume(),
                        record.fadeDurationMs(),
                        SOURCE_SAVED
                ))
                .orElse(new ResolvedNarrationMixSettings(
                        DEFAULT_DUCKING_VOLUME,
                        DEFAULT_NARRATION_VOLUME,
                        DEFAULT_FADE_DURATION_MS,
                        SOURCE_DEFAULTS
                ));
    }

    static NarrationMixSettingsRecord defaultRecord(String jobId) {
        return new NarrationMixSettingsRecord(
                jobId,
                DEFAULT_DUCKING_VOLUME,
                DEFAULT_NARRATION_VOLUME,
                DEFAULT_FADE_DURATION_MS,
                null
        );
    }

    record ResolvedNarrationMixSettings(
            BigDecimal duckingVolume,
            BigDecimal narrationVolume,
            int fadeDurationMs,
            String source
    ) {
    }
}
