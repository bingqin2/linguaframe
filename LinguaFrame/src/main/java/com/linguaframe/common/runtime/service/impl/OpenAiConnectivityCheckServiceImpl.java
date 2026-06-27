package com.linguaframe.common.runtime.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.common.runtime.domain.enums.RuntimeProbeStatus;
import com.linguaframe.common.runtime.domain.vo.RuntimeProbeResultVo;
import com.linguaframe.common.runtime.service.OpenAiConnectivityCheckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class OpenAiConnectivityCheckServiceImpl implements OpenAiConnectivityCheckService {

    private static final String DISABLED_MESSAGE = "OpenAI connectivity check is disabled";
    private static final String MISSING_CONFIGURATION_MESSAGE =
            "OpenAI connectivity check is enabled but API key or model is missing";
    private static final String SUCCESS_MESSAGE = "OpenAI model metadata endpoint is reachable";

    private final LinguaFrameProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final boolean configureRequestFactory;

    @Autowired
    public OpenAiConnectivityCheckServiceImpl(
            LinguaFrameProperties properties,
            RestClient.Builder restClientBuilder
    ) {
        this(properties, restClientBuilder, true);
    }

    public OpenAiConnectivityCheckServiceImpl(
            LinguaFrameProperties properties,
            RestClient.Builder restClientBuilder,
            boolean configureRequestFactory
    ) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
        this.configureRequestFactory = configureRequestFactory;
    }

    @Override
    public RuntimeProbeResultVo check() {
        long started = System.nanoTime();
        if (!properties.getOpenAiConnectivity().isEnabled()) {
            return result(RuntimeProbeStatus.SKIPPED, started, DISABLED_MESSAGE);
        }

        OpenAiConfig config = resolveConfig();
        if (!hasText(config.apiKey()) || !hasText(config.baseUrl()) || !hasText(config.model())) {
            return result(RuntimeProbeStatus.DOWN, started, MISSING_CONFIGURATION_MESSAGE);
        }

        try {
            client(config).get()
                    .uri("/v1/models/{model}", config.model())
                    .retrieve()
                    .toBodilessEntity();
            return result(RuntimeProbeStatus.UP, started, SUCCESS_MESSAGE);
        } catch (RestClientResponseException ex) {
            return result(RuntimeProbeStatus.DOWN, started,
                    "OpenAI model metadata endpoint returned HTTP " + ex.getStatusCode().value());
        } catch (RuntimeException ex) {
            return result(RuntimeProbeStatus.DOWN, started, "OpenAI model metadata endpoint is unreachable");
        }
    }

    private RestClient client(OpenAiConfig config) {
        Duration timeout = Duration.ofSeconds(properties.getOpenAiConnectivity().getTimeoutSeconds());
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        RestClient.Builder builder = restClientBuilder
                .baseUrl(config.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey());
        if (configureRequestFactory) {
            builder.requestFactory(requestFactory);
        }
        return builder.build();
    }

    private OpenAiConfig resolveConfig() {
        String overrideModel = properties.getOpenAiConnectivity().getModel();
        List<OpenAiConfig> providerConfigs = List.of(
                new OpenAiConfig(
                        properties.getTranscription().getOpenai().getApiKey(),
                        properties.getTranscription().getOpenai().getBaseUrl(),
                        properties.getTranscription().getOpenai().getModel()
                ),
                new OpenAiConfig(
                        properties.getTranslation().getOpenai().getApiKey(),
                        properties.getTranslation().getOpenai().getBaseUrl(),
                        properties.getTranslation().getOpenai().getModel()
                ),
                new OpenAiConfig(
                        properties.getEvaluation().getOpenai().getApiKey(),
                        properties.getEvaluation().getOpenai().getBaseUrl(),
                        properties.getEvaluation().getOpenai().getModel()
                ),
                new OpenAiConfig(
                        properties.getTts().getOpenai().getApiKey(),
                        properties.getTts().getOpenai().getBaseUrl(),
                        properties.getTts().getOpenai().getModel()
                )
        );
        if (hasText(overrideModel)) {
            return providerConfigs.stream()
                    .filter(config -> hasText(config.apiKey()) && hasText(config.baseUrl()))
                    .findFirst()
                    .map(config -> new OpenAiConfig(config.apiKey(), config.baseUrl(), overrideModel))
                    .orElse(new OpenAiConfig("", "", overrideModel));
        }

        return providerConfigs.stream()
                .filter(config -> hasText(config.apiKey()) && hasText(config.baseUrl()) && hasText(config.model()))
                .findFirst()
                .orElse(new OpenAiConfig("", "", ""));
    }

    private RuntimeProbeResultVo result(RuntimeProbeStatus status, long started, String message) {
        return new RuntimeProbeResultVo(status, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), message);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record OpenAiConfig(String apiKey, String baseUrl, String model) {
    }
}
