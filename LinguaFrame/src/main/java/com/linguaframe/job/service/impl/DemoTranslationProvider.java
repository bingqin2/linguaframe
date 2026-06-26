package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.bo.TranslationResultBo;
import com.linguaframe.job.domain.bo.TranslationSegmentBo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.service.TranslationProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DemoTranslationProvider implements TranslationProvider {

    private static final Map<String, String> ZH_CN_TRANSLATIONS = Map.of(
            "Hello from LinguaFrame.", "LinguaFrame 向你问好。",
            "This demo transcript is deterministic.", "这个演示字幕是确定性的。"
    );

    @Override
    public TranslationResultBo translate(String jobId, String targetLanguage, List<TranscriptSegmentVo> transcriptSegments) {
        List<TranslationSegmentBo> segments = transcriptSegments.stream()
                .map(segment -> new TranslationSegmentBo(
                        segment.index(),
                        segment.startMs(),
                        segment.endMs(),
                        translateText(targetLanguage, segment.text())
                ))
                .toList();
        return new TranslationResultBo(segments);
    }

    private String translateText(String targetLanguage, String sourceText) {
        if ("zh-CN".equals(targetLanguage) && ZH_CN_TRANSLATIONS.containsKey(sourceText)) {
            return ZH_CN_TRANSLATIONS.get(sourceText);
        }
        return "[" + targetLanguage + "] " + sourceText;
    }
}
