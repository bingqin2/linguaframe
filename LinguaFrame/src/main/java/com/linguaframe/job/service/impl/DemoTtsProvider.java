package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.bo.TtsRequestBo;
import com.linguaframe.job.domain.bo.TtsResultBo;
import com.linguaframe.job.service.TtsProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(prefix = "linguaframe.tts", name = "provider", havingValue = "demo", matchIfMissing = true)
public class DemoTtsProvider implements TtsProvider {

    @Override
    public TtsResultBo synthesize(TtsRequestBo request) {
        byte[] audio = ("LINGUAFRAME_DEMO_DUBBING_AUDIO\n" + request.language() + "\n" + request.text())
                .getBytes(StandardCharsets.UTF_8);
        return new TtsResultBo(audio, "dubbing-audio.mp3", "audio/mpeg");
    }
}
