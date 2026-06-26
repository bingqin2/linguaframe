package com.linguaframe;

import com.linguaframe.job.service.TtsProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "linguaframe.tts.provider=openai",
        "linguaframe.tts.openai.api-key=test-openai-key",
        "linguaframe.tts.openai.model=gpt-4o-mini-tts",
        "linguaframe.tts.openai.voice=alloy"
})
class OpenAiTtsContextTests {

    @Autowired
    private List<TtsProvider> ttsProviders;

    @Test
    void contextLoadsWithOpenAiTtsProvider() {
        assertThat(ttsProviders).hasSize(1);
    }
}
