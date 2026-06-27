package com.linguaframe.job.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateModelCallRecordCommand;
import com.linguaframe.job.domain.entity.ModelCallRecord;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallStatus;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.ModelCallVo;
import com.linguaframe.job.repository.ModelCallRepository;
import com.linguaframe.job.service.ModelCallAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ModelCallAuditServiceImpl implements ModelCallAuditService {

    private static final int COST_SCALE = 8;
    private static final int MAX_SAFE_ERROR_LENGTH = 512;
    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000L);
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60L);

    private final ModelCallRepository repository;
    private final LinguaFrameProperties properties;
    private final Clock clock;

    @Autowired
    public ModelCallAuditServiceImpl(
            ModelCallRepository repository,
            LinguaFrameProperties properties
    ) {
        this(repository, properties, Clock.systemUTC());
    }

    public ModelCallAuditServiceImpl(
            ModelCallRepository repository,
            LinguaFrameProperties properties,
            Clock clock
    ) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public ModelCallVo recordSuccess(CreateModelCallRecordCommand command) {
        return save(command, ModelCallStatus.SUCCEEDED, null);
    }

    @Override
    public ModelCallVo recordFailure(CreateModelCallRecordCommand command, String safeErrorSummary) {
        return save(command, ModelCallStatus.FAILED, truncate(safeErrorSummary));
    }

    @Override
    public List<ModelCallVo> listModelCalls(String jobId) {
        return repository.findByJobId(jobId).stream()
                .map(this::toVo)
                .toList();
    }

    @Override
    public JobUsageSummaryVo summarizeJob(String jobId) {
        List<ModelCallRecord> records = repository.findByJobId(jobId);
        int failedCount = (int) records.stream()
                .filter(record -> record.status() == ModelCallStatus.FAILED)
                .count();
        long totalLatencyMs = records.stream()
                .mapToLong(ModelCallRecord::latencyMs)
                .sum();
        BigDecimal estimatedCostUsd = records.stream()
                .map(ModelCallRecord::estimatedCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(COST_SCALE, RoundingMode.HALF_UP);
        Integer inputTokens = sumNullableIntegers(records.stream()
                .map(ModelCallRecord::inputTokens)
                .toList());
        Integer outputTokens = sumNullableIntegers(records.stream()
                .map(ModelCallRecord::outputTokens)
                .toList());
        BigDecimal audioSeconds = sumNullableDecimals(records.stream()
                .map(ModelCallRecord::audioSeconds)
                .toList());
        Integer characterCount = sumNullableIntegers(records.stream()
                .map(ModelCallRecord::characterCount)
                .toList());

        return new JobUsageSummaryVo(
                records.size(),
                failedCount,
                totalLatencyMs,
                estimatedCostUsd,
                inputTokens,
                outputTokens,
                audioSeconds,
                characterCount
        );
    }

    private ModelCallVo save(
            CreateModelCallRecordCommand command,
            ModelCallStatus status,
            String safeErrorSummary
    ) {
        ModelCallRecord record = new ModelCallRecord(
                UUID.randomUUID().toString(),
                command.jobId(),
                command.stage(),
                command.operation(),
                command.provider(),
                command.model(),
                command.promptVersion(),
                status,
                command.latencyMs(),
                command.inputTokens(),
                command.outputTokens(),
                command.audioSeconds(),
                command.characterCount(),
                truncate(command.inputSummary()),
                truncate(command.outputSummary()),
                properties.getCost().getBudgetIdentity(),
                estimateCost(command),
                safeErrorSummary,
                Instant.now(clock)
        );
        repository.save(record);
        return toVo(record);
    }

    private BigDecimal estimateCost(CreateModelCallRecordCommand command) {
        if (!properties.getCost().isEnabled()) {
            return zeroCost();
        }
        BigDecimal cost = switch (command.operation()) {
            case TRANSCRIPTION -> transcriptionCost(command);
            case TRANSLATION, EVALUATION -> translationCost(command);
            case TTS -> ttsCost(command);
        };
        return cost.setScale(COST_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal transcriptionCost(CreateModelCallRecordCommand command) {
        if (command.audioSeconds() == null) {
            return BigDecimal.ZERO;
        }
        return command.audioSeconds()
                .divide(SIXTY, 12, RoundingMode.HALF_UP)
                .multiply(properties.getCost().getTranscriptionUsdPerMinute());
    }

    private BigDecimal translationCost(CreateModelCallRecordCommand command) {
        BigDecimal inputCost = tokensCost(
                command.inputTokens(),
                properties.getCost().getTranslationInputUsdPerMillionTokens()
        );
        BigDecimal outputCost = tokensCost(
                command.outputTokens(),
                properties.getCost().getTranslationOutputUsdPerMillionTokens()
        );
        return inputCost.add(outputCost);
    }

    private BigDecimal ttsCost(CreateModelCallRecordCommand command) {
        if (command.characterCount() == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(command.characterCount())
                .divide(ONE_MILLION, 12, RoundingMode.HALF_UP)
                .multiply(properties.getCost().getTtsUsdPerMillionCharacters());
    }

    private BigDecimal tokensCost(Integer tokens, BigDecimal rate) {
        if (tokens == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(tokens)
                .divide(ONE_MILLION, 12, RoundingMode.HALF_UP)
                .multiply(rate);
    }

    private ModelCallVo toVo(ModelCallRecord record) {
        return new ModelCallVo(
                record.id(),
                record.jobId(),
                record.stage(),
                record.operation(),
                record.provider(),
                record.model(),
                record.promptVersion(),
                record.status(),
                record.latencyMs(),
                record.inputTokens(),
                record.outputTokens(),
                record.audioSeconds(),
                record.characterCount(),
                record.inputSummary(),
                record.outputSummary(),
                record.budgetIdentity(),
                record.estimatedCostUsd(),
                record.safeErrorSummary(),
                record.createdAt()
        );
    }

    @Override
    public BigDecimal summarizeDailyBudget(String budgetIdentity, Instant since) {
        return repository.sumEstimatedCostByBudgetIdentitySince(budgetIdentity, since);
    }

    private Integer sumNullableIntegers(List<Integer> values) {
        int sum = 0;
        boolean hasValue = false;
        for (Integer value : values) {
            if (value == null) {
                continue;
            }
            sum += value;
            hasValue = true;
        }
        return hasValue ? sum : null;
    }

    private BigDecimal sumNullableDecimals(List<BigDecimal> values) {
        BigDecimal sum = BigDecimal.ZERO;
        boolean hasValue = false;
        for (BigDecimal value : values) {
            if (value == null) {
                continue;
            }
            sum = sum.add(value);
            hasValue = true;
        }
        return hasValue ? sum : null;
    }

    private BigDecimal zeroCost() {
        return BigDecimal.ZERO.setScale(COST_SCALE, RoundingMode.HALF_UP);
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_SAFE_ERROR_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_SAFE_ERROR_LENGTH);
    }
}
