package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.common.config.LinguaFrameProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiTranscriptionProviderTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void transcribesVerboseJsonSegments() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = testRestClient(restClientBuilder);
        OpenAiTranscriptionProvider provider = new OpenAiTranscriptionProvider(
                openAiProperties("test-openai-key", "whisper-1", "https://api.openai.test", 5),
                restClient,
                objectMapper
        );

        server.expect(requestTo("https://api.openai.test/v1/audio/transcriptions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-openai-key"))
                .andExpect(content().string(allOf(
                        containsString("name=\"model\""),
                        containsString("whisper-1"),
                        containsString("name=\"response_format\""),
                        containsString("verbose_json"),
                        containsString("name=\"timestamp_granularities[]\""),
                        containsString("segment"),
                        containsString("name=\"file\""),
                        containsString("filename=\"transcription-job-1.wav\"")
                )))
                .andRespond(withSuccess("""
                        {
                          "text": "Hello from LinguaFrame. This is a real transcript.",
                          "segments": [
                            {"id": 0, "start": 0.0, "end": 1.8, "text": "Hello from LinguaFrame."},
                            {"id": 1, "start": 1.8, "end": 3.6, "text": "This is a real transcript."}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = provider.transcribe("transcription-job-1", new byte[] {1, 2, 3});

        assertThat(result.segments())
                .extracting(segment -> segment.index() + ":" + segment.startMs() + ":" + segment.endMs() + ":" + segment.text())
                .containsExactly(
                        "0:0:1800:Hello from LinguaFrame.",
                        "1:1800:3600:This is a real transcript."
                );
        server.verify();
    }

    @Test
    void failsFastWhenOpenAiConfigurationIsMissing() {
        RestClient.Builder restClientBuilder = RestClient.builder();

        assertThatThrownBy(() -> new OpenAiTranscriptionProvider(
                openAiProperties("", "", "https://api.openai.test", 5),
                restClientBuilder,
                objectMapper
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI transcription provider requires OPENAI_API_KEY and OPENAI_TRANSCRIPTION_MODEL.");
    }

    @Test
    void wrapsHttpFailuresWithSanitizedStatusMessage() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = testRestClient(restClientBuilder);
        OpenAiTranscriptionProvider provider = new OpenAiTranscriptionProvider(
                openAiProperties("test-openai-key", "whisper-1", "https://api.openai.test", 5),
                restClient,
                objectMapper
        );

        server.expect(requestTo("https://api.openai.test/v1/audio/transcriptions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"provider failure details\"}}"));

        assertThatThrownBy(() -> provider.transcribe("transcription-job-2", new byte[] {1}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI transcription request failed with status 401");
        server.verify();
    }

    @Test
    void rejectsNonJsonResponse() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = testRestClient(restClientBuilder);
        OpenAiTranscriptionProvider provider = new OpenAiTranscriptionProvider(
                openAiProperties("test-openai-key", "whisper-1", "https://api.openai.test", 5),
                restClient,
                objectMapper
        );

        server.expect(requestTo("https://api.openai.test/v1/audio/transcriptions"))
                .andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.transcribe("transcription-job-3", new byte[] {1}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI transcription response was not valid JSON.");
        server.verify();
    }

    @Test
    void rejectsMissingSegmentTimestamps() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = testRestClient(restClientBuilder);
        OpenAiTranscriptionProvider provider = new OpenAiTranscriptionProvider(
                openAiProperties("test-openai-key", "whisper-1", "https://api.openai.test", 5),
                restClient,
                objectMapper
        );

        server.expect(requestTo("https://api.openai.test/v1/audio/transcriptions"))
                .andRespond(withSuccess("{\"text\":\"Hello\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.transcribe("transcription-job-4", new byte[] {1}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI transcription response did not contain segment timestamps.");
        server.verify();
    }

    @Test
    void rejectsBlankSegmentText() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = testRestClient(restClientBuilder);
        OpenAiTranscriptionProvider provider = new OpenAiTranscriptionProvider(
                openAiProperties("test-openai-key", "whisper-1", "https://api.openai.test", 5),
                restClient,
                objectMapper
        );

        server.expect(requestTo("https://api.openai.test/v1/audio/transcriptions"))
                .andRespond(withSuccess("""
                        {"segments":[
                          {"start":0.0,"end":1.0,"text":"First"},
                          {"start":1.0,"end":2.0,"text":"   "}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.transcribe("transcription-job-5", new byte[] {1}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI transcription returned blank text for segment 1.");
        server.verify();
    }

    @Test
    void rejectsInvalidSegmentTimestamps() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = testRestClient(restClientBuilder);
        OpenAiTranscriptionProvider provider = new OpenAiTranscriptionProvider(
                openAiProperties("test-openai-key", "whisper-1", "https://api.openai.test", 5),
                restClient,
                objectMapper
        );

        server.expect(requestTo("https://api.openai.test/v1/audio/transcriptions"))
                .andRespond(withSuccess("""
                        {"segments":[
                          {"start":0.0,"end":1.0,"text":"First"},
                          {"start":2.0,"end":2.0,"text":"Second"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.transcribe("transcription-job-6", new byte[] {1}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI transcription returned invalid timestamps for segment 1.");
        server.verify();
    }

    private LinguaFrameProperties openAiProperties(String apiKey, String model, String baseUrl, int timeoutSeconds) {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getTranscription().setProvider("openai");
        properties.getTranscription().getOpenai().setApiKey(apiKey);
        properties.getTranscription().getOpenai().setModel(model);
        properties.getTranscription().getOpenai().setBaseUrl(baseUrl);
        properties.getTranscription().getOpenai().setTimeoutSeconds(timeoutSeconds);
        return properties;
    }

    private RestClient testRestClient(RestClient.Builder restClientBuilder) {
        return restClientBuilder
                .baseUrl("https://api.openai.test")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-openai-key")
                .build();
    }
}
