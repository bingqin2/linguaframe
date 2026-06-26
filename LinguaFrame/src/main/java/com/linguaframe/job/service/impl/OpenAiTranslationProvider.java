package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.TranslationResultBo;
import com.linguaframe.job.domain.bo.TranslationSegmentBo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.service.TranslationProvider;
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
public class OpenAiTranslationProvider implements TranslationProvider {

    private static final String MISSING_CONFIGURATION_MESSAGE =
            "OpenAI translation provider requires OPENAI_API_KEY and OPENAI_TRANSLATION_MODEL.";

    private final LinguaFrameProperties.Translation.OpenAi openai;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiTranslationProvider(
            LinguaFrameProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper
    ) {
        this(properties, buildRestClient(properties.getTranslation().getOpenai(), restClientBuilder), objectMapper);
    }

    OpenAiTranslationProvider(
            LinguaFrameProperties properties,
            RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.openai = properties.getTranslation().getOpenai();
        this.objectMapper = objectMapper;
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
    public TranslationResultBo translate(String jobId, String targetLanguage, List<TranscriptSegmentVo> transcriptSegments) {
        String responseBody = sendRequest(buildRequestBody(jobId, targetLanguage, transcriptSegments));
        String outputText = extractOutputText(responseBody);
        TranslatedSegmentsResponse translatedResponse = parseTranslatedSegments(outputText);
        return toTranslationResult(transcriptSegments, translatedResponse);
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
            throw new IllegalStateException("OpenAI translation request failed with status " + ex.getStatusCode().value(), ex);
        }
    }

    private String buildRequestBody(String jobId, String targetLanguage, List<TranscriptSegmentVo> transcriptSegments) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", openai.getModel());
        request.put("input", List.of(
                inputMessage("system", """
                        You translate subtitle segments for video localization. Translate text only, preserve the meaning and line order, and return JSON only.
                        """),
                inputMessage("user", userPayload(jobId, targetLanguage, transcriptSegments))
        ));
        request.put("text", Map.of(
                "format", Map.of(
                        "type", "json_schema",
                        "name", "subtitle_translation",
                        "strict", true,
                        "schema", translationSchema()
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

    private String userPayload(String jobId, String targetLanguage, List<TranscriptSegmentVo> transcriptSegments) {
        List<Map<String, Object>> segments = transcriptSegments.stream()
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
        payload.put("segments", segments);
        return writeJson(payload);
    }

    private Map<String, Object> translationSchema() {
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
            throw new IllegalStateException("OpenAI translation request could not be serialized.", ex);
        }
    }

    private String extractOutputText(String responseBody) {
        JsonNode response = readResponseJson(responseBody);
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

        throw new IllegalStateException("OpenAI translation response did not contain text output.");
    }

    private JsonNode readResponseJson(String responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("OpenAI translation response did not contain text output.", ex);
        }
    }

    private TranslatedSegmentsResponse parseTranslatedSegments(String outputText) {
        try {
            return objectMapper.readValue(outputText, TranslatedSegmentsResponse.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("OpenAI translation response was not valid JSON.", ex);
        }
    }

    private TranslationResultBo toTranslationResult(
            List<TranscriptSegmentVo> sourceSegments,
            TranslatedSegmentsResponse translatedResponse
    ) {
        List<TranslatedSegment> translatedSegments = translatedResponse.segments() == null
                ? List.of()
                : translatedResponse.segments();
        if (translatedSegments.size() != sourceSegments.size()) {
            throw new IllegalStateException("OpenAI translation returned %d segments for %d source segments."
                    .formatted(translatedSegments.size(), sourceSegments.size()));
        }

        Map<Integer, TranscriptSegmentVo> sourceByIndex = sourceSegments.stream()
                .collect(Collectors.toMap(TranscriptSegmentVo::index, Function.identity(), (first, second) -> first, LinkedHashMap::new));
        Map<Integer, String> translatedTextByIndex = new LinkedHashMap<>();
        for (TranslatedSegment translatedSegment : translatedSegments) {
            if (!sourceByIndex.containsKey(translatedSegment.index())) {
                throw new IllegalStateException("OpenAI translation returned an unknown segment index: " + translatedSegment.index() + ".");
            }
            if (translatedSegment.text() == null || translatedSegment.text().isBlank()) {
                throw new IllegalStateException("OpenAI translation returned blank text for segment " + translatedSegment.index() + ".");
            }
            translatedTextByIndex.put(translatedSegment.index(), translatedSegment.text().trim());
        }

        List<TranslationSegmentBo> resultSegments = sourceSegments.stream()
                .map(source -> new TranslationSegmentBo(
                        source.index(),
                        source.startMs(),
                        source.endMs(),
                        translatedTextByIndex.get(source.index())
                ))
                .toList();
        return new TranslationResultBo(resultSegments);
    }

    private static void requireConfigured(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(MISSING_CONFIGURATION_MESSAGE);
        }
    }

    private record TranslatedSegmentsResponse(List<TranslatedSegment> segments) {
    }

    private record TranslatedSegment(int index, String text) {
    }
}
