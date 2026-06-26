package com.linguaframe;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "linguaframe.translation.provider=openai",
        "linguaframe.translation.openai.api-key=test-openai-key",
        "linguaframe.translation.openai.model=test-translation-model"
})
class OpenAiTranslationContextTests {

    @Test
    void contextLoadsWithOpenAiTranslationProvider() {
    }
}
