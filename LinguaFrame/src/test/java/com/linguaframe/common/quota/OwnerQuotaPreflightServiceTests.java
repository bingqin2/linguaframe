package com.linguaframe.common.quota;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.common.security.DemoOwnerIdentityService;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.ModelCallAuditService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OwnerQuotaPreflightServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-28T12:34:56Z"),
            ZoneOffset.UTC
    );

    private final LinguaFrameProperties properties = new LinguaFrameProperties();
    private final LocalizationJobRepository jobRepository = mock(LocalizationJobRepository.class);
    private final ModelCallAuditService modelCallAuditService = mock(ModelCallAuditService.class);
    private final DemoOwnerIdentityService ownerIdentityService = () -> "owner-alpha";

    @Test
    void returnsAllowedWhenQuotaIsDisabledWithoutQueryingRepositories() {
        OwnerQuotaPreflightService service = service();

        OwnerQuotaPreflightVo preflight = service.getPreflight();

        assertThat(preflight.ownerId()).isEqualTo("owner-alpha");
        assertThat(preflight.enabled()).isFalse();
        assertThat(preflight.allowed()).isTrue();
        assertThat(preflight.blockingReasons()).isEmpty();
        verify(jobRepository, never()).countActiveJobsByOwnerId("owner-alpha");
        verify(jobRepository, never()).countQueuedJobsByOwnerId("owner-alpha");
        verify(modelCallAuditService, never()).summarizeDailyBudget("owner-alpha", Instant.parse("2026-06-28T00:00:00Z"));
    }

    @Test
    void allowsUploadWhenConfiguredLimitsAreNotExceeded() {
        enableQuota(3, 2, "0.25");
        when(jobRepository.countActiveJobsByOwnerId("owner-alpha")).thenReturn(2);
        when(jobRepository.countQueuedJobsByOwnerId("owner-alpha")).thenReturn(1);
        when(modelCallAuditService.summarizeDailyBudget("owner-alpha", Instant.parse("2026-06-28T00:00:00Z")))
                .thenReturn(new BigDecimal("0.10000000"));

        OwnerQuotaPreflightVo preflight = service().getPreflight();

        assertThat(preflight.allowed()).isTrue();
        assertThat(preflight.activeJobs()).isEqualTo(2);
        assertThat(preflight.queuedJobs()).isEqualTo(1);
        assertThat(preflight.dailyEstimatedCostUsd()).isEqualByComparingTo("0.10000000");
        assertThat(preflight.limits())
                .extracting(OwnerQuotaLimitVo::name)
                .containsExactly("activeJobs", "queuedJobs", "dailyCostUsd");
    }

    @Test
    void blocksUploadWhenActiveJobLimitIsReached() {
        enableQuota(2, 0, "0");
        when(jobRepository.countActiveJobsByOwnerId("owner-alpha")).thenReturn(2);
        when(jobRepository.countQueuedJobsByOwnerId("owner-alpha")).thenReturn(0);

        OwnerQuotaPreflightService service = service();
        OwnerQuotaPreflightVo preflight = service.getPreflight();

        assertThat(preflight.allowed()).isFalse();
        assertThat(preflight.blockingReasons())
                .containsExactly("Active job limit reached for owner owner-alpha: current 2, limit 2.");
        assertThatThrownBy(service::requireUploadAllowed)
                .isInstanceOf(OwnerQuotaExceededException.class)
                .hasMessageContaining("Active job limit reached");
    }

    @Test
    void blocksUploadWhenQueuedJobLimitIsReached() {
        enableQuota(0, 1, "0");
        when(jobRepository.countActiveJobsByOwnerId("owner-alpha")).thenReturn(1);
        when(jobRepository.countQueuedJobsByOwnerId("owner-alpha")).thenReturn(1);

        OwnerQuotaPreflightVo preflight = service().getPreflight();

        assertThat(preflight.allowed()).isFalse();
        assertThat(preflight.blockingReasons())
                .containsExactly("Queued job limit reached for owner owner-alpha: current 1, limit 1.");
    }

    @Test
    void blocksUploadWhenDailyBudgetLimitIsReached() {
        enableQuota(0, 0, "0.25");
        when(jobRepository.countActiveJobsByOwnerId("owner-alpha")).thenReturn(0);
        when(jobRepository.countQueuedJobsByOwnerId("owner-alpha")).thenReturn(0);
        when(modelCallAuditService.summarizeDailyBudget("owner-alpha", Instant.parse("2026-06-28T00:00:00Z")))
                .thenReturn(new BigDecimal("0.25000000"));

        OwnerQuotaPreflightVo preflight = service().getPreflight();

        assertThat(preflight.allowed()).isFalse();
        assertThat(preflight.blockingReasons())
                .containsExactly("Daily owner budget reached for owner owner-alpha on 2026-06-28: current estimated cost 0.25000000 USD, limit 0.25 USD.");
    }

    private OwnerQuotaPreflightService service() {
        return new OwnerQuotaPreflightServiceImpl(
                properties,
                ownerIdentityService,
                jobRepository,
                modelCallAuditService,
                FIXED_CLOCK
        );
    }

    private void enableQuota(int maxActiveJobs, int maxQueuedJobs, String maxDailyCostUsd) {
        properties.getOwnerQuota().setEnabled(true);
        properties.getOwnerQuota().setMaxActiveJobs(maxActiveJobs);
        properties.getOwnerQuota().setMaxQueuedJobs(maxQueuedJobs);
        properties.getOwnerQuota().setDailyBudgetGuardEnabled(new BigDecimal(maxDailyCostUsd).compareTo(BigDecimal.ZERO) > 0);
        properties.getOwnerQuota().setMaxDailyCostUsd(new BigDecimal(maxDailyCostUsd));
    }
}
