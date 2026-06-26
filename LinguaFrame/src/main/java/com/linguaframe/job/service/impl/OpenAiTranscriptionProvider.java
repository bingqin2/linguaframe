package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateModelCallRecordCommand;
import com.linguaframe.job.domain.bo.TranscriptionResultBo;
import com.linguaframe.job.domain.bo.TranscriptionSegmentBo;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.service.ModelCallAuditService;
import com.linguaframe.job.service.TranscriptionProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "linguaframe.transcription", name = "provider", havingValue = "openai")
public class OpenAiTranscriptionProvider implements TranscriptionProvider {

    private static final String MISSING_CONFIGURATION_MESSAGE =
            "OpenAI transcription provider requires OPENAI_API_KEY and OPENAI_TRANSCRIPTION_MODEL.";
    private static final BigDecimal ONE_THOUSAND = BigDecimal.valueOf(1_000L);

    private final LinguaFrameProperties.Transcription.OpenAi openai;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ModelCallAuditService auditService;

    @Autowired
    public OpenAiTranscriptionProvider(
            LinguaFrameProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            ModelCallAuditService auditService
    ) {
        this(properties, buildRestClient(properties.getTranscription().getOpenai(), restClientBuilder), objectMapper, auditService);
    }

    OpenAiTranscriptionProvider(
            LinguaFrameProperties properties,
            RestClient restClient,
            ObjectMapper objectMapper,
            ModelCallAuditService auditService
    ) {
        this.openai = properties.getTranscription().getOpenai();
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        requireConfigured(openai.getApiKey());
        requireConfigured(openai.getModel());
    }

    private static RestClient buildRestClient(
            LinguaFrameProperties.Transcription.OpenAi openai,
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
    public TranscriptionResultBo transcribe(String jobId, byte[] audioContent) {
        long started = System.nanoTime();
        try {
            String responseBody = sendRequest(jobId, audioContent);
            TranscriptionResultBo result = parseResult(responseBody);
            auditService.recordSuccess(command(jobId, elapsedMillis(started), audioSeconds(result)));
            return result;
        } catch (RuntimeException ex) {
            auditService.recordFailure(command(jobId, elapsedMillis(started), null), ex.getMessage());
            throw ex;
        }
    }

    private String sendRequest(String jobId, byte[] audioContent) {
        try {
            return restClient.post()
                    .uri("/v1/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(requestBody(jobId, audioContent))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException("OpenAI transcription request failed with status " + ex.getStatusCode().value(), ex);
        }
    }

    private MultiValueMap<String, Object> requestBody(String jobId, byte[] audioContent) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model", openai.getModel());
        body.add("response_format", "verbose_json");
        body.add("timestamp_granularities[]", "segment");
        body.add("file", new NamedByteArrayResource(audioContent, jobId + ".wav"));
        return body;
    }

    private TranscriptionResultBo parseResult(String responseBody) {
        JsonNode response = readResponseJson(responseBody);
        JsonNode segmentsNode = response.get("segments");
        if (segmentsNode == null || !segmentsNode.isArray() || segmentsNode.isEmpty()) {
            throw new IllegalStateException("OpenAI transcription response did not contain segment timestamps.");
        }

        List<TranscriptionSegmentBo> segments = new ArrayList<>();
        for (int i = 0; i < segmentsNode.size(); i++) {
            JsonNode segmentNode = segmentsNode.get(i);
            long startMs = secondsToMillis(segmentNode.get("start"));
            long endMs = secondsToMillis(segmentNode.get("end"));
            if (startMs < 0 || endMs <= startMs) {
                throw new IllegalStateException("OpenAI transcription returned invalid timestamps for segment " + i + ".");
            }

            JsonNode textNode = segmentNode.get("text");
            String text = textNode == null || !textNode.isTextual() ? "" : textNode.asText().trim();
            if (text.isBlank()) {
                throw new IllegalStateException("OpenAI transcription returned blank text for segment " + i + ".");
            }

            segments.add(new TranscriptionSegmentBo(i, startMs, endMs, text));
        }
        return new TranscriptionResultBo(segments);
    }

    private JsonNode readResponseJson(String responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("OpenAI transcription response was not valid JSON.", ex);
        }
    }

    private long secondsToMillis(JsonNode value) {
        if (value == null || !value.isNumber()) {
            return -1L;
        }
        return Math.round(value.asDouble() * 1000.0d);
    }

    private CreateModelCallRecordCommand command(String jobId, long latencyMs, BigDecimal audioSeconds) {
        return new CreateModelCallRecordCommand(
                jobId,
                LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSCRIPTION,
                ModelCallProvider.OPENAI,
                openai.getModel(),
                "openai-audio-transcriptions-v1",
                latencyMs,
                null,
                null,
                audioSeconds,
                null
        );
    }

    private BigDecimal audioSeconds(TranscriptionResultBo result) {
        return result.segments().stream()
                .map(TranscriptionSegmentBo::endMs)
                .max(Long::compareTo)
                .map(endMs -> BigDecimal.valueOf(endMs).divide(ONE_THOUSAND, 3, RoundingMode.HALF_UP))
                .orElse(null);
    }

    private long elapsedMillis(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    private static void requireConfigured(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(MISSING_CONFIGURATION_MESSAGE);
        }
    }

    private static class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
