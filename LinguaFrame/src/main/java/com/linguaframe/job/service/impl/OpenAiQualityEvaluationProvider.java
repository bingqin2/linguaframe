package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateModelCallRecordCommand;
import com.linguaframe.job.domain.bo.QualityEvaluationRequestBo;
import com.linguaframe.job.domain.bo.QualityEvaluationResultBo;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.domain.enums.PromptTemplatePurpose;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.service.ModelCallAuditService;
import com.linguaframe.job.service.PromptTemplateRegistry;
import com.linguaframe.job.service.QualityEvaluationProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "linguaframe.evaluation", name = "provider", havingValue = "openai")
public class OpenAiQualityEvaluationProvider implements QualityEvaluationProvider {

    private static final String MISSING_CONFIGURATION_MESSAGE =
            "OpenAI quality evaluation provider requires OPENAI_API_KEY and OPENAI_EVALUATION_MODEL.";

    private final LinguaFrameProperties.Evaluation.OpenAi openai;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ModelCallAuditService auditService;
    private final PromptTemplateRegistry promptTemplateRegistry;

    @Autowired
    public OpenAiQualityEvaluationProvider(
            LinguaFrameProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            ModelCallAuditService auditService,
            PromptTemplateRegistry promptTemplateRegistry
    ) {
        this(
                properties,
                buildRestClient(properties.getEvaluation().getOpenai(), restClientBuilder),
                objectMapper,
                auditService,
                promptTemplateRegistry
        );
    }

    OpenAiQualityEvaluationProvider(
            LinguaFrameProperties properties,
            RestClient restClient,
            ObjectMapper objectMapper,
            ModelCallAuditService auditService,
            PromptTemplateRegistry promptTemplateRegistry
    ) {
        this.openai = properties.getEvaluation().getOpenai();
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.promptTemplateRegistry = promptTemplateRegistry;
        requireConfigured(openai.getApiKey());
        requireConfigured(openai.getModel());
        this.restClient = restClient;
    }

    private static RestClient buildRestClient(
            LinguaFrameProperties.Evaluation.OpenAi openai,
            RestClient.Builder restClientBuilder
    ) {
        requireConfigured(openai.getApiKey());
        requireConfigured(openai.getModel());
        return restClientBuilder
                .baseUrl(openai.getBaseUrl())
                .requestFactory(clientHttpRequestFactory(openai.getTimeoutSeconds()))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openai.getApiKey())
                .build();
    }

    private static JdkClientHttpRequestFactory clientHttpRequestFactory(int timeoutSeconds) {
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return requestFactory;
    }

    @Override
    public QualityEvaluationResultBo evaluate(QualityEvaluationRequestBo request) {
        long started = System.nanoTime();
        try {
            String responseBody = sendRequest(buildRequestBody(request));
            OpenAiQualityResponse response = extractResponse(responseBody);
            QualityEvaluationResultBo result = parseQualityResult(response.outputText());
            auditService.recordSuccess(command(request.jobId(), elapsedMillis(started), response.inputTokens(), response.outputTokens()));
            return result;
        } catch (RuntimeException ex) {
            auditService.recordFailure(command(request.jobId(), elapsedMillis(started), null, null), ex.getMessage());
            throw ex;
        }
    }

    private String sendRequest(String requestBody) {
        try {
            return restClient.post()
                    .uri("/v1/responses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException("OpenAI quality evaluation request failed with status " + ex.getStatusCode().value(), ex);
        }
    }

    private String buildRequestBody(QualityEvaluationRequestBo requestBo) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", openai.getModel());
        request.put("input", List.of(
                inputMessage(
                        "system",
                        promptTemplateRegistry.activeTemplate(PromptTemplatePurpose.TRANSLATION_QUALITY_EVALUATION)
                                .systemPrompt()
                ),
                inputMessage("user", userPayload(requestBo))
        ));
        request.put("text", Map.of(
                "format", Map.of(
                        "type", "json_schema",
                        "name", "translation_quality_evaluation",
                        "strict", true,
                        "schema", qualitySchema()
                )
        ));
        return writeJson(request);
    }

    private Map<String, Object> inputMessage(String role, String text) {
        return Map.of(
                "role", role,
                "content", List.of(Map.of(
                        "type", "input_text",
                        "text", text
                ))
        );
    }

    private String userPayload(QualityEvaluationRequestBo requestBo) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobId", requestBo.jobId());
        payload.put("language", requestBo.language());
        payload.put("sourceSegments", requestBo.sourceSegments().stream()
                .map(this::sourceSegmentPayload)
                .toList());
        payload.put("targetSegments", requestBo.targetSegments().stream()
                .map(this::targetSegmentPayload)
                .toList());
        return writeJson(payload);
    }

    private Map<String, Object> sourceSegmentPayload(TranscriptSegmentVo segment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("index", segment.index());
        payload.put("startMs", segment.startMs());
        payload.put("endMs", segment.endMs());
        payload.put("text", segment.text());
        return payload;
    }

    private Map<String, Object> targetSegmentPayload(SubtitleSegmentVo segment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("index", segment.index());
        payload.put("startMs", segment.startMs());
        payload.put("endMs", segment.endMs());
        payload.put("text", segment.text());
        return payload;
    }

    private Map<String, Object> qualitySchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("score", Map.of("type", "integer"));
        properties.put("verdict", Map.of("type", "string"));
        properties.put("completeness", Map.of("type", "integer"));
        properties.put("readability", Map.of("type", "integer"));
        properties.put("timingPreservation", Map.of("type", "integer"));
        properties.put("naturalness", Map.of("type", "integer"));
        properties.put("issues", Map.of("type", "array", "items", Map.of("type", "string")));
        properties.put("suggestedFixes", Map.of("type", "array", "items", Map.of("type", "string")));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of(
                "score",
                "verdict",
                "completeness",
                "readability",
                "timingPreservation",
                "naturalness",
                "issues",
                "suggestedFixes"
        ));
        schema.put("properties", properties);
        return schema;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("OpenAI quality evaluation request could not be serialized.", ex);
        }
    }

    private OpenAiQualityResponse extractResponse(String responseBody) {
        JsonNode response = readResponseJson(responseBody);
        String outputText = extractOutputText(response);
        JsonNode usage = response.get("usage");
        return new OpenAiQualityResponse(
                outputText,
                integerOrNull(usage, "input_tokens"),
                integerOrNull(usage, "output_tokens")
        );
    }

    private JsonNode readResponseJson(String responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("OpenAI quality evaluation response did not contain text output.", ex);
        }
    }

    private String extractOutputText(JsonNode response) {
        JsonNode outputText = response.get("output_text");
        if (outputText != null && outputText.isTextual() && !outputText.asText().isBlank()) {
            return outputText.asText();
        }

        JsonNode output = response.get("output");
        if (output != null && output.isArray()) {
            for (JsonNode outputItem : output) {
                JsonNode content = outputItem.get("content");
                if (content == null || !content.isArray()) {
                    continue;
                }
                for (JsonNode contentItem : content) {
                    JsonNode text = contentItem.get("text");
                    if (text != null && text.isTextual() && !text.asText().isBlank()) {
                        return text.asText();
                    }
                }
            }
        }

        throw new IllegalStateException("OpenAI quality evaluation response did not contain text output.");
    }

    private QualityEvaluationResultBo parseQualityResult(String outputText) {
        try {
            return objectMapper.readValue(outputText, QualityEvaluationResultBo.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("OpenAI quality evaluation response was not valid JSON.", ex);
        }
    }

    private CreateModelCallRecordCommand command(
            String jobId,
            long latencyMs,
            Integer inputTokens,
            Integer outputTokens
    ) {
        return new CreateModelCallRecordCommand(
                jobId,
                LocalizationJobStage.TRANSLATION_QUALITY_EVALUATION,
                ModelCallOperation.EVALUATION,
                ModelCallProvider.OPENAI,
                openai.getModel(),
                promptTemplateRegistry.activeTemplate(PromptTemplatePurpose.TRANSLATION_QUALITY_EVALUATION).version(),
                latencyMs,
                inputTokens,
                outputTokens,
                null,
                null
        );
    }

    private Integer integerOrNull(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        return value != null && value.canConvertToInt() ? value.asInt() : null;
    }

    private long elapsedMillis(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    private static void requireConfigured(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(MISSING_CONFIGURATION_MESSAGE);
        }
    }

    private record OpenAiQualityResponse(String outputText, Integer inputTokens, Integer outputTokens) {
    }
}
