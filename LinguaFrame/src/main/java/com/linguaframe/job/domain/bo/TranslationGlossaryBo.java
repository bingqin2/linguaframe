package com.linguaframe.job.domain.bo;

import java.util.List;

public record TranslationGlossaryBo(
        List<TranslationGlossaryEntryBo> entries,
        String json,
        String hash,
        int entryCount
) {

    public static TranslationGlossaryBo empty() {
        return new TranslationGlossaryBo(List.of(), "[]", "", 0);
    }
}
