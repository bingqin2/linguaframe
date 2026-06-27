package com.linguaframe.common.runtime;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.common.runtime.domain.enums.RuntimeProbeStatus;
import com.linguaframe.common.runtime.domain.vo.RuntimeLiveCheckSummaryVo;
import com.linguaframe.common.runtime.service.impl.RuntimeLiveCheckServiceImpl;
import com.linguaframe.storage.service.ObjectStorageHealthCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeLiveCheckServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-28T00:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void reportsHealthyWhenAllDependenciesRespond() throws Exception {
        Fixtures fixtures = fixtures();
        RuntimeLiveCheckServiceImpl service = fixtures.service();

        RuntimeLiveCheckSummaryVo summary = service.check();

        assertThat(summary.healthy()).isTrue();
        assertThat(summary.checkedAt()).isEqualTo(Instant.parse("2026-06-28T00:00:00Z"));
        assertThat(summary.checks().keySet())
                .containsExactly("database", "redis", "rabbitmq", "minio", "ffmpeg");
        assertThat(summary.checks().get("database").status()).isEqualTo(RuntimeProbeStatus.UP);
        assertThat(summary.checks().get("redis").message()).isEqualTo("Redis ping succeeded");
        assertThat(summary.checks().get("rabbitmq").message()).isEqualTo("RabbitMQ connection opened");
        assertThat(summary.checks().get("minio").message()).isEqualTo("MinIO bucket is reachable");
        assertThat(summary.checks().get("ffmpeg").message()).isEqualTo("FFmpeg binary responded");
        verify(fixtures.rabbitConnection).close();
    }

    @Test
    void reportsDownWithSafeMessagesWhenDependencyFails() throws Exception {
        Fixtures fixtures = fixtures();
        when(fixtures.redisConnection.ping()).thenThrow(new IllegalStateException("secret redis password"));
        RuntimeLiveCheckServiceImpl service = fixtures.service();

        RuntimeLiveCheckSummaryVo summary = service.check();

        assertThat(summary.healthy()).isFalse();
        assertThat(summary.checks().get("redis").status()).isEqualTo(RuntimeProbeStatus.DOWN);
        assertThat(summary.checks().get("redis").message()).isEqualTo("Redis ping failed");
        assertThat(summary.toString()).doesNotContain("secret redis password");
    }

    private Fixtures fixtures() throws Exception {
        JdbcClient jdbcClient = mock(JdbcClient.class);
        JdbcClient.StatementSpec statementSpec = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<Integer> querySpec = mockQuerySpec();
        when(jdbcClient.sql("SELECT 1")).thenReturn(statementSpec);
        when(statementSpec.query(Integer.class)).thenReturn(querySpec);
        when(querySpec.single()).thenReturn(1);

        RedisConnection redisConnection = mock(RedisConnection.class);
        when(redisConnection.ping()).thenReturn("PONG");

        ConnectionFactory rabbitConnectionFactory = mock(ConnectionFactory.class);
        Connection rabbitConnection = mock(Connection.class);
        when(rabbitConnectionFactory.createConnection()).thenReturn(rabbitConnection);
        when(rabbitConnection.isOpen()).thenReturn(true);

        ObjectStorageHealthCheckService objectStorageHealthCheckService = mock(ObjectStorageHealthCheckService.class);
        when(objectStorageHealthCheckService.bucketExistsForHealthCheck()).thenReturn(true);

        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getFfmpeg().setBinaryPath("/bin/echo");

        return new Fixtures(
                jdbcClient,
                redisConnection,
                rabbitConnection,
                rabbitConnectionFactory,
                objectStorageHealthCheckService,
                properties
        );
    }

    @SuppressWarnings("unchecked")
    private JdbcClient.MappedQuerySpec<Integer> mockQuerySpec() {
        return mock(JdbcClient.MappedQuerySpec.class);
    }

    private record Fixtures(
            JdbcClient jdbcClient,
            RedisConnection redisConnection,
            Connection rabbitConnection,
            ConnectionFactory rabbitConnectionFactory,
            ObjectStorageHealthCheckService objectStorageHealthCheckService,
            LinguaFrameProperties properties
    ) {
        RuntimeLiveCheckServiceImpl service() {
            return new RuntimeLiveCheckServiceImpl(
                    jdbcClient,
                    redisTemplate(),
                    rabbitConnectionFactory,
                    objectStorageHealthCheckService,
                    properties,
                    FIXED_CLOCK
            );
        }

        private StringRedisTemplate redisTemplate() {
            StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
            RedisConnectionFactory redisConnectionFactory = mock(RedisConnectionFactory.class);
            when(redisTemplate.getConnectionFactory()).thenReturn(redisConnectionFactory);
            when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
            return redisTemplate;
        }
    }
}
