package com.linguaframe.job.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.service.impl.RedisLocalizationJobStatusCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisLocalizationJobStatusCacheServiceTests {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void disabledCacheBypassesRedisReadsAndWrites() {
        LinguaFrameProperties properties = properties(false, 30);
        RedisLocalizationJobStatusCacheService service = service(properties, objectMapper);
        LocalizationJobVo job = job("job-cache-disabled", LocalizationJobStatus.PROCESSING);

        Optional<LocalizationJobVo> cached = service.get(job.jobId());
        service.put(job);
        service.evict(job.jobId());

        assertThat(cached).isEmpty();
        verify(redisTemplate, never()).opsForValue();
        verify(redisTemplate, never()).delete(any(String.class));
    }

    @Test
    void returnsEmptyOnCacheMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("linguaframe:job-status:job-cache-miss")).thenReturn(null);
        RedisLocalizationJobStatusCacheService service = service(properties(true, 30), objectMapper);

        Optional<LocalizationJobVo> cached = service.get("job-cache-miss");

        assertThat(cached).isEmpty();
    }

    @Test
    void returnsCachedJobOnCacheHit() throws Exception {
        LocalizationJobVo job = job("job-cache-hit", LocalizationJobStatus.COMPLETED);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("linguaframe:job-status:job-cache-hit"))
                .thenReturn(objectMapper.writeValueAsString(job));
        RedisLocalizationJobStatusCacheService service = service(properties(true, 30), objectMapper);

        Optional<LocalizationJobVo> cached = service.get("job-cache-hit");

        assertThat(cached).contains(job);
    }

    @Test
    void writesCachedJobWithConfiguredTtl() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        RedisLocalizationJobStatusCacheService service = service(properties(true, 45), objectMapper);
        LocalizationJobVo job = job("job-cache-put", LocalizationJobStatus.PROCESSING);

        service.put(job);

        verify(valueOperations).set(
                eq("linguaframe:job-status:job-cache-put"),
                eq(objectMapper.writeValueAsString(job)),
                eq(Duration.ofSeconds(45))
        );
    }

    @Test
    void evictsCacheKey() {
        RedisLocalizationJobStatusCacheService service = service(properties(true, 30), objectMapper);

        service.evict("job-cache-evict");

        verify(redisTemplate).delete("linguaframe:job-status:job-cache-evict");
    }

    @Test
    void redisReadFailuresFailOpenAsCacheMiss() {
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));
        RedisLocalizationJobStatusCacheService service = service(properties(true, 30), objectMapper);

        Optional<LocalizationJobVo> cached = service.get("job-cache-error");

        assertThat(cached).isEmpty();
    }

    @Test
    void invalidJsonFailsOpenAsCacheMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("linguaframe:job-status:job-cache-invalid")).thenReturn("{bad json");
        RedisLocalizationJobStatusCacheService service = service(properties(true, 30), objectMapper);

        Optional<LocalizationJobVo> cached = service.get("job-cache-invalid");

        assertThat(cached).isEmpty();
    }

    @Test
    void redisWriteAndDeleteFailuresDoNotThrow() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(valueOperations)
                .set(any(String.class), any(String.class), any(Duration.class));
        doThrow(new IllegalStateException("redis unavailable"))
                .when(redisTemplate)
                .delete(any(String.class));
        RedisLocalizationJobStatusCacheService service = service(properties(true, 30), objectMapper);

        service.put(job("job-cache-write-error", LocalizationJobStatus.PROCESSING));
        service.evict("job-cache-delete-error");
    }

    @Test
    void jsonWriteFailuresDoNotThrow() {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        try {
            when(failingMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("bad") {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
        RedisLocalizationJobStatusCacheService service = service(properties(true, 30), failingMapper);

        service.put(job("job-cache-json-error", LocalizationJobStatus.PROCESSING));
    }

    private RedisLocalizationJobStatusCacheService service(
            LinguaFrameProperties properties,
            ObjectMapper mapper
    ) {
        return new RedisLocalizationJobStatusCacheService(properties, redisTemplate, mapper);
    }

    private LinguaFrameProperties properties(boolean enabled, int ttlSeconds) {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getJobStatusCache().setEnabled(enabled);
        properties.getJobStatusCache().setTtlSeconds(ttlSeconds);
        return properties;
    }

    private LocalizationJobVo job(String jobId, LocalizationJobStatus status) {
        return new LocalizationJobVo(
                jobId,
                "video-" + jobId,
                "zh-CN",
                null,
                status,
                Instant.parse("2026-06-27T05:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                0,
                null,
                List.of(),
                new JobUsageSummaryVo(0, 0, 0, BigDecimal.ZERO, null, null, null, null),
                new JobCacheSummaryVo(0, 0, 0),
                List.of(),
                null,
                null
        );
    }
}
