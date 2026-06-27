package com.linguaframe.common.runtime.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.common.runtime.domain.enums.RuntimeProbeStatus;
import com.linguaframe.common.runtime.domain.vo.RuntimeLiveCheckSummaryVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeProbeResultVo;
import com.linguaframe.common.runtime.service.RuntimeLiveCheckService;
import com.linguaframe.storage.service.ObjectStorageHealthCheckService;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class RuntimeLiveCheckServiceImpl implements RuntimeLiveCheckService {

    private static final Duration FFMPEG_TIMEOUT = Duration.ofSeconds(3);

    private final JdbcClient jdbcClient;
    private final StringRedisTemplate redisTemplate;
    private final ConnectionFactory rabbitConnectionFactory;
    private final ObjectStorageHealthCheckService objectStorageHealthCheckService;
    private final LinguaFrameProperties properties;
    private final Clock clock;

    @Autowired
    public RuntimeLiveCheckServiceImpl(
            JdbcClient jdbcClient,
            StringRedisTemplate redisTemplate,
            ConnectionFactory rabbitConnectionFactory,
            ObjectStorageHealthCheckService objectStorageHealthCheckService,
            LinguaFrameProperties properties
    ) {
        this(jdbcClient, redisTemplate, rabbitConnectionFactory, objectStorageHealthCheckService, properties, Clock.systemUTC());
    }

    public RuntimeLiveCheckServiceImpl(
            JdbcClient jdbcClient,
            StringRedisTemplate redisTemplate,
            ConnectionFactory rabbitConnectionFactory,
            ObjectStorageHealthCheckService objectStorageHealthCheckService,
            LinguaFrameProperties properties,
            Clock clock
    ) {
        this.jdbcClient = jdbcClient;
        this.redisTemplate = redisTemplate;
        this.rabbitConnectionFactory = rabbitConnectionFactory;
        this.objectStorageHealthCheckService = objectStorageHealthCheckService;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public RuntimeLiveCheckSummaryVo check() {
        Map<String, RuntimeProbeResultVo> checks = new LinkedHashMap<>();
        checks.put("database", probe("Database query succeeded", "Database query failed", this::checkDatabase));
        checks.put("redis", probe("Redis ping succeeded", "Redis ping failed", this::checkRedis));
        checks.put("rabbitmq", probe("RabbitMQ connection opened", "RabbitMQ connection failed", this::checkRabbitmq));
        checks.put("minio", probe("MinIO bucket is reachable", "MinIO bucket check failed", this::checkMinio));
        checks.put("ffmpeg", probe("FFmpeg binary responded", "FFmpeg binary check failed", this::checkFfmpeg));
        boolean healthy = checks.values().stream()
                .noneMatch(result -> result.status() == RuntimeProbeStatus.DOWN);
        return new RuntimeLiveCheckSummaryVo(healthy, Instant.now(clock), checks);
    }

    private RuntimeProbeResultVo probe(String successMessage, String failureMessage, ProbeAction action) {
        long started = System.nanoTime();
        try {
            boolean ok = action.run();
            return new RuntimeProbeResultVo(
                    ok ? RuntimeProbeStatus.UP : RuntimeProbeStatus.DOWN,
                    elapsedMs(started),
                    ok ? successMessage : failureMessage
            );
        } catch (Exception ex) {
            return new RuntimeProbeResultVo(RuntimeProbeStatus.DOWN, elapsedMs(started), failureMessage);
        }
    }

    private boolean checkDatabase() {
        Integer value = jdbcClient.sql("SELECT 1").query(Integer.class).single();
        return value != null && value == 1;
    }

    private boolean checkRedis() {
        RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
        if (connectionFactory == null) {
            return false;
        }
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String pong = connection.ping();
            return "PONG".equalsIgnoreCase(pong);
        }
    }

    private boolean checkRabbitmq() {
        Connection connection = rabbitConnectionFactory.createConnection();
        try {
            return connection.isOpen();
        } finally {
            connection.close();
        }
    }

    private boolean checkMinio() throws Exception {
        return objectStorageHealthCheckService.bucketExistsForHealthCheck();
    }

    private boolean checkFfmpeg() {
        String binaryPath = properties.getFfmpeg().getBinaryPath();
        if (binaryPath == null || binaryPath.isBlank()) {
            return false;
        }
        Process process = null;
        try {
            process = new ProcessBuilder(binaryPath, "-version")
                    .redirectErrorStream(true)
                    .start();
            boolean exited = process.waitFor(FFMPEG_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return exited && process.exitValue() == 0;
        } catch (Exception ex) {
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private long elapsedMs(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    @FunctionalInterface
    private interface ProbeAction {

        boolean run() throws Exception;
    }
}
