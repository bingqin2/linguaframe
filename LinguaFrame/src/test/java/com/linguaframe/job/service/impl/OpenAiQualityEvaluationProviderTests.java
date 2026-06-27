package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.QualityEvaluationRequestBo;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.domain.enums.PromptTemplatePurpose;
import com.linguaframe.job.domain.vo.PromptTemplateVo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
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
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiQualityEvaluationProviderTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void evaluatesQualityWithResponsesApi() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = testRestClient(restClientBuilder);
        RecordingModelCallAuditService auditService = new RecordingModelCallAuditService();
        OpenAiQualityEvaluationProvider provider = new OpenAiQualityEvaluationProvider(
                openAiProperties("test-openai-key", "test-evaluation-model", "https://api.openai.test", 5),
                restClient,
                objectMapper,
                auditService,
                testRegistry("test-evaluation-template-v7", "Test evaluation prompt.")
        );

        server.expect(requestTo("https://api.openai.test/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-openai-key"))
                .andExpect(jsonPath("$.model").value("test-evaluation-model"))
                .andExpect(jsonPath("$.input[0].content[0].text").value("Test evaluation prompt."))
                .andExpect(jsonPath("$.text.format.schema.properties.score.type").value("integer"))
                .andRespond(withSuccess(responsesPayload("""
                        {
                          "score": 92,
                          "verdict": "GOOD",
                          "completeness": 95,
                          "readability": 92,
                          "timingPreservation": 94,
                          "naturalness": 88,
                          "issues": ["No blocking issue."],
                          "suggestedFixes": ["Review terminology."]
                        }
                        """), MediaType.APPLICATION_JSON));

        var result = provider.evaluate(request());

        assertThat(result.score()).isEqualTo(92);
        assertThat(result.verdict()).isEqualTo("GOOD");
        assertThat(result.issues()).containsExactly("No blocking issue.");
        assertThat(result.suggestedFixes()).containsExactly("Review terminology.");
        assertThat(auditService.successCommands).hasSize(1);
        var command = auditService.successCommands.getFirst();
        assertThat(command.jobId()).isEqualTo("quality-openai-job");
        assertThat(command.stage()).isEqualTo(LocalizationJobStage.TRANSLATION_QUALITY_EVALUATION);
        assertThat(command.operation()).isEqualTo(ModelCallOperation.EVALUATION);
        assertThat(command.provider()).isEqualTo(ModelCallProvider.OPENAI);
        assertThat(command.model()).isEqualTo("test-evaluation-model");
        assertThat(command.promptVersion()).isEqualTo("test-evaluation-template-v7");
        assertThat(command.inputTokens()).isEqualTo(900);
        assertThat(command.outputTokens()).isEqualTo(300);
        server.verify();
    }

    @Test
    void failsFastWhenOpenAiEvaluationConfigurationIsMissing() {
        RestClient.Builder restClientBuilder = RestClient.builder();

        assertThatThrownBy(() -> new OpenAiQualityEvaluationProvider(
                openAiProperties("", "", "https://api.openai.test", 5),
                restClientBuilder,
                objectMapper,
                new RecordingModelCallAuditService(),
                defaultRegistry()
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI quality evaluation provider requires OPENAI_API_KEY and OPENAI_EVALUATION_MODEL.");
    }

    @Test
    void wrapsHttpFailuresWithSanitizedStatusMessage() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = testRestClient(restClientBuilder);
        RecordingModelCallAuditService auditService = new RecordingModelCallAuditService();
        OpenAiQualityEvaluationProvider provider = new OpenAiQualityEvaluationProvider(
                openAiProperties("test-openai-key", "test-evaluation-model", "https://api.openai.test", 5),
                restClient,
                objectMapper,
                auditService,
                defaultRegistry()
        );
        server.expect(requestTo("https://api.openai.test/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"provider failure details\"}}"));

        assertThatThrownBy(() -> provider.evaluate(request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI quality evaluation request failed with status 401");
        assertThat(auditService.failureCommands).hasSize(1);
        assertThat(auditService.failureCommands.getFirst().operation()).isEqualTo(ModelCallOperation.EVALUATION);
        assertThat(auditService.failureSummaries).containsExactly("OpenAI quality evaluation request failed with status 401");
        server.verify();
    }

    private LinguaFrameProperties openAiProperties(String apiKey, String model, String baseUrl, int timeoutSeconds) {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getEvaluation().setProvider("openai");
        properties.getEvaluation().getOpenai().setApiKey(apiKey);
        properties.getEvaluation().getOpenai().setModel(model);
        properties.getEvaluation().getOpenai().setBaseUrl(baseUrl);
        properties.getEvaluation().getOpenai().setTimeoutSeconds(timeoutSeconds);
        return properties;
    }

    private RestClient testRestClient(RestClient.Builder restClientBuilder) {
        return restClientBuilder
                .baseUrl("https://api.openai.test")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-openai-key")
                .build();
    }

    private QualityEvaluationRequestBo request() {
        return new QualityEvaluationRequestBo(
                "quality-openai-job",
                "zh-CN",
                List.of(new TranscriptSegmentVo(0, 0L, 1_000L, "Hello from LinguaFrame.")),
                List.of(new SubtitleSegmentVo("zh-CN", 0, 0L, 1_000L, "LinguaFrame 向你问好。"))
        );
    }

    private String responsesPayload(String outputText) {
        String escapedOutputText = new String(objectMapper.valueToTree(outputText).toString().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        return """
                {
                  "id": "resp_quality_123",
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
                    "input_tokens": 900,
                    "output_tokens": 300
                  }
                }
                """.formatted(escapedOutputText, escapedOutputText);
    }

    private PromptTemplateRegistry defaultRegistry() {
        return testRegistry(
                "openai-translation-quality-evaluation-v1",
                "You evaluate translated subtitle quality for video localization. Return JSON only with numeric scores from 0 to 100."
        );
    }

    private PromptTemplateRegistry testRegistry(String version, String systemPrompt) {
        PromptTemplateVo template = new PromptTemplateVo(
                version,
                PromptTemplatePurpose.TRANSLATION_QUALITY_EVALUATION,
                "OPENAI",
                "responses",
                systemPrompt,
                "Return JSON with score, verdict, completeness, readability, timingPreservation, naturalness, issues, and suggestedFixes.",
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
