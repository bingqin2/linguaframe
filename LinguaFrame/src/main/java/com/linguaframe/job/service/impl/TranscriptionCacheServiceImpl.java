package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.bo.CreateTranscriptionCacheEntryCommand;
import com.linguaframe.job.domain.bo.TranscriptionCacheLookupBo;
import com.linguaframe.job.domain.bo.TranscriptionResultBo;
import com.linguaframe.job.domain.bo.TranscriptionSegmentBo;
import com.linguaframe.job.domain.vo.TranscriptionCacheHitVo;
import com.linguaframe.job.repository.TranscriptionCacheRepository;
import com.linguaframe.job.service.TranscriptionCacheService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TranscriptionCacheServiceImpl implements TranscriptionCacheService {

    private final TranscriptionCacheRepository repository;
    private final ObjectMapper objectMapper;

    public TranscriptionCacheServiceImpl(TranscriptionCacheRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<TranscriptionCacheHitVo> findCachedTranscription(TranscriptionCacheLookupBo lookup) {
        return repository.findByCacheKey(lookup.cacheKey())
                .flatMap(record -> {
                    try {
                        TranscriptionResultBo result = objectMapper.readValue(
                                record.responseJson(),
                                TranscriptionCachePayload.class
                        ).toResult();
                        return Optional.of(new TranscriptionCacheHitVo(record.cacheKey(), record.sourceJobId(), result));
                    } catch (RuntimeException | JsonProcessingException ex) {
                        return Optional.empty();
                    }
                });
    }

    @Override
    public void storeTranscription(TranscriptionCacheLookupBo lookup, String jobId, TranscriptionResultBo result) {
        if (result.segments() == null || result.segments().isEmpty()) {
            throw new IllegalArgumentException("Transcription cache segments must not be empty.");
        }
        try {
            String responseJson = objectMapper.writeValueAsString(TranscriptionCachePayload.from(result));
            repository.saveIfAbsent(new CreateTranscriptionCacheEntryCommand(
                    lookup.cacheKey(),
                    lookup.audioHash(),
                    lookup.provider(),
                    lookup.model(),
                    lookup.promptVersion(),
                    responseJson,
                    jobId
            ));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Transcription cache response could not be serialized.", ex);
        }
    }

    private record TranscriptionCachePayload(List<TranscriptionSegmentBo> segments) {

        private static TranscriptionCachePayload from(TranscriptionResultBo result) {
            return new TranscriptionCachePayload(result.segments());
        }

        private TranscriptionResultBo toResult() {
            if (segments == null || segments.isEmpty()) {
                throw new IllegalArgumentException("Transcription cache segments must not be empty.");
            }
            return new TranscriptionResultBo(segments);
        }
    }
}
