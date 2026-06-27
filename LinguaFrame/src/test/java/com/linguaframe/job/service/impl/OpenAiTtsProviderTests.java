package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.TtsRequestBo;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.service.ModelCallSummaryService;
import com.linguaframe.job.service.RecordingModelCallAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiTtsProviderTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ModelCallSummaryService summaryService = new ModelCallSummaryServiceImpl();

    @Test
    void synthesizesMp3AudioWithSpeechApi() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = testRestClient(restClientBuilder);
        RecordingModelCallAuditService auditService = new RecordingModelCallAuditService();
        OpenAiTtsProvider provider = new OpenAiTtsProvider(
                openAiProperties("test-openai-key", "gpt-4o-mini-tts", "alloy", "https://api.openai.test", 5),
                restClient,
                objectMapper,
                auditService,
                summaryService
        );

        server.expect(requestTo("https://api.openai.test/v1/audio/speech"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-openai-key"))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini-tts"))
                .andExpect(jsonPath("$.voice").value("alloy"))
                .andExpect(jsonPath("$.input").value("LinguaFrame 向你问好。\n这个演示字幕是确定性的。"))
                .andExpect(jsonPath("$.response_format").value("mp3"))
                .andRespond(withSuccess(new byte[] {1, 2, 3, 4}, MediaType.valueOf("audio/mpeg")));

        var result = provider.synthesize(new TtsRequestBo(
                "tts-job-1",
                "zh-CN",
                "LinguaFrame 向你问好。\n这个演示字幕是确定性的。"
        ));

        assertThat(result.filename()).isEqualTo("dubbing-audio.mp3");
        assertThat(result.contentType()).isEqualTo("audio/mpeg");
        assertThat(result.audioContent()).containsExactly(1, 2, 3, 4);
        assertThat(auditService.successCommands).hasSize(1);
        var command = auditService.successCommands.getFirst();
        assertThat(command.jobId()).isEqualTo("tts-job-1");
        assertThat(command.stage()).isEqualTo(LocalizationJobStage.DUBBING_AUDIO_GENERATION);
        assertThat(command.operation()).isEqualTo(ModelCallOperation.TTS);
        assertThat(command.provider()).isEqualTo(ModelCallProvider.OPENAI);
        assertThat(command.model()).isEqualTo("gpt-4o-mini-tts");
        assertThat(command.promptVersion()).isEqualTo("openai-tts-v1");
        assertThat(command.characterCount()).isEqualTo("LinguaFrame 向你问好。\n这个演示字幕是确定性的。".length());
        assertThat(command.inputSummary()).isEqualTo("characters=30");
        assertThat(command.outputSummary()).isEqualTo("audioBytes=4");
        assertThat(command.inputSummary()).doesNotContain("LinguaFrame 向你问好。");
        assertThat(command.latencyMs()).isGreaterThanOrEqualTo(0L);
        server.verify();
    }

    @Test
    void failsFastWhenOpenAiConfigurationIsMissing() {
        RestClient.Builder restClientBuilder = RestClient.builder();

        assertThatThrownBy(() -> new OpenAiTtsProvider(
                openAiProperties("", "", "", "https://api.openai.test", 5),
                restClientBuilder,
                objectMapper,
                new RecordingModelCallAuditService(),
                summaryService
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI TTS provider requires OPENAI_API_KEY, OPENAI_TTS_MODEL, and OPENAI_TTS_VOICE.");
    }

    @Test
    void wrapsHttpFailuresWithSanitizedStatusMessage() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = testRestClient(restClientBuilder);
        RecordingModelCallAuditService auditService = new RecordingModelCallAuditService();
        OpenAiTtsProvider provider = new OpenAiTtsProvider(
                openAiProperties("test-openai-key", "gpt-4o-mini-tts", "alloy", "https://api.openai.test", 5),
                restClient,
                objectMapper,
                auditService,
                summaryService
        );

        server.expect(requestTo("https://api.openai.test/v1/audio/speech"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"provider failure details\"}}"));

        assertThatThrownBy(() -> provider.synthesize(request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI TTS request failed with status 401");
        assertThat(auditService.failureCommands).hasSize(1);
        assertThat(auditService.failureCommands.getFirst().jobId()).isEqualTo("tts-job-2");
        assertThat(auditService.failureCommands.getFirst().operation()).isEqualTo(ModelCallOperation.TTS);
        assertThat(auditService.failureCommands.getFirst().provider()).isEqualTo(ModelCallProvider.OPENAI);
        assertThat(auditService.failureCommands.getFirst().characterCount()).isEqualTo("LinguaFrame 向你问好。".length());
        assertThat(auditService.failureCommands.getFirst().inputSummary()).isEqualTo("characters=17");
        assertThat(auditService.failureCommands.getFirst().outputSummary()).isNull();
        assertThat(auditService.failureSummaries).containsExactly("OpenAI TTS request failed with status 401");
        server.verify();
    }

    @Test
    void rejectsEmptyAudioResponse() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = testRestClient(restClientBuilder);
        OpenAiTtsProvider provider = new OpenAiTtsProvider(
                openAiProperties("test-openai-key", "gpt-4o-mini-tts", "alloy", "https://api.openai.test", 5),
                restClient,
                objectMapper,
                new RecordingModelCallAuditService(),
                summaryService
        );

        server.expect(requestTo("https://api.openai.test/v1/audio/speech"))
                .andRespond(withSuccess(new byte[0], MediaType.valueOf("audio/mpeg")));

        assertThatThrownBy(() -> provider.synthesize(request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI TTS response was empty.");
        server.verify();
    }

    private LinguaFrameProperties openAiProperties(
            String apiKey,
            String model,
            String voice,
            String baseUrl,
            int timeoutSeconds
    ) {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getTts().setProvider("openai");
        properties.getTts().getOpenai().setApiKey(apiKey);
        properties.getTts().getOpenai().setModel(model);
        properties.getTts().getOpenai().setVoice(voice);
        properties.getTts().getOpenai().setBaseUrl(baseUrl);
        properties.getTts().getOpenai().setTimeoutSeconds(timeoutSeconds);
        return properties;
    }

    private RestClient testRestClient(RestClient.Builder restClientBuilder) {
        return restClientBuilder
                .baseUrl("https://api.openai.test")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-openai-key")
                .build();
    }

    private TtsRequestBo request() {
        return new TtsRequestBo("tts-job-2", "zh-CN", "LinguaFrame 向你问好。");
    }
}
