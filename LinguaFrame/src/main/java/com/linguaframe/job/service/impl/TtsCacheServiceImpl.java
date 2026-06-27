package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.bo.CreateTtsCacheEntryCommand;
import com.linguaframe.job.domain.bo.TtsCacheLookupBo;
import com.linguaframe.job.domain.bo.TtsResultBo;
import com.linguaframe.job.domain.vo.TtsCacheHitVo;
import com.linguaframe.job.repository.TtsCacheRepository;
import com.linguaframe.job.service.TtsCacheService;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Optional;

@Service
public class TtsCacheServiceImpl implements TtsCacheService {

    private final TtsCacheRepository repository;
    private final ObjectMapper objectMapper;

    public TtsCacheServiceImpl(TtsCacheRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<TtsCacheHitVo> findCachedTts(TtsCacheLookupBo lookup) {
        return repository.findByCacheKey(lookup.cacheKey())
                .flatMap(record -> {
                    try {
                        TtsCachePayload payload = objectMapper.readValue(record.responseJson(), TtsCachePayload.class);
                        return Optional.of(new TtsCacheHitVo(
                                record.cacheKey(),
                                record.sourceJobId(),
                                new TtsResultBo(
                                        Base64.getDecoder().decode(payload.audioBase64()),
                                        payload.filename(),
                                        payload.contentType()
                                )
                        ));
                    } catch (RuntimeException | JsonProcessingException ex) {
                        return Optional.empty();
                    }
                });
    }

    @Override
    public void storeTts(TtsCacheLookupBo lookup, String jobId, TtsResultBo result) {
        if (result.audioContent() == null || result.audioContent().length == 0) {
            throw new IllegalArgumentException("TTS cache audio must not be empty.");
        }
        try {
            String responseJson = objectMapper.writeValueAsString(new TtsCachePayload(
                    Base64.getEncoder().encodeToString(result.audioContent()),
                    result.filename(),
                    result.contentType()
            ));
            repository.saveIfAbsent(new CreateTtsCacheEntryCommand(
                    lookup.cacheKey(),
                    lookup.textHash(),
                    lookup.language(),
                    lookup.provider(),
                    lookup.model(),
                    lookup.voice(),
                    responseJson,
                    jobId
            ));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("TTS cache response could not be serialized.", ex);
        }
    }

    private record TtsCachePayload(
            String audioBase64,
            String filename,
            String contentType
    ) {
    }
}
