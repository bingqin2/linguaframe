package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.bo.CreateQualityEvaluationCacheEntryCommand;
import com.linguaframe.job.domain.bo.QualityEvaluationCacheLookupBo;
import com.linguaframe.job.domain.bo.QualityEvaluationResultBo;
import com.linguaframe.job.domain.vo.QualityEvaluationCacheHitVo;
import com.linguaframe.job.repository.QualityEvaluationCacheRepository;
import com.linguaframe.job.service.QualityEvaluationCacheService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class QualityEvaluationCacheServiceImpl implements QualityEvaluationCacheService {

    private final QualityEvaluationCacheRepository repository;
    private final ObjectMapper objectMapper;

    public QualityEvaluationCacheServiceImpl(QualityEvaluationCacheRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<QualityEvaluationCacheHitVo> findCachedEvaluation(QualityEvaluationCacheLookupBo lookup) {
        return repository.findByCacheKey(lookup.cacheKey())
                .flatMap(record -> {
                    try {
                        QualityEvaluationResultBo result = objectMapper.readValue(
                                record.responseJson(),
                                QualityEvaluationResultBo.class
                        );
                        return Optional.of(new QualityEvaluationCacheHitVo(record.cacheKey(), record.sourceJobId(), result));
                    } catch (RuntimeException | JsonProcessingException ex) {
                        return Optional.empty();
                    }
                });
    }

    @Override
    public void storeEvaluation(QualityEvaluationCacheLookupBo lookup, String jobId, QualityEvaluationResultBo result) {
        try {
            String responseJson = objectMapper.writeValueAsString(result);
            repository.saveIfAbsent(new CreateQualityEvaluationCacheEntryCommand(
                    lookup.cacheKey(),
                    lookup.sourceHash(),
                    lookup.targetHash(),
                    lookup.language(),
                    lookup.provider(),
                    lookup.model(),
                    lookup.promptVersion(),
                    responseJson,
                    jobId
            ));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Quality evaluation cache response could not be serialized.", ex);
        }
    }
}
