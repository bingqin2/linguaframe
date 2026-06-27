package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.domain.enums.PromptTemplatePurpose;
import com.linguaframe.job.domain.vo.PromptTemplateVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.service.ModelCallSummaryService;
import com.linguaframe.job.service.PromptTemplateRegistry;
import com.linguaframe.job.service.RecordingModelCallAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

class OpenAiTranslationProviderTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ModelCallSummaryService summaryService = new ModelCallSummaryServiceImpl();

    @Test
    void translatesWithResponsesApiAndPreservesTiming() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = testRestClient(restClientBuilder);
        RecordingModelCallAuditService auditService = new RecordingModelCallAuditService();
        OpenAiTranslationProvider provider = new OpenAiTranslationProvider(
                openAiProperties("test-openai-key", "test-translation-model", "https://api.openai.test", 5),
                restClient,
                objectMapper,
                auditService,
                testRegistry("test-translation-template-v9", "Test translation prompt."),
                summaryService
        );

        server.expect(requestTo("https://api.openai.test/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-openai-key"))
                .andExpect(jsonPath("$.model").value("test-translation-model"))
                .andExpect(jsonPath("$.input[0].content[0].text").value("Test translation prompt."))
                .andExpect(jsonPath("$.input[1].content[0].text").exists())
                .andExpect(jsonPath("$.text.format.schema.properties.segments.type").value("array"))
                .andRespond(withSuccess(responsesPayload("""
                        {"segments":[{"index":0,"text":"LinguaFrame 向你问好。"},{"index":1,"text":"这个演示字幕来自 OpenAI。"}]}
                        """), MediaType.APPLICATION_JSON));

        var result = provider.translate("translation-job-1", "zh-CN", List.of(
                new TranscriptSegmentVo(0, 0L, 1_800L, "Hello from LinguaFrame."),
                new TranscriptSegmentVo(1, 1_800L, 3_600L, "This demo transcript is deterministic.")
        ));

        assertThat(result.segments())
                .extracting(segment -> segment.index() + ":" + segment.startMs() + ":" + segment.endMs() + ":" + segment.text())
                .containsExactly(
                        "0:0:1800:LinguaFrame 向你问好。",
                        "1:1800:3600:这个演示字幕来自 OpenAI。"
                );
        assertThat(auditService.successCommands).hasSize(1);
        var command = auditService.successCommands.getFirst();
        assertThat(command.jobId()).isEqualTo("translation-job-1");
        assertThat(command.stage()).isEqualTo(LocalizationJobStage.TARGET_SUBTITLE_EXPORT);
        assertThat(command.operation()).isEqualTo(ModelCallOperation.TRANSLATION);
        assertThat(command.provider()).isEqualTo(ModelCallProvider.OPENAI);
        assertThat(command.model()).isEqualTo("test-translation-model");
        assertThat(command.promptVersion()).isEqualTo("test-translation-template-v9");
        assertThat(command.inputTokens()).isEqualTo(1000);
        assertThat(command.outputTokens()).isEqualTo(500);
        assertThat(command.inputSummary()).isEqualTo("target=zh-CN, segments=2, sourceChars=61");
        assertThat(command.outputSummary()).isEqualTo("segments=2, targetChars=33");
        assertThat(command.inputSummary()).doesNotContain("Hello from LinguaFrame.");
        assertThat(command.outputSummary()).doesNotContain("LinguaFrame 向你问好。");
        assertThat(command.latencyMs()).isGreaterThanOrEqualTo(0L);
        server.verify();
    }

    @Test
    void failsFastWhenOpenAiConfigurationIsMissing() {
        RestClient.Builder restClientBuilder = RestClient.builder();

        assertThatThrownBy(() -> new OpenAiTranslationProvider(
                openAiProperties("", "", "https://api.openai.test", 5),
                restClientBuilder,
                objectMapper,
                new RecordingModelCallAuditService(),
                defaultRegistry(),
                summaryService
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI translation provider requires OPENAI_API_KEY and OPENAI_TRANSLATION_MODEL.");
    }

    @Test
    void wrapsHttpFailuresWithSanitizedStatusMessage() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = testRestClient(restClientBuilder);
        RecordingModelCallAuditService auditService = new RecordingModelCallAuditService();
        OpenAiTranslationProvider provider = new OpenAiTranslationProvider(
                openAiProperties("test-openai-key", "test-translation-model", "https://api.openai.test", 5),
                restClient,
                objectMapper,
                auditService,
                defaultRegistry(),
                summaryService
        );

        server.expect(requestTo("https://api.openai.test/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"provider failure details\"}}"));

        assertThatThrownBy(() -> provider.translate("translation-job-2", "zh-CN", oneSegment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI translation request failed with status 401");
        assertThat(auditService.failureCommands).hasSize(1);
        assertThat(auditService.failureCommands.getFirst().jobId()).isEqualTo("translation-job-2");
        assertThat(auditService.failureCommands.getFirst().operation()).isEqualTo(ModelCallOperation.TRANSLATION);
        assertThat(auditService.failureCommands.getFirst().provider()).isEqualTo(ModelCallProvider.OPENAI);
        assertThat(auditService.failureCommands.getFirst().inputSummary()).isEqualTo("target=zh-CN, segments=1, sourceChars=23");
        assertThat(auditService.failureCommands.getFirst().outputSummary()).isNull();
        assertThat(auditService.failureSummaries).containsExactly("OpenAI translation request failed with status 401");
        server.verify();
    }

    @Test
    void rejectsNonJsonOutputText() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = testRestClient(restClientBuilder);
        OpenAiTranslationProvider provider = new OpenAiTranslationProvider(
                openAiProperties("test-openai-key", "test-translation-model", "https://api.openai.test", 5),
                restClient,
                objectMapper,
                new RecordingModelCallAuditService(),
                defaultRegistry(),
                summaryService
        );
        server.expect(requestTo("https://api.openai.test/v1/responses"))
                .andRespond(withSuccess(responsesPayload("not json"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.translate("translation-job-3", "zh-CN", oneSegment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI translation response was not valid JSON.");
        server.verify();
    }

    @Test
    void rejectsMissingOutputText() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = testRestClient(restClientBuilder);
        OpenAiTranslationProvider provider = new OpenAiTranslationProvider(
                openAiProperties("test-openai-key", "test-translation-model", "https://api.openai.test", 5),
                restClient,
                objectMapper,
                new RecordingModelCallAuditService(),
                defaultRegistry(),
                summaryService
        );
        server.expect(requestTo("https://api.openai.test/v1/responses"))
                .andRespond(withSuccess("{\"id\":\"resp_1\",\"output\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.translate("translation-job-4", "zh-CN", oneSegment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI translation response did not contain text output.");
        server.verify();
    }

    @Test
    void rejectsMismatchedSegmentCount() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = testRestClient(restClientBuilder);
        OpenAiTranslationProvider provider = new OpenAiTranslationProvider(
                openAiProperties("test-openai-key", "test-translation-model", "https://api.openai.test", 5),
                restClient,
                objectMapper,
                new RecordingModelCallAuditService(),
                defaultRegistry(),
                summaryService
        );
        server.expect(requestTo("https://api.openai.test/v1/responses"))
                .andRespond(withSuccess(responsesPayload("{\"segments\":[{\"index\":0,\"text\":\"Only one\"}]}"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.translate("translation-job-5", "zh-CN", List.of(
                new TranscriptSegmentVo(0, 0L, 1_000L, "First."),
                new TranscriptSegmentVo(1, 1_000L, 2_000L, "Second.")
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI translation returned 1 segments for 2 source segments.");
        server.verify();
    }

    @Test
    void rejectsBlankTranslatedText() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = testRestClient(restClientBuilder);
        OpenAiTranslationProvider provider = new OpenAiTranslationProvider(
                openAiProperties("test-openai-key", "test-translation-model", "https://api.openai.test", 5),
                restClient,
                objectMapper,
                new RecordingModelCallAuditService(),
                defaultRegistry(),
                summaryService
        );
        server.expect(requestTo("https://api.openai.test/v1/responses"))
                .andRespond(withSuccess(responsesPayload("{\"segments\":[{\"index\":0,\"text\":\"   \"}]}"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.translate("translation-job-6", "zh-CN", oneSegment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI translation returned blank text for segment 0.");
        server.verify();
    }

    @Test
    void rejectsUnknownSegmentIndex() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = testRestClient(restClientBuilder);
        OpenAiTranslationProvider provider = new OpenAiTranslationProvider(
                openAiProperties("test-openai-key", "test-translation-model", "https://api.openai.test", 5),
                restClient,
                objectMapper,
                new RecordingModelCallAuditService(),
                defaultRegistry(),
                summaryService
        );
        server.expect(requestTo("https://api.openai.test/v1/responses"))
                .andRespond(withSuccess(responsesPayload("{\"segments\":[{\"index\":9,\"text\":\"Unknown\"}]}"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.translate("translation-job-7", "zh-CN", oneSegment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI translation returned an unknown segment index: 9.");
        server.verify();
    }

    private LinguaFrameProperties openAiProperties(String apiKey, String model, String baseUrl, int timeoutSeconds) {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getTranslation().setProvider("openai");
        properties.getTranslation().getOpenai().setApiKey(apiKey);
        properties.getTranslation().getOpenai().setModel(model);
        properties.getTranslation().getOpenai().setBaseUrl(baseUrl);
        properties.getTranslation().getOpenai().setTimeoutSeconds(timeoutSeconds);
        return properties;
    }

    private RestClient testRestClient(RestClient.Builder restClientBuilder) {
        return restClientBuilder
                .baseUrl("https://api.openai.test")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-openai-key")
                .build();
    }

    private List<TranscriptSegmentVo> oneSegment() {
        return List.of(new TranscriptSegmentVo(0, 0L, 1_000L, "Hello from LinguaFrame."));
    }

    private String responsesPayload(String outputText) {
        String escapedOutputText = new String(objectMapper.valueToTree(outputText).toString().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        return """
                {
                  "id": "resp_123",
                  "output_text": %s,
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "text": %s
                        }
                      ]
                    }
                  ],
                  "usage": {
                    "input_tokens": 1000,
                    "output_tokens": 500
                  }
                }
                """.formatted(escapedOutputText, escapedOutputText);
    }

    private PromptTemplateRegistry defaultRegistry() {
        return testRegistry(
                "openai-subtitle-translation-v1",
                "You translate subtitle segments for video localization. Translate text only, preserve the meaning and line order, and return JSON only."
        );
    }

    private PromptTemplateRegistry testRegistry(String version, String systemPrompt) {
        PromptTemplateVo template = new PromptTemplateVo(
                version,
                PromptTemplatePurpose.SUBTITLE_TRANSLATION,
                "OPENAI",
                "responses",
                systemPrompt,
                "Return JSON with segments[{index,text}] preserving order and timing.",
                true
        );
        return new PromptTemplateRegistry() {
            @Override
            public PromptTemplateVo activeTemplate(PromptTemplatePurpose purpose) {
                return template;
            }

            @Override
            public List<PromptTemplateVo> listActiveTemplates() {
                return List.of(template);
            }
        };
    }
}
