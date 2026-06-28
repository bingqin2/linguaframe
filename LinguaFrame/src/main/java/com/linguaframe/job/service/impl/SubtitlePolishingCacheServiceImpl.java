package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.bo.CreateSubtitlePolishingCacheEntryCommand;
import com.linguaframe.job.domain.bo.SubtitlePolishingCacheLookupBo;
import com.linguaframe.job.domain.bo.SubtitlePolishingResultBo;
import com.linguaframe.job.domain.vo.SubtitlePolishingCacheHitVo;
import com.linguaframe.job.repository.SubtitlePolishingCacheRepository;
import com.linguaframe.job.service.SubtitlePolishingCacheService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SubtitlePolishingCacheServiceImpl implements SubtitlePolishingCacheService {

    private final SubtitlePolishingCacheRepository repository;
    private final ObjectMapper objectMapper;

    public SubtitlePolishingCacheServiceImpl(SubtitlePolishingCacheRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<SubtitlePolishingCacheHitVo> findCachedPolishing(SubtitlePolishingCacheLookupBo lookup) {
        return repository.findByCacheKey(lookup.cacheKey())
                .flatMap(entry -> {
                    try {
                        return Optional.of(new SubtitlePolishingCacheHitVo(
                                entry.cacheKey(),
                                entry.sourceJobId(),
                                objectMapper.readValue(entry.responseJson(), SubtitlePolishingResultBo.class)
                        ));
                    } catch (JsonProcessingException ex) {
                        return Optional.empty();
                    }
                });
    }

    @Override
    public void storePolishing(SubtitlePolishingCacheLookupBo lookup, String jobId, SubtitlePolishingResultBo result) {
        validate(result);
        repository.saveIfAbsent(new CreateSubtitlePolishingCacheEntryCommand(
                lookup.cacheKey(),
                lookup.sourceHash(),
                lookup.targetLanguage(),
                lookup.provider(),
                lookup.model(),
                lookup.promptVersion(),
                lookup.subtitlePolishingMode(),
                writeJson(result),
                jobId
        ));
    }

    private void validate(SubtitlePolishingResultBo result) {
        if (result == null || result.segments() == null || result.segments().isEmpty()) {
            throw new IllegalArgumentException("Subtitle polishing cache result must contain segments.");
        }
    }

    private String writeJson(SubtitlePolishingResultBo result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Subtitle polishing cache result could not be serialized.", ex);
        }
    }
}
