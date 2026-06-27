package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.service.LocalizationJobStatusCacheService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class RedisLocalizationJobStatusCacheService implements LocalizationJobStatusCacheService {

    private static final String KEY_PREFIX = "linguaframe:job-status:";

    private final LinguaFrameProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisLocalizationJobStatusCacheService(
            LinguaFrameProperties properties,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<LocalizationJobVo> get(String jobId) {
        if (!properties.getJobStatusCache().isEnabled()) {
            return Optional.empty();
        }
        try {
            String value = redisTemplate.opsForValue().get(key(jobId));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, LocalizationJobVo.class));
        } catch (RuntimeException | JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void put(LocalizationJobVo job) {
        if (!properties.getJobStatusCache().isEnabled()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    key(job.jobId()),
                    objectMapper.writeValueAsString(job),
                    Duration.ofSeconds(properties.getJobStatusCache().getTtlSeconds())
            );
        } catch (RuntimeException | JsonProcessingException exception) {
            // Cache writes are best-effort; MySQL remains the source of truth.
        }
    }

    @Override
    public void evict(String jobId) {
        if (!properties.getJobStatusCache().isEnabled()) {
            return;
        }
        try {
            redisTemplate.delete(key(jobId));
        } catch (RuntimeException exception) {
            // Cache eviction is best-effort; following reads can still rebuild from MySQL after TTL.
        }
    }

    private String key(String jobId) {
        return KEY_PREFIX + jobId;
    }
}
