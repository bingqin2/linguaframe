package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.bo.CreateTranslationCacheEntryCommand;
import com.linguaframe.job.domain.bo.TranslationCacheLookupBo;
import com.linguaframe.job.domain.bo.TranslationResultBo;
import com.linguaframe.job.domain.vo.TranslationCacheHitVo;
import com.linguaframe.job.repository.TranslationCacheRepository;
import com.linguaframe.job.service.TranslationCacheService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class TranslationCacheServiceImpl implements TranslationCacheService {

    private final TranslationCacheRepository repository;
    private final ObjectMapper objectMapper;

    public TranslationCacheServiceImpl(TranslationCacheRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<TranslationCacheHitVo> findCachedTranslation(TranslationCacheLookupBo lookup) {
        return repository.findByCacheKey(lookup.cacheKey())
                .flatMap(entry -> {
                    try {
                        return Optional.of(new TranslationCacheHitVo(
                                entry.cacheKey(),
                                entry.sourceJobId(),
                                objectMapper.readValue(entry.responseJson(), TranslationResultBo.class)
                        ));
                    } catch (JsonProcessingException ex) {
                        return Optional.empty();
                    }
                });
    }

    @Override
    public void storeTranslation(TranslationCacheLookupBo lookup, String jobId, TranslationResultBo result) {
        validate(result);
        repository.saveIfAbsent(new CreateTranslationCacheEntryCommand(
                lookup.cacheKey(),
                lookup.sourceHash(),
                lookup.targetLanguage(),
                lookup.provider(),
                lookup.model(),
                lookup.promptVersion(),
                lookup.translationGlossaryHash(),
                writeJson(result),
                jobId
        ));
    }

    private void validate(TranslationResultBo result) {
        if (result == null || result.segments() == null || result.segments().isEmpty()) {
            throw new IllegalArgumentException("Translation cache result must contain segments.");
        }
    }

    private String writeJson(TranslationResultBo result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Translation cache result could not be serialized.", ex);
        }
    }
}
