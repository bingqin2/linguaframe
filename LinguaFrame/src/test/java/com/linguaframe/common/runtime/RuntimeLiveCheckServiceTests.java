package com.linguaframe.common.runtime;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.common.runtime.domain.enums.RuntimeProbeStatus;
import com.linguaframe.common.runtime.domain.vo.RuntimeLiveCheckSummaryVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeProbeResultVo;
import com.linguaframe.common.runtime.service.OpenAiConnectivityCheckService;
import com.linguaframe.common.runtime.service.impl.OpenAiConnectivityCheckServiceImpl;
import com.linguaframe.common.runtime.service.impl.RuntimeLiveCheckServiceImpl;
import com.linguaframe.storage.service.ObjectStorageHealthCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RuntimeLiveCheckServiceTests {

    @Test
    void openAiConnectivityCheckIsSkippedByDefaultWithoutHttpCall() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        OpenAiConnectivityCheckServiceImpl service = new OpenAiConnectivityCheckServiceImpl(
                new LinguaFrameProperties(),
                restClientBuilder,
                false
        );

        RuntimeProbeResultVo result = service.check();

        assertThat(result.status()).isEqualTo(RuntimeProbeStatus.SKIPPED);
        assertThat(result.message()).isEqualTo("OpenAI connectivity check is disabled");
        assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0L);
        server.verify();
    }

    @Test
    void openAiConnectivityCheckReportsMissingConfigurationSafely() {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getOpenAiConnectivity().setEnabled(true);

        RuntimeProbeResultVo result = new OpenAiConnectivityCheckServiceImpl(
                properties,
                RestClient.builder(),
                false
        ).check();

        assertThat(result.status()).isEqualTo(RuntimeProbeStatus.DOWN);
        assertThat(result.message()).isEqualTo("OpenAI connectivity check is enabled but API key or model is missing");
        assertThat(result.message())
                .doesNotContain("apiKey")
                .doesNotContain("Bearer")
                .doesNotContain("sk-");
    }

    @Test
    void openAiConnectivityCheckUsesConfiguredModelAndBearerAuth() {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getOpenAiConnectivity().setEnabled(true);
        properties.getOpenAiConnectivity().setModel("gpt-test");
        properties.getTranslation().getOpenai().setApiKey("sk-test-secret");
        properties.getTranslation().getOpenai().setBaseUrl("https://api.example.test");
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://api.example.test/v1/models/gpt-test"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sk-test-secret"))
                .andRespond(withSuccess("{\"id\":\"gpt-test\"}", org.springframework.http.MediaType.APPLICATION_JSON));

        RuntimeProbeResultVo result = new OpenAiConnectivityCheckServiceImpl(properties, restClientBuilder, false).check();

        assertThat(result.status()).isEqualTo(RuntimeProbeStatus.UP);
        assertThat(result.message()).isEqualTo("OpenAI model metadata endpoint is reachable");
        assertThat(result.message()).doesNotContain("sk-test-secret").doesNotContain("Bearer");
        server.verify();
    }

    @Test
    void openAiConnectivityCheckFallsBackToProviderModel() {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getOpenAiConnectivity().setEnabled(true);
        properties.getTranslation().getOpenai().setApiKey("sk-test-secret");
        properties.getTranslation().getOpenai().setBaseUrl("https://api.example.test");
        properties.getTranslation().getOpenai().setModel("gpt-translation");
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://api.example.test/v1/models/gpt-translation"))
                .andRespond(withSuccess("{\"id\":\"gpt-translation\"}", org.springframework.http.MediaType.APPLICATION_JSON));

        RuntimeProbeResultVo result = new OpenAiConnectivityCheckServiceImpl(properties, restClientBuilder, false).check();

        assertThat(result.status()).isEqualTo(RuntimeProbeStatus.UP);
        server.verify();
    }

    @Test
    void openAiConnectivityCheckReportsProviderFailureSafely() {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getOpenAiConnectivity().setEnabled(true);
        properties.getOpenAiConnectivity().setModel("gpt-test");
        properties.getTranslation().getOpenai().setApiKey("sk-test-secret");
        properties.getTranslation().getOpenai().setBaseUrl("https://api.example.test");
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://api.example.test/v1/models/gpt-test"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"error\":\"secret detail\"}"));

        RuntimeProbeResultVo result = new OpenAiConnectivityCheckServiceImpl(properties, restClientBuilder, false).check();

        assertThat(result.status()).isEqualTo(RuntimeProbeStatus.DOWN);
        assertThat(result.message()).isEqualTo("OpenAI model metadata endpoint returned HTTP 401");
        assertThat(result.message())
                .doesNotContain("secret detail")
                .doesNotContain("sk-test-secret")
                .doesNotContain("Bearer");
        server.verify();
    }

    @Test
    void runtimeLiveChecksIncludesOpenAiProbeWithoutMakingHealthDependOnSkippedProbe() throws Exception {
        OpenAiConnectivityCheckService openAiCheckService = mock(OpenAiConnectivityCheckService.class);
        when(openAiCheckService.check()).thenReturn(new RuntimeProbeResultVo(
                RuntimeProbeStatus.SKIPPED,
                1L,
                "OpenAI connectivity check is disabled"
        ));
        RuntimeLiveCheckServiceImpl service = new RuntimeLiveCheckServiceImpl(
                jdbcClient(),
                redisTemplate(),
                rabbitConnectionFactory(),
                minioHealthCheck(),
                new LinguaFrameProperties(),
                openAiCheckService,
                Clock.fixed(Instant.parse("2026-06-28T00:00:00Z"), ZoneOffset.UTC)
        );

        RuntimeLiveCheckSummaryVo summary = service.check();

        assertThat(summary.healthy()).isTrue();
        assertThat(summary.checks()).containsKey("openai");
        assertThat(summary.checks().get("openai").status()).isEqualTo(RuntimeProbeStatus.SKIPPED);
    }

    private JdbcClient jdbcClient() {
        JdbcClient jdbcClient = mock(JdbcClient.class);
        JdbcClient.StatementSpec statementSpec = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<Integer> querySpec = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbcClient.sql("SELECT 1")).thenReturn(statementSpec);
        when(statementSpec.query(Integer.class)).thenReturn(querySpec);
        when(querySpec.single()).thenReturn(1);
        return jdbcClient;
    }

    private StringRedisTemplate redisTemplate() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");
        return redisTemplate;
    }

    private ConnectionFactory rabbitConnectionFactory() {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        Connection connection = mock(Connection.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        when(connection.isOpen()).thenReturn(true);
        return connectionFactory;
    }

    private ObjectStorageHealthCheckService minioHealthCheck() throws Exception {
        ObjectStorageHealthCheckService service = mock(ObjectStorageHealthCheckService.class);
        when(service.bucketExistsForHealthCheck()).thenReturn(true);
        return service;
    }
}
