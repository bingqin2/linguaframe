package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.CreateModelCallRecordCommand;
import com.linguaframe.job.domain.enums.ModelCallStatus;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.ModelCallVo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class RecordingModelCallAuditService implements ModelCallAuditService {

    public final List<CreateModelCallRecordCommand> successCommands = new ArrayList<>();
    public final List<CreateModelCallRecordCommand> failureCommands = new ArrayList<>();
    public final List<String> failureSummaries = new ArrayList<>();

    @Override
    public ModelCallVo recordSuccess(CreateModelCallRecordCommand command) {
        successCommands.add(command);
        return toVo(command, ModelCallStatus.SUCCEEDED, null);
    }

    @Override
    public ModelCallVo recordFailure(CreateModelCallRecordCommand command, String safeErrorSummary) {
        failureCommands.add(command);
        failureSummaries.add(safeErrorSummary);
        return toVo(command, ModelCallStatus.FAILED, safeErrorSummary);
    }

    @Override
    public List<ModelCallVo> listModelCalls(String jobId) {
        return List.of();
    }

    @Override
    public JobUsageSummaryVo summarizeJob(String jobId) {
        return new JobUsageSummaryVo(0, 0, 0L, BigDecimal.ZERO, null, null, null, null);
    }

    private ModelCallVo toVo(
            CreateModelCallRecordCommand command,
            ModelCallStatus status,
            String safeErrorSummary
    ) {
        return new ModelCallVo(
                "recorded-call-" + (successCommands.size() + failureCommands.size()),
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
                command.inputSummary(),
                command.outputSummary(),
                BigDecimal.ZERO,
                safeErrorSummary,
                Instant.parse("2026-06-26T00:00:00Z")
        );
    }
}
