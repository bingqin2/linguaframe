package com.linguaframe.job.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateModelCallRecordCommand;
import com.linguaframe.job.domain.entity.ModelCallRecord;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.domain.enums.ModelCallStatus;
import com.linguaframe.job.repository.ModelCallRepository;
import com.linguaframe.job.service.impl.ModelCallAuditServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelCallAuditServiceTests {

    private final Instant now = Instant.parse("2026-06-26T10:00:00Z");

    @Test
    void recordsTranslationCostFromTokenUsage() {
        InMemoryModelCallRepository repository = new InMemoryModelCallRepository();
        ModelCallAuditService service = newService(repository, propertiesWithRates());

        var result = service.recordSuccess(new CreateModelCallRecordCommand(
                "audit-job-translation",
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSLATION,
                ModelCallProvider.OPENAI,
                "gpt-test",
                "openai-subtitle-translation-v1",
                125L,
                1000,
                500,
                null,
                null
        ));

        assertThat(result.estimatedCostUsd()).isEqualByComparingTo("0.00045000");
        assertThat(repository.records).hasSize(1);
        assertThat(repository.records.getFirst().status()).isEqualTo(ModelCallStatus.SUCCEEDED);
        assertThat(repository.records.getFirst().estimatedCostUsd()).isEqualByComparingTo("0.00045000");
    }

    @Test
    void recordsTranscriptionCostFromAudioSeconds() {
        InMemoryModelCallRepository repository = new InMemoryModelCallRepository();
        ModelCallAuditService service = newService(repository, propertiesWithRates());

        var result = service.recordSuccess(new CreateModelCallRecordCommand(
                "audit-job-transcription",
                LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSCRIPTION,
                ModelCallProvider.OPENAI,
                "whisper-test",
                "openai-audio-transcriptions-v1",
                250L,
                null,
                null,
                new BigDecimal("120.0"),
                null
        ));

        assertThat(result.estimatedCostUsd()).isEqualByComparingTo("0.01200000");
    }

    @Test
    void recordsTtsCostFromCharacterCount() {
        InMemoryModelCallRepository repository = new InMemoryModelCallRepository();
        ModelCallAuditService service = newService(repository, propertiesWithRates());

        var result = service.recordSuccess(new CreateModelCallRecordCommand(
                "audit-job-tts",
                LocalizationJobStage.DUBBING_AUDIO_GENERATION,
                ModelCallOperation.TTS,
                ModelCallProvider.OPENAI,
                "gpt-4o-mini-tts",
                "openai-tts-v1",
                100L,
                null,
                null,
                null,
                2000
        ));

        assertThat(result.estimatedCostUsd()).isEqualByComparingTo("0.03000000");
    }

    @Test
    void recordsEvaluationCostFromTokenUsage() {
        InMemoryModelCallRepository repository = new InMemoryModelCallRepository();
        ModelCallAuditService service = newService(repository, propertiesWithRates());

        var result = service.recordSuccess(new CreateModelCallRecordCommand(
                "audit-job-evaluation",
                LocalizationJobStage.TRANSLATION_QUALITY_EVALUATION,
                ModelCallOperation.EVALUATION,
                ModelCallProvider.OPENAI,
                "gpt-test",
                "openai-translation-quality-evaluation-v1",
                90L,
                900,
                300,
                null,
                null
        ));

        assertThat(result.estimatedCostUsd()).isEqualByComparingTo("0.00031500");
    }

    @Test
    void summarizesJobUsageAcrossCalls() {
        InMemoryModelCallRepository repository = new InMemoryModelCallRepository();
        ModelCallAuditService service = newService(repository, propertiesWithRates());
        service.recordSuccess(new CreateModelCallRecordCommand(
                "audit-job-summary",
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSLATION,
                ModelCallProvider.OPENAI,
                "gpt-test",
                "openai-subtitle-translation-v1",
                125L,
                1000,
                500,
                null,
                null
        ));
        service.recordFailure(new CreateModelCallRecordCommand(
                "audit-job-summary",
                LocalizationJobStage.DUBBING_AUDIO_GENERATION,
                ModelCallOperation.TTS,
                ModelCallProvider.OPENAI,
                "gpt-4o-mini-tts",
                "openai-tts-v1",
                75L,
                null,
                null,
                null,
                2000
        ), "OpenAI TTS request failed with status 401");

        var summary = service.summarizeJob("audit-job-summary");

        assertThat(summary.modelCallCount()).isEqualTo(2);
        assertThat(summary.failedModelCallCount()).isEqualTo(1);
        assertThat(summary.totalLatencyMs()).isEqualTo(200L);
        assertThat(summary.estimatedCostUsd()).isEqualByComparingTo("0.03045000");
        assertThat(summary.inputTokens()).isEqualTo(1000);
        assertThat(summary.outputTokens()).isEqualTo(500);
        assertThat(summary.characterCount()).isEqualTo(2000);
    }

    @Test
    void recordFailureTruncatesSafeErrorSummary() {
        InMemoryModelCallRepository repository = new InMemoryModelCallRepository();
        ModelCallAuditService service = newService(repository, propertiesWithRates());
        String longSafeError = "x".repeat(600);

        var result = service.recordFailure(new CreateModelCallRecordCommand(
                "audit-job-failure",
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSLATION,
                ModelCallProvider.OPENAI,
                "gpt-test",
                "openai-subtitle-translation-v1",
                125L,
                1000,
                500,
                null,
                null
        ), longSafeError);

        assertThat(result.status()).isEqualTo(ModelCallStatus.FAILED);
        assertThat(result.safeErrorSummary()).hasSize(512);
        assertThat(repository.records.getFirst().safeErrorSummary()).hasSize(512);
    }

    @Test
    void returnsEmptySummaryForJobWithoutModelCalls() {
        ModelCallAuditService service = newService(new InMemoryModelCallRepository(), propertiesWithRates());

        var summary = service.summarizeJob("missing-job");

        assertThat(summary.modelCallCount()).isZero();
        assertThat(summary.failedModelCallCount()).isZero();
        assertThat(summary.totalLatencyMs()).isZero();
        assertThat(summary.estimatedCostUsd()).isEqualByComparingTo("0.00000000");
        assertThat(summary.inputTokens()).isNull();
        assertThat(summary.outputTokens()).isNull();
        assertThat(summary.audioSeconds()).isNull();
        assertThat(summary.characterCount()).isNull();
    }

    private ModelCallAuditService newService(
            InMemoryModelCallRepository repository,
            LinguaFrameProperties properties
    ) {
        return new ModelCallAuditServiceImpl(
                repository,
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        );
    }

    private LinguaFrameProperties propertiesWithRates() {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getCost().setTranscriptionUsdPerMinute(new BigDecimal("0.006"));
        properties.getCost().setTranslationInputUsdPerMillionTokens(new BigDecimal("0.15"));
        properties.getCost().setTranslationOutputUsdPerMillionTokens(new BigDecimal("0.60"));
        properties.getCost().setTtsUsdPerMillionCharacters(new BigDecimal("15.00"));
        return properties;
    }

    private static class InMemoryModelCallRepository extends ModelCallRepository {

        private final List<ModelCallRecord> records = new ArrayList<>();

        private InMemoryModelCallRepository() {
            super(null);
        }

        @Override
        public void save(ModelCallRecord record) {
            records.add(record);
        }

        @Override
        public List<ModelCallRecord> findByJobId(String jobId) {
            return records.stream()
                    .filter(record -> record.jobId().equals(jobId))
                    .toList();
        }
    }
}
