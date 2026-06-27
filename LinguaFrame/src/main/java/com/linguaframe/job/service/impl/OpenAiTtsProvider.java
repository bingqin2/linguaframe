package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateModelCallRecordCommand;
import com.linguaframe.job.domain.bo.TtsRequestBo;
import com.linguaframe.job.domain.bo.TtsResultBo;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.service.ModelCallAuditService;
import com.linguaframe.job.service.ModelCallSummaryService;
import com.linguaframe.job.service.TtsProvider;
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
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "linguaframe.tts", name = "provider", havingValue = "openai")
public class OpenAiTtsProvider implements TtsProvider {

    private static final String MISSING_CONFIGURATION_MESSAGE =
            "OpenAI TTS provider requires OPENAI_API_KEY, OPENAI_TTS_MODEL, and OPENAI_TTS_VOICE.";

    private final LinguaFrameProperties.Tts.OpenAi openai;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ModelCallAuditService auditService;
    private final ModelCallSummaryService summaryService;

    @Autowired
    public OpenAiTtsProvider(
            LinguaFrameProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            ModelCallAuditService auditService,
            ModelCallSummaryService summaryService
    ) {
        this(properties, buildRestClient(properties.getTts().getOpenai(), restClientBuilder), objectMapper, auditService, summaryService);
    }

    OpenAiTtsProvider(
            LinguaFrameProperties properties,
            RestClient restClient,
            ObjectMapper objectMapper,
            ModelCallAuditService auditService,
            ModelCallSummaryService summaryService
    ) {
        this.openai = properties.getTts().getOpenai();
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.summaryService = summaryService;
        requireConfigured(openai.getApiKey());
        requireConfigured(openai.getModel());
        requireConfigured(openai.getVoice());
    }

    private static RestClient buildRestClient(
            LinguaFrameProperties.Tts.OpenAi openai,
            RestClient.Builder restClientBuilder
    ) {
        requireConfigured(openai.getApiKey());
        requireConfigured(openai.getModel());
        requireConfigured(openai.getVoice());
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
    public TtsResultBo synthesize(TtsRequestBo request) {
        long started = System.nanoTime();
        try {
            byte[] audioContent = sendRequest(request);
            if (audioContent == null || audioContent.length == 0) {
                throw new IllegalStateException("OpenAI TTS response was empty.");
            }
            TtsResultBo result = new TtsResultBo(audioContent, "dubbing-audio.mp3", "audio/mpeg");
            auditService.recordSuccess(command(request, elapsedMillis(started), audioContent.length));
            return result;
        } catch (RuntimeException ex) {
            auditService.recordFailure(command(request, elapsedMillis(started), null), ex.getMessage());
            throw ex;
        }
    }

    private byte[] sendRequest(TtsRequestBo request) {
        try {
            return restClient.post()
                    .uri("/v1/audio/speech")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.valueOf("audio/mpeg"))
                    .body(buildRequestBody(request))
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException("OpenAI TTS request failed with status " + ex.getStatusCode().value(), ex);
        }
    }

    private String buildRequestBody(TtsRequestBo request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", openai.getModel());
        body.put("voice", effectiveVoice(request));
        body.put("input", request.text());
        body.put("response_format", "mp3");
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("OpenAI TTS request could not be serialized.", ex);
        }
    }

    private CreateModelCallRecordCommand command(TtsRequestBo request, long latencyMs, Integer audioByteCount) {
        return new CreateModelCallRecordCommand(
                request.jobId(),
                LocalizationJobStage.DUBBING_AUDIO_GENERATION,
                ModelCallOperation.TTS,
                ModelCallProvider.OPENAI,
                openai.getModel(),
                "openai-tts-v1",
                latencyMs,
                null,
                null,
                null,
                characterCount(request),
                summaryService.ttsInput(characterCount(request)),
                audioByteCount == null ? null : summaryService.ttsOutput(audioByteCount)
        );
    }

    private Integer characterCount(TtsRequestBo request) {
        return request.text() == null ? 0 : request.text().length();
    }

    private String effectiveVoice(TtsRequestBo request) {
        if (request.voice() != null && !request.voice().isBlank()) {
            return request.voice().trim();
        }
        return openai.getVoice();
    }

    private long elapsedMillis(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    private static void requireConfigured(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(MISSING_CONFIGURATION_MESSAGE);
        }
    }
}
