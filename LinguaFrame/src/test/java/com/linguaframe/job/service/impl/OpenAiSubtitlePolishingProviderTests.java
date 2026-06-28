package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.domain.enums.PromptTemplatePurpose;
import com.linguaframe.job.domain.vo.PromptTemplateVo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiSubtitlePolishingProviderTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ModelCallSummaryService summaryService = new ModelCallSummaryServiceImpl();

    @Test
    void polishesWithResponsesApiAndRecordsSeparateAuditOperation() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = testRestClient(restClientBuilder);
        RecordingModelCallAuditService auditService = new RecordingModelCallAuditService();
        OpenAiSubtitlePolishingProvider provider = new OpenAiSubtitlePolishingProvider(
                openAiProperties("test-openai-key", "test-polishing-model", "https://api.openai.test", 5),
                restClient,
                objectMapper,
                auditService,
                testRegistry("test-polishing-template-v3", "Polish subtitles."),
                summaryService
        );

        server.expect(requestTo("https://api.openai.test/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-openai-key"))
                .andExpect(jsonPath("$.model").value("test-polishing-model"))
                .andExpect(jsonPath("$.input[0].content[0].text").value("Polish subtitles."))
                .andExpect(jsonPath("$.input[1].content[0].text").value(org.hamcrest.Matchers.containsString("\"subtitlePolishingMode\":\"BALANCED\"")))
                .andExpect(jsonPath("$.input[1].content[0].text").value(org.hamcrest.Matchers.containsString("\"targetLanguage\":\"zh-CN\"")))
                .andRespond(withSuccess(responsesPayload("""
                        {"segments":[{"index":0,"text":"更自然的字幕。"}]}
                        """), MediaType.APPLICATION_JSON));

        var result = provider.polish(
                "polishing-job-1",
                "zh-CN",
                "BALANCED",
                List.of(new SubtitleSegmentVo("zh-CN", 0, 0L, 1_000L, "直译 字幕。"))
        );

        assertThat(result.segments())
                .extracting(segment -> segment.index() + ":" + segment.startMs() + ":" + segment.endMs() + ":" + segment.text())
                .containsExactly("0:0:1000:更自然的字幕。");
        assertThat(auditService.successCommands).hasSize(1);
        var command = auditService.successCommands.getFirst();
        assertThat(command.jobId()).isEqualTo("polishing-job-1");
        assertThat(command.stage()).isEqualTo(LocalizationJobStage.SUBTITLE_POLISHING);
        assertThat(command.operation()).isEqualTo(ModelCallOperation.SUBTITLE_POLISHING);
        assertThat(command.provider()).isEqualTo(ModelCallProvider.OPENAI);
        assertThat(command.model()).isEqualTo("test-polishing-model");
        assertThat(command.promptVersion()).isEqualTo("test-polishing-template-v3");
        assertThat(command.inputTokens()).isEqualTo(1000);
        assertThat(command.outputTokens()).isEqualTo(500);
        assertThat(command.inputSummary()).isEqualTo("target=zh-CN, style=BALANCED, segments=1, sourceChars=6");
        assertThat(command.outputSummary()).isEqualTo("segments=1, targetChars=7");
        assertThat(command.inputSummary()).doesNotContain("直译 字幕。");
        assertThat(command.outputSummary()).doesNotContain("更自然的字幕。");
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

    private PromptTemplateRegistry testRegistry(String version, String systemPrompt) {
        PromptTemplateVo template = new PromptTemplateVo(
                version,
                PromptTemplatePurpose.SUBTITLE_POLISHING,
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
