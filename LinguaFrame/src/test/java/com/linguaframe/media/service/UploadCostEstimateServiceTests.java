package com.linguaframe.media.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.common.quota.OwnerQuotaLimitVo;
import com.linguaframe.common.quota.OwnerQuotaPreflightService;
import com.linguaframe.common.quota.OwnerQuotaPreflightVo;
import com.linguaframe.job.service.ModelCallAuditService;
import com.linguaframe.media.domain.bo.UploadCostEstimateOptionsBo;
import com.linguaframe.media.domain.enums.MediaUploadValidationCode;
import com.linguaframe.media.domain.vo.MediaUploadValidationVo;
import com.linguaframe.media.domain.vo.UploadCostEstimateVo;
import com.linguaframe.media.service.impl.UploadCostEstimateServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UploadCostEstimateServiceTests {

    private final LinguaFrameProperties properties = new LinguaFrameProperties();
    private final StubValidationService validationService = new StubValidationService();
    private final OwnerQuotaPreflightService ownerQuotaPreflightService = mock(OwnerQuotaPreflightService.class);
    private final ModelCallAuditService modelCallAuditService = mock(ModelCallAuditService.class);
    private final UploadCostEstimateService service = new UploadCostEstimateServiceImpl(
            properties,
            validationService,
            ownerQuotaPreflightService,
            modelCallAuditService
    );

    @Test
    void estimatesProviderStagesAndBudgetForValidUpload() {
        properties.getCost().setTranscriptionUsdPerMinute(new BigDecimal("0.006"));
        properties.getCost().setTranslationInputUsdPerMillionTokens(new BigDecimal("5"));
        properties.getCost().setTranslationOutputUsdPerMillionTokens(new BigDecimal("15"));
        properties.getCost().setTtsUsdPerMillionCharacters(new BigDecimal("15"));
        properties.getCost().setBudgetGuardEnabled(true);
        properties.getCost().setMaxJobCostUsd(new BigDecimal("1.00"));
        properties.getCost().setDailyBudgetGuardEnabled(true);
        properties.getCost().setMaxDailyCostUsd(new BigDecimal("2.00"));
        when(modelCallAuditService.summarizeDailyBudget(any(), any())).thenReturn(new BigDecimal("0.10"));
        when(ownerQuotaPreflightService.getPreflight()).thenReturn(ownerPreflight(true, new BigDecimal("0.10"), new BigDecimal("2.00")));
        validationService.next = validValidation(90);
        MockMultipartFile file = videoFile();

        UploadCostEstimateVo estimate = service.estimate(file, new UploadCostEstimateOptionsBo(
                " zh-CN ",
                "",
                "formal",
                "high_contrast",
                "Maya => 玛雅",
                "balanced",
                "tears-showcase"
        ));

        assertThat(estimate.overallStatus()).isEqualTo("READY");
        assertThat(estimate.targetLanguage()).isEqualTo("zh-CN");
        assertThat(estimate.translationStyle()).isEqualTo("FORMAL");
        assertThat(estimate.subtitleStylePreset()).isEqualTo("HIGH_CONTRAST");
        assertThat(estimate.subtitlePolishingMode()).isEqualTo("BALANCED");
        assertThat(estimate.demoProfileId()).isEqualTo("tears-showcase");
        assertThat(estimate.translationGlossaryEntryCount()).isEqualTo(1);
        assertThat(estimate.durationSeconds()).isEqualTo(90);
        assertThat(estimate.estimatedCostUsd()).isGreaterThan(BigDecimal.ZERO);
        assertThat(estimate.estimatedCostUsdUpper()).isGreaterThan(estimate.estimatedCostUsdLower());
        assertThat(estimate.stages())
                .extracting("id")
                .contains("transcription", "translation", "subtitlePolishing", "tts", "subtitleBurnIn");
        assertThat(estimate.budgets())
                .extracting("id")
                .contains("jobCost", "dailyCost", "ownerDailyCost");
        assertThat(estimate.recommendedNextAction()).contains("Upload");
    }

    @Test
    void blocksWhenValidationFails() {
        validationService.next = new MediaUploadValidationVo(
                false,
                MediaUploadValidationCode.DURATION_TOO_LONG,
                "The uploaded video exceeds the 300 second duration limit.",
                "long.mp4",
                "video/mp4",
                10,
                100,
                301,
                300,
                List.of("video/mp4")
        );
        when(ownerQuotaPreflightService.getPreflight()).thenReturn(ownerPreflight(false, BigDecimal.ZERO, BigDecimal.ZERO));

        UploadCostEstimateVo estimate = service.estimate(videoFile(), UploadCostEstimateOptionsBo.empty());

        assertThat(estimate.overallStatus()).isEqualTo("BLOCKED");
        assertThat(estimate.validationCode()).isEqualTo("DURATION_TOO_LONG");
        assertThat(estimate.estimatedCostUsd()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(estimate.recommendedNextAction()).contains("Replace");
    }

    @Test
    void blocksWhenEstimatedJobCostExceedsConfiguredGuard() {
        properties.getCost().setTranscriptionUsdPerMinute(new BigDecimal("1.00"));
        properties.getCost().setBudgetGuardEnabled(true);
        properties.getCost().setMaxJobCostUsd(new BigDecimal("0.50"));
        validationService.next = validValidation(60);
        when(ownerQuotaPreflightService.getPreflight()).thenReturn(ownerPreflight(true, BigDecimal.ZERO, BigDecimal.ZERO));

        UploadCostEstimateVo estimate = service.estimate(videoFile(), UploadCostEstimateOptionsBo.empty());

        assertThat(estimate.overallStatus()).isEqualTo("BLOCKED");
        assertThat(estimate.budgets())
                .anySatisfy(budget -> {
                    assertThat(budget.id()).isEqualTo("jobCost");
                    assertThat(budget.status()).isEqualTo("BLOCKED");
                });
        assertThat(estimate.recommendedNextAction()).contains("cost");
    }

    private static MockMultipartFile videoFile() {
        return new MockMultipartFile("file", "sample.mp4", "video/mp4", new byte[] {1, 2, 3});
    }

    private static MediaUploadValidationVo validValidation(int durationSeconds) {
        return new MediaUploadValidationVo(
                true,
                MediaUploadValidationCode.READY,
                "File is ready for upload.",
                "sample.mp4",
                "video/mp4",
                3,
                100,
                durationSeconds,
                300,
                List.of("video/mp4")
        );
    }

    private static OwnerQuotaPreflightVo ownerPreflight(boolean allowed, BigDecimal current, BigDecimal limit) {
        return new OwnerQuotaPreflightVo(
                "demo-owner",
                limit.compareTo(BigDecimal.ZERO) > 0,
                allowed,
                0,
                0,
                current,
                LocalDate.parse("2026-06-29"),
                List.of(new OwnerQuotaLimitVo("dailyCostUsd", limit.compareTo(BigDecimal.ZERO) > 0, limit, current)),
                allowed ? List.of() : List.of("Owner quota is already blocked.")
        );
    }

    private static final class StubValidationService implements MediaUploadValidationService {
        private MediaUploadValidationVo next = validValidation(60);

        @Override
        public MediaUploadValidationVo validate(MultipartFile file) {
            return next;
        }
    }
}
