package com.linguaframe.common.quota;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.common.security.DemoOwnerIdentityService;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.ModelCallAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class OwnerQuotaPreflightServiceImpl implements OwnerQuotaPreflightService {

    private final LinguaFrameProperties properties;
    private final DemoOwnerIdentityService ownerIdentityService;
    private final LocalizationJobRepository jobRepository;
    private final ModelCallAuditService modelCallAuditService;
    private final Clock clock;

    @Autowired
    public OwnerQuotaPreflightServiceImpl(
            LinguaFrameProperties properties,
            DemoOwnerIdentityService ownerIdentityService,
            LocalizationJobRepository jobRepository,
            ModelCallAuditService modelCallAuditService
    ) {
        this(properties, ownerIdentityService, jobRepository, modelCallAuditService, Clock.systemUTC());
    }

    public OwnerQuotaPreflightServiceImpl(
            LinguaFrameProperties properties,
            DemoOwnerIdentityService ownerIdentityService,
            LocalizationJobRepository jobRepository,
            ModelCallAuditService modelCallAuditService,
            Clock clock
    ) {
        this.properties = properties;
        this.ownerIdentityService = ownerIdentityService;
        this.jobRepository = jobRepository;
        this.modelCallAuditService = modelCallAuditService;
        this.clock = clock;
    }

    @Override
    public OwnerQuotaPreflightVo getPreflight() {
        String ownerId = ownerIdentityService.currentOwnerId();
        LocalDate budgetDate = LocalDate.now(clock);
        if (!properties.getOwnerQuota().isEnabled()) {
            return new OwnerQuotaPreflightVo(
                    ownerId,
                    false,
                    true,
                    0,
                    0,
                    BigDecimal.ZERO,
                    budgetDate,
                    List.of(),
                    List.of()
            );
        }

        int activeJobs = jobRepository.countActiveJobsByOwnerId(ownerId);
        int queuedJobs = jobRepository.countQueuedJobsByOwnerId(ownerId);
        BigDecimal dailyEstimatedCostUsd = dailyEstimatedCost(ownerId, budgetDate);
        List<OwnerQuotaLimitVo> limits = limits(activeJobs, queuedJobs, dailyEstimatedCostUsd);
        List<String> blockingReasons = blockingReasons(ownerId, budgetDate, activeJobs, queuedJobs, dailyEstimatedCostUsd);

        return new OwnerQuotaPreflightVo(
                ownerId,
                true,
                blockingReasons.isEmpty(),
                activeJobs,
                queuedJobs,
                dailyEstimatedCostUsd,
                budgetDate,
                limits,
                blockingReasons
        );
    }

    @Override
    public void requireUploadAllowed() {
        OwnerQuotaPreflightVo preflight = getPreflight();
        if (!preflight.allowed()) {
            throw new OwnerQuotaExceededException(preflight);
        }
    }

    private BigDecimal dailyEstimatedCost(String ownerId, LocalDate budgetDate) {
        if (!dailyBudgetEnabled()) {
            return BigDecimal.ZERO;
        }
        Instant since = budgetDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        return modelCallAuditService.summarizeDailyBudget(ownerId, since);
    }

    private List<OwnerQuotaLimitVo> limits(
            int activeJobs,
            int queuedJobs,
            BigDecimal dailyEstimatedCostUsd
    ) {
        List<OwnerQuotaLimitVo> limits = new ArrayList<>();
        limits.add(new OwnerQuotaLimitVo(
                "activeJobs",
                properties.getOwnerQuota().getMaxActiveJobs() > 0,
                BigDecimal.valueOf(properties.getOwnerQuota().getMaxActiveJobs()),
                BigDecimal.valueOf(activeJobs)
        ));
        limits.add(new OwnerQuotaLimitVo(
                "queuedJobs",
                properties.getOwnerQuota().getMaxQueuedJobs() > 0,
                BigDecimal.valueOf(properties.getOwnerQuota().getMaxQueuedJobs()),
                BigDecimal.valueOf(queuedJobs)
        ));
        limits.add(new OwnerQuotaLimitVo(
                "dailyCostUsd",
                dailyBudgetEnabled(),
                properties.getOwnerQuota().getMaxDailyCostUsd(),
                dailyEstimatedCostUsd
        ));
        return limits;
    }

    private List<String> blockingReasons(
            String ownerId,
            LocalDate budgetDate,
            int activeJobs,
            int queuedJobs,
            BigDecimal dailyEstimatedCostUsd
    ) {
        List<String> reasons = new ArrayList<>();
        int activeLimit = properties.getOwnerQuota().getMaxActiveJobs();
        if (activeLimit > 0 && activeJobs >= activeLimit) {
            reasons.add("Active job limit reached for owner " + ownerId + ": current " + activeJobs + ", limit " + activeLimit + ".");
        }
        int queuedLimit = properties.getOwnerQuota().getMaxQueuedJobs();
        if (queuedLimit > 0 && queuedJobs >= queuedLimit) {
            reasons.add("Queued job limit reached for owner " + ownerId + ": current " + queuedJobs + ", limit " + queuedLimit + ".");
        }
        BigDecimal dailyLimit = properties.getOwnerQuota().getMaxDailyCostUsd();
        if (dailyBudgetEnabled() && dailyEstimatedCostUsd.compareTo(dailyLimit) >= 0) {
            reasons.add("Daily owner budget reached for owner " + ownerId
                    + " on " + budgetDate
                    + ": current estimated cost " + dailyEstimatedCostUsd
                    + " USD, limit " + dailyLimit + " USD.");
        }
        return reasons;
    }

    private boolean dailyBudgetEnabled() {
        return properties.getOwnerQuota().isDailyBudgetGuardEnabled()
                && properties.getOwnerQuota().getMaxDailyCostUsd().compareTo(BigDecimal.ZERO) > 0;
    }
}
