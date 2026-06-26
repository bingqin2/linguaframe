package com.linguaframe;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "linguaframe.transcription.provider=openai",
        "linguaframe.transcription.openai.api-key=test-openai-key",
        "linguaframe.transcription.openai.model=whisper-1"
})
class OpenAiTranscriptionContextTests {

    @Test
    void contextLoadsWithOpenAiTranscriptionProvider() {
    }
}
