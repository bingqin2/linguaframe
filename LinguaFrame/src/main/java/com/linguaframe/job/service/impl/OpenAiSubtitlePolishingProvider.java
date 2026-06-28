package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateModelCallRecordCommand;
import com.linguaframe.job.domain.bo.SubtitlePolishingResultBo;
import com.linguaframe.job.domain.bo.TranslationSegmentBo;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.domain.enums.PromptTemplatePurpose;
import com.linguaframe.job.domain.vo.PromptTemplateVo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.service.ModelCallAuditService;
import com.linguaframe.job.service.ModelCallSummaryService;
import com.linguaframe.job.service.PromptTemplateRegistry;
import com.linguaframe.job.service.SubtitlePolishingProvider;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(prefix = "linguaframe.translation", name = "provider", havingValue = "openai")
public class OpenAiSubtitlePolishingProvider implements SubtitlePolishingProvider {

    private static final String MISSING_CONFIGURATION_MESSAGE =
            "OpenAI subtitle polishing provider requires OPENAI_API_KEY and OPENAI_TRANSLATION_MODEL.";

    private final LinguaFrameProperties.Translation.OpenAi openai;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ModelCallAuditService auditService;
    private final PromptTemplateRegistry promptTemplateRegistry;
    private final ModelCallSummaryService summaryService;

    @Autowired
    public OpenAiSubtitlePolishingProvider(
            LinguaFrameProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            ModelCallAuditService auditService,
            PromptTemplateRegistry promptTemplateRegistry,
            ModelCallSummaryService summaryService
    ) {
        this(
                properties,
                buildRestClient(properties.getTranslation().getOpenai(), restClientBuilder),
                objectMapper,
                auditService,
                promptTemplateRegistry,
                summaryService
        );
    }

    OpenAiSubtitlePolishingProvider(
            LinguaFrameProperties properties,
            RestClient restClient,
            ObjectMapper objectMapper,
            ModelCallAuditService auditService,
            PromptTemplateRegistry promptTemplateRegistry,
            ModelCallSummaryService summaryService
    ) {
        this.openai = properties.getTranslation().getOpenai();
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.promptTemplateRegistry = promptTemplateRegistry;
        this.summaryService = summaryService;
        requireConfigured(openai.getApiKey());
        requireConfigured(openai.getModel());
        this.restClient = restClient;
    }

    private static RestClient buildRestClient(
            LinguaFrameProperties.Translation.OpenAi openai,
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
    public SubtitlePolishingResultBo polish(
            String jobId,
            String targetLanguage,
            String subtitlePolishingMode,
            List<SubtitleSegmentVo> subtitles
    ) {
        long started = System.nanoTime();
        try {
            String responseBody = sendRequest(buildRequestBody(jobId, targetLanguage, subtitlePolishingMode, subtitles));
            OpenAiSubtitlePolishingResponse response = extractResponse(responseBody);
            PolishedSegmentsResponse polishedResponse = parsePolishedSegments(response.outputText());
            SubtitlePolishingResultBo result = toResult(subtitles, polishedResponse);
            auditService.recordSuccess(command(
                    jobId,
                    elapsedMillis(started),
                    response.inputTokens(),
                    response.outputTokens(),
                    inputSummary(targetLanguage, subtitlePolishingMode, subtitles),
                    outputSummary(result)
            ));
            return result;
        } catch (RuntimeException ex) {
            auditService.recordFailure(command(
                    jobId,
                    elapsedMillis(started),
                    null,
                    null,
                    inputSummary(targetLanguage, subtitlePolishingMode, subtitles),
                    null
            ), ex.getMessage());
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
            throw new IllegalStateException("OpenAI subtitle polishing request failed with status " + ex.getStatusCode().value(), ex);
        }
    }

    private String buildRequestBody(
            String jobId,
            String targetLanguage,
            String subtitlePolishingMode,
            List<SubtitleSegmentVo> subtitles
    ) {
        PromptTemplateVo template = promptTemplateRegistry.activeTemplate(PromptTemplatePurpose.SUBTITLE_POLISHING);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", openai.getModel());
        request.put("input", List.of(
                inputMessage("system", template.systemPrompt()),
                inputMessage("user", userPayload(jobId, targetLanguage, subtitlePolishingMode, subtitles))
        ));
        request.put("text", Map.of(
                "format", Map.of(
                        "type", "json_schema",
                        "name", "subtitle_polishing",
                        "strict", true,
                        "schema", polishingSchema()
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

    private String userPayload(
            String jobId,
            String targetLanguage,
            String subtitlePolishingMode,
            List<SubtitleSegmentVo> subtitles
    ) {
        List<Map<String, Object>> segments = subtitles.stream()
                .map(segment -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("index", segment.index());
                    payload.put("text", segment.text());
                    return payload;
                })
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobId", jobId);
        payload.put("targetLanguage", targetLanguage);
        payload.put("subtitlePolishingMode", subtitlePolishingMode);
        payload.put("instruction", "Polish text only. Preserve segment count, indexes, timing, meaning, and speaker intent.");
        payload.put("segments", segments);
        return writeJson(payload);
    }

    private Map<String, Object> polishingSchema() {
        Map<String, Object> segmentProperties = new LinkedHashMap<>();
        segmentProperties.put("index", Map.of("type", "integer"));
        segmentProperties.put("text", Map.of("type", "string"));

        Map<String, Object> segmentItem = new LinkedHashMap<>();
        segmentItem.put("type", "object");
        segmentItem.put("additionalProperties", false);
        segmentItem.put("required", List.of("index", "text"));
        segmentItem.put("properties", segmentProperties);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("segments", Map.of(
                "type", "array",
                "items", segmentItem
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of("segments"));
        schema.put("properties", properties);
        return schema;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("OpenAI subtitle polishing request could not be serialized.", ex);
        }
    }

    private OpenAiSubtitlePolishingResponse extractResponse(String responseBody) {
        JsonNode response = readResponseJson(responseBody);
        String outputText = extractOutputText(response);
        JsonNode usage = response.get("usage");
        return new OpenAiSubtitlePolishingResponse(
                outputText,
                integerOrNull(usage, "input_tokens"),
                integerOrNull(usage, "output_tokens")
        );
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

        throw new IllegalStateException("OpenAI subtitle polishing response did not contain text output.");
    }

    private JsonNode readResponseJson(String responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("OpenAI subtitle polishing response did not contain text output.", ex);
        }
    }

    private PolishedSegmentsResponse parsePolishedSegments(String outputText) {
        try {
            return objectMapper.readValue(outputText, PolishedSegmentsResponse.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("OpenAI subtitle polishing response was not valid JSON.", ex);
        }
    }

    private SubtitlePolishingResultBo toResult(
            List<SubtitleSegmentVo> sourceSegments,
            PolishedSegmentsResponse polishedResponse
    ) {
        List<PolishedSegment> polishedSegments = polishedResponse.segments() == null
                ? List.of()
                : polishedResponse.segments();
        if (polishedSegments.size() != sourceSegments.size()) {
            throw new IllegalStateException("OpenAI subtitle polishing returned %d segments for %d source segments."
                    .formatted(polishedSegments.size(), sourceSegments.size()));
        }

        Map<Integer, SubtitleSegmentVo> sourceByIndex = sourceSegments.stream()
                .collect(Collectors.toMap(SubtitleSegmentVo::index, Function.identity(), (first, second) -> first, LinkedHashMap::new));
        Map<Integer, String> polishedTextByIndex = new LinkedHashMap<>();
        for (PolishedSegment polishedSegment : polishedSegments) {
            if (!sourceByIndex.containsKey(polishedSegment.index())) {
                throw new IllegalStateException("OpenAI subtitle polishing returned an unknown segment index: " + polishedSegment.index() + ".");
            }
            if (polishedSegment.text() == null || polishedSegment.text().isBlank()) {
                throw new IllegalStateException("OpenAI subtitle polishing returned blank text for segment " + polishedSegment.index() + ".");
            }
            polishedTextByIndex.put(polishedSegment.index(), polishedSegment.text().trim());
        }

        List<TranslationSegmentBo> resultSegments = sourceSegments.stream()
                .map(source -> new TranslationSegmentBo(
                        source.index(),
                        source.startMs(),
                        source.endMs(),
                        polishedTextByIndex.get(source.index())
                ))
                .toList();
        return new SubtitlePolishingResultBo(resultSegments);
    }

    private CreateModelCallRecordCommand command(
            String jobId,
            long latencyMs,
            Integer inputTokens,
            Integer outputTokens,
            String inputSummary,
            String outputSummary
    ) {
        return new CreateModelCallRecordCommand(
                jobId,
                LocalizationJobStage.SUBTITLE_POLISHING,
                ModelCallOperation.SUBTITLE_POLISHING,
                ModelCallProvider.OPENAI,
                openai.getModel(),
                promptTemplateRegistry.activeTemplate(PromptTemplatePurpose.SUBTITLE_POLISHING).version(),
                latencyMs,
                inputTokens,
                outputTokens,
                null,
                null,
                inputSummary,
                outputSummary
        );
    }

    private String inputSummary(String targetLanguage, String mode, List<SubtitleSegmentVo> subtitles) {
        return summaryService.translationInput(
                targetLanguage,
                mode,
                subtitles.size(),
                subtitles.stream().map(SubtitleSegmentVo::text).mapToInt(this::length).sum(),
                0
        );
    }

    private String outputSummary(SubtitlePolishingResultBo result) {
        return summaryService.translationOutput(
                result.segments().size(),
                result.segments().stream().map(TranslationSegmentBo::text).mapToInt(this::length).sum()
        );
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
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

    private record OpenAiSubtitlePolishingResponse(String outputText, Integer inputTokens, Integer outputTokens) {
    }

    private record PolishedSegmentsResponse(List<PolishedSegment> segments) {
    }

    private record PolishedSegment(int index, String text) {
    }
}
