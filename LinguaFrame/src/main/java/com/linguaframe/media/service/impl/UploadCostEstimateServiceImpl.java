package com.linguaframe.media.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.common.quota.OwnerQuotaLimitVo;
import com.linguaframe.common.quota.OwnerQuotaPreflightService;
import com.linguaframe.common.quota.OwnerQuotaPreflightVo;
import com.linguaframe.demo.service.DemoRunProfileService;
import com.linguaframe.demo.service.impl.InMemoryDemoRunProfileService;
import com.linguaframe.job.domain.bo.TranslationGlossaryBo;
import com.linguaframe.job.domain.enums.SubtitlePolishingMode;
import com.linguaframe.job.domain.enums.SubtitleStylePreset;
import com.linguaframe.job.domain.enums.TranslationStyle;
import com.linguaframe.job.service.ModelCallAuditService;
import com.linguaframe.job.service.impl.TranslationGlossaryParser;
import com.linguaframe.media.domain.bo.UploadCostEstimateOptionsBo;
import com.linguaframe.media.domain.vo.MediaUploadValidationVo;
import com.linguaframe.media.domain.vo.UploadCostEstimateBudgetVo;
import com.linguaframe.media.domain.vo.UploadCostEstimateStageVo;
import com.linguaframe.media.domain.vo.UploadCostEstimateVo;
import com.linguaframe.media.service.MediaUploadValidationService;
import com.linguaframe.media.service.UploadCostEstimateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class UploadCostEstimateServiceImpl implements UploadCostEstimateService {

    private static final String DEFAULT_TARGET_LANGUAGE = "zh-CN";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_ATTENTION = "ATTENTION";
    private static final String STATUS_BLOCKED = "BLOCKED";
    private static final BigDecimal TOKEN_DIVISOR = new BigDecimal("4");
    private static final BigDecimal MILLION = new BigDecimal("1000000");
    private static final BigDecimal RANGE_LOW = new BigDecimal("0.75");
    private static final BigDecimal RANGE_HIGH = new BigDecimal("1.25");

    private final LinguaFrameProperties properties;
    private final MediaUploadValidationService validationService;
    private final OwnerQuotaPreflightService ownerQuotaPreflightService;
    private final ModelCallAuditService modelCallAuditService;
    private final TranslationGlossaryParser translationGlossaryParser;
    private final DemoRunProfileService demoRunProfileService;

    public UploadCostEstimateServiceImpl(
            LinguaFrameProperties properties,
            MediaUploadValidationService validationService,
            OwnerQuotaPreflightService ownerQuotaPreflightService,
            ModelCallAuditService modelCallAuditService
    ) {
        this(properties, validationService, ownerQuotaPreflightService, modelCallAuditService, new TranslationGlossaryParser(), new InMemoryDemoRunProfileService());
    }

    @Autowired
    public UploadCostEstimateServiceImpl(
            LinguaFrameProperties properties,
            MediaUploadValidationService validationService,
            OwnerQuotaPreflightService ownerQuotaPreflightService,
            ModelCallAuditService modelCallAuditService,
            TranslationGlossaryParser translationGlossaryParser,
            DemoRunProfileService demoRunProfileService
    ) {
        this.properties = properties;
        this.validationService = validationService;
        this.ownerQuotaPreflightService = ownerQuotaPreflightService;
        this.modelCallAuditService = modelCallAuditService;
        this.translationGlossaryParser = translationGlossaryParser;
        this.demoRunProfileService = demoRunProfileService;
    }

    @Override
    public UploadCostEstimateVo estimate(MultipartFile file, UploadCostEstimateOptionsBo options) {
        UploadCostEstimateOptionsBo safeOptions = options == null ? UploadCostEstimateOptionsBo.empty() : options;
        NormalizedOptions normalized = normalize(safeOptions);
        MediaUploadValidationVo validation = validationService.validate(file);
        if (!validation.valid()) {
            return blockedValidation(validation, normalized);
        }

        int durationSeconds = validation.durationSeconds() == null ? 0 : validation.durationSeconds();
        UsageProxy usage = usageProxy(durationSeconds, normalized.glossary().entryCount());
        List<UploadCostEstimateStageVo> stages = stages(normalized, usage);
        BigDecimal pointEstimate = stages.stream()
                .map(UploadCostEstimateStageVo::estimatedCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        pointEstimate = money(pointEstimate);
        BigDecimal lower = money(pointEstimate.multiply(RANGE_LOW));
        BigDecimal upper = money(pointEstimate.multiply(RANGE_HIGH));
        List<UploadCostEstimateBudgetVo> budgets = budgets(pointEstimate);
        List<String> cacheNotes = List.of(
                "Estimate does not assume cache hits; repeated media, glossary, or subtitle settings may lower actual provider spend.",
                "FFmpeg-only stages are included for visibility but have no OpenAI cost."
        );
        List<String> safetyNotes = safetyNotes(validation, normalized);
        String status = overallStatus(budgets);
        String recommendedNextAction = switch (status) {
            case STATUS_BLOCKED -> "Reduce estimated cost, change the file, or adjust configured budget limits before upload.";
            case STATUS_ATTENTION -> "Review budget warnings, then upload only if the demo spend is acceptable.";
            default -> "Upload can proceed with the selected profile and options.";
        };

        return new UploadCostEstimateVo(
                status,
                recommendedNextAction,
                validation.filename(),
                validation.contentType(),
                validation.fileSizeBytes(),
                validation.maxFileSizeBytes(),
                validation.durationSeconds(),
                validation.maxDurationSeconds(),
                true,
                validation.code().name(),
                validation.message(),
                normalized.targetLanguage(),
                normalized.ttsVoice(),
                normalized.translationStyle().name(),
                normalized.subtitleStylePreset().name(),
                normalized.glossary().entryCount(),
                normalized.glossary().hash(),
                normalized.subtitlePolishingMode().name(),
                normalized.demoProfileId(),
                lower,
                pointEstimate,
                upper,
                stages,
                budgets,
                cacheNotes,
                safetyNotes
        );
    }

    private UploadCostEstimateVo blockedValidation(MediaUploadValidationVo validation, NormalizedOptions normalized) {
        return new UploadCostEstimateVo(
                STATUS_BLOCKED,
                "Replace the source video or choose media inside the configured upload limits.",
                validation.filename(),
                validation.contentType(),
                validation.fileSizeBytes(),
                validation.maxFileSizeBytes(),
                validation.durationSeconds(),
                validation.maxDurationSeconds(),
                false,
                validation.code().name(),
                validation.message(),
                normalized.targetLanguage(),
                normalized.ttsVoice(),
                normalized.translationStyle().name(),
                normalized.subtitleStylePreset().name(),
                normalized.glossary().entryCount(),
                normalized.glossary().hash(),
                normalized.subtitlePolishingMode().name(),
                normalized.demoProfileId(),
                BigDecimal.ZERO.setScale(8),
                BigDecimal.ZERO.setScale(8),
                BigDecimal.ZERO.setScale(8),
                List.of(),
                budgets(BigDecimal.ZERO),
                List.of("No provider stages are estimated until the file passes upload validation."),
                List.of(validation.message())
        );
    }

    private NormalizedOptions normalize(UploadCostEstimateOptionsBo options) {
        String normalizedDemoProfileId = normalizeDemoProfileId(options.demoProfileId());
        String targetLanguage = normalizeTargetLanguage(options.targetLanguage());
        String ttsVoice = normalizeTtsVoice(options.ttsVoice());
        TranslationStyle translationStyle = TranslationStyle.parse(options.translationStyle());
        SubtitleStylePreset subtitleStylePreset = SubtitleStylePreset.parse(options.subtitleStylePreset());
        SubtitlePolishingMode subtitlePolishingMode = SubtitlePolishingMode.parse(options.subtitlePolishingMode());
        TranslationGlossaryBo glossary = translationGlossaryParser.parse(options.translationGlossary());
        return new NormalizedOptions(
                targetLanguage,
                ttsVoice,
                translationStyle,
                subtitleStylePreset,
                glossary,
                subtitlePolishingMode,
                normalizedDemoProfileId
        );
    }

    private List<UploadCostEstimateStageVo> stages(NormalizedOptions options, UsageProxy usage) {
        List<UploadCostEstimateStageVo> stages = new ArrayList<>();
        stages.add(stage(
                "audioExtraction",
                "Audio extraction",
                "LOCAL",
                "ffmpeg",
                properties.getFfmpeg().getBinaryPath(),
                false,
                BigDecimal.ZERO,
                usage.durationSeconds() + " seconds",
                "Local media preparation before transcription."
        ));
        stages.add(stage(
                "transcription",
                "Transcription",
                providerStatus(properties.getTranscription().isEnabled()),
                properties.getTranscription().getProvider(),
                properties.getTranscription().getOpenai().getModel(),
                properties.getTranscription().isEnabled(),
                properties.getCost().getTranscriptionUsdPerMinute()
                        .multiply(new BigDecimal(usage.durationSeconds()))
                        .divide(new BigDecimal("60"), 8, RoundingMode.HALF_UP),
                usage.durationSeconds() + " audio seconds",
                "Estimated from rounded upload duration."
        ));
        stages.add(stage(
                "translation",
                "Translation",
                providerStatus(properties.getTranslation().isEnabled()),
                properties.getTranslation().getProvider(),
                properties.getTranslation().getOpenai().getModel(),
                properties.getTranslation().isEnabled(),
                tokenCost(usage.translationInputTokens(), usage.translationOutputTokens()),
                usage.translationInputTokens() + " input tokens, " + usage.translationOutputTokens() + " output tokens",
                "Includes style prompt and " + options.glossary().entryCount() + " glossary entries."
        ));
        boolean polishingEnabled = options.subtitlePolishingMode() != SubtitlePolishingMode.OFF;
        stages.add(stage(
                "subtitlePolishing",
                "Subtitle polishing",
                polishingEnabled ? providerStatus(properties.getTranslation().isEnabled()) : "DISABLED",
                properties.getTranslation().getProvider(),
                properties.getTranslation().getOpenai().getModel(),
                polishingEnabled && properties.getTranslation().isEnabled(),
                polishingEnabled ? tokenCost(usage.polishingInputTokens(), usage.polishingOutputTokens()) : BigDecimal.ZERO,
                options.subtitlePolishingMode().name(),
                polishingEnabled ? "Estimated from translated subtitle volume." : "Skipped because polishing mode is OFF."
        ));
        stages.add(stage(
                "qualityEvaluation",
                "Quality evaluation",
                providerStatus(properties.getEvaluation().isEnabled()),
                properties.getEvaluation().getProvider(),
                properties.getEvaluation().getOpenai().getModel(),
                properties.getEvaluation().isEnabled(),
                properties.getEvaluation().isEnabled()
                        ? tokenCost(usage.evaluationInputTokens(), usage.evaluationOutputTokens())
                        : BigDecimal.ZERO,
                usage.evaluationInputTokens() + " input tokens, " + usage.evaluationOutputTokens() + " output tokens",
                "Estimated from transcript and target subtitle text."
        ));
        stages.add(stage(
                "tts",
                "Text to speech",
                providerStatus(properties.getTts().isEnabled()),
                properties.getTts().getProvider(),
                properties.getTts().getOpenai().getModel(),
                properties.getTts().isEnabled(),
                properties.getTts().isEnabled()
                        ? properties.getCost().getTtsUsdPerMillionCharacters()
                        .multiply(new BigDecimal(usage.ttsCharacters()))
                        .divide(MILLION, 8, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO,
                usage.ttsCharacters() + " characters",
                "Uses voice " + displayVoice(options.ttsVoice()) + "."
        ));
        stages.add(stage(
                "subtitleBurnIn",
                "Subtitle burn-in",
                "LOCAL",
                "ffmpeg",
                properties.getFfmpeg().getBinaryPath(),
                false,
                BigDecimal.ZERO,
                options.subtitleStylePreset().name(),
                "Local rendering step with no OpenAI provider spend."
        ));
        return stages;
    }

    private UploadCostEstimateStageVo stage(
            String id,
            String label,
            String status,
            String provider,
            String model,
            boolean paidProviderCall,
            BigDecimal estimatedCostUsd,
            String basis,
            String detail
    ) {
        return new UploadCostEstimateStageVo(
                id,
                label,
                status,
                display(provider),
                display(model),
                paidProviderCall,
                money(estimatedCostUsd),
                basis,
                detail
        );
    }

    private List<UploadCostEstimateBudgetVo> budgets(BigDecimal pointEstimate) {
        List<UploadCostEstimateBudgetVo> budgets = new ArrayList<>();
        budgets.add(jobBudget(pointEstimate));
        budgets.add(dailyBudget(pointEstimate));
        budgets.add(ownerDailyBudget(pointEstimate));
        return budgets;
    }

    private UploadCostEstimateBudgetVo jobBudget(BigDecimal pointEstimate) {
        boolean enabled = properties.getCost().isBudgetGuardEnabled()
                && properties.getCost().getMaxJobCostUsd().compareTo(BigDecimal.ZERO) > 0;
        BigDecimal limit = enabled ? properties.getCost().getMaxJobCostUsd() : BigDecimal.ZERO;
        String status = budgetStatus(enabled, pointEstimate, limit);
        return new UploadCostEstimateBudgetVo(
                "jobCost",
                "Per-job cost guard",
                enabled,
                status,
                BigDecimal.ZERO.setScale(8),
                money(pointEstimate),
                money(pointEstimate),
                money(limit),
                enabled ? "Projected job cost compared with linguaframe.cost.max-job-cost-usd." : "Per-job cost guard is disabled."
        );
    }

    private UploadCostEstimateBudgetVo dailyBudget(BigDecimal pointEstimate) {
        boolean enabled = properties.getCost().isDailyBudgetGuardEnabled()
                && properties.getCost().getMaxDailyCostUsd().compareTo(BigDecimal.ZERO) > 0;
        BigDecimal current = BigDecimal.ZERO;
        if (enabled) {
            Instant since = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();
            current = modelCallAuditService.summarizeDailyBudget(properties.getCost().getBudgetIdentity(), since);
        }
        BigDecimal projected = current.add(pointEstimate);
        BigDecimal limit = enabled ? properties.getCost().getMaxDailyCostUsd() : BigDecimal.ZERO;
        return new UploadCostEstimateBudgetVo(
                "dailyCost",
                "Daily cost guard",
                enabled,
                budgetStatus(enabled, projected, limit),
                money(current),
                money(pointEstimate),
                money(projected),
                money(limit),
                enabled ? "Projected daily spend for budget identity " + properties.getCost().getBudgetIdentity() + "." : "Daily cost guard is disabled."
        );
    }

    private UploadCostEstimateBudgetVo ownerDailyBudget(BigDecimal pointEstimate) {
        OwnerQuotaPreflightVo preflight = ownerQuotaPreflightService.getPreflight();
        OwnerQuotaLimitVo dailyLimit = preflight.limits().stream()
                .filter(limit -> "dailyCostUsd".equals(limit.name()))
                .findFirst()
                .orElse(new OwnerQuotaLimitVo("dailyCostUsd", false, BigDecimal.ZERO, preflight.dailyEstimatedCostUsd()));
        BigDecimal current = dailyLimit.current() == null ? preflight.dailyEstimatedCostUsd() : dailyLimit.current();
        BigDecimal limit = dailyLimit.limit() == null ? BigDecimal.ZERO : dailyLimit.limit();
        BigDecimal projected = current.add(pointEstimate);
        String status = preflight.allowed() ? budgetStatus(dailyLimit.enabled(), projected, limit) : STATUS_BLOCKED;
        return new UploadCostEstimateBudgetVo(
                "ownerDailyCost",
                "Owner daily quota",
                preflight.enabled() && dailyLimit.enabled(),
                status,
                money(current),
                money(pointEstimate),
                money(projected),
                money(limit),
                preflight.blockingReasons().isEmpty()
                        ? "Owner " + preflight.ownerId() + " projected daily cost impact."
                        : String.join(" ", preflight.blockingReasons())
        );
    }

    private String overallStatus(List<UploadCostEstimateBudgetVo> budgets) {
        if (budgets.stream().anyMatch(budget -> STATUS_BLOCKED.equals(budget.status()))) {
            return STATUS_BLOCKED;
        }
        if (budgets.stream().anyMatch(budget -> STATUS_ATTENTION.equals(budget.status()))) {
            return STATUS_ATTENTION;
        }
        return STATUS_READY;
    }

    private String budgetStatus(boolean enabled, BigDecimal projected, BigDecimal limit) {
        if (!enabled || limit.compareTo(BigDecimal.ZERO) <= 0) {
            return STATUS_READY;
        }
        if (projected.compareTo(limit) >= 0) {
            return STATUS_BLOCKED;
        }
        BigDecimal attentionThreshold = limit.multiply(new BigDecimal("0.80"));
        if (projected.compareTo(attentionThreshold) >= 0) {
            return STATUS_ATTENTION;
        }
        return STATUS_READY;
    }

    private UsageProxy usageProxy(int durationSeconds, int glossaryEntries) {
        int transcriptCharacters = Math.max(120, durationSeconds * 14 + glossaryEntries * 18);
        int inputTokens = tokens(transcriptCharacters + 500);
        int outputTokens = tokens(transcriptCharacters);
        int polishingInputTokens = tokens(transcriptCharacters * 2);
        int polishingOutputTokens = tokens(transcriptCharacters);
        int evaluationInputTokens = tokens(transcriptCharacters * 3);
        int evaluationOutputTokens = 300;
        return new UsageProxy(
                durationSeconds,
                inputTokens,
                outputTokens,
                polishingInputTokens,
                polishingOutputTokens,
                evaluationInputTokens,
                evaluationOutputTokens,
                transcriptCharacters
        );
    }

    private int tokens(int characters) {
        return Math.max(1, new BigDecimal(characters).divide(TOKEN_DIVISOR, 0, RoundingMode.CEILING).intValue());
    }

    private BigDecimal tokenCost(int inputTokens, int outputTokens) {
        BigDecimal inputCost = properties.getCost().getTranslationInputUsdPerMillionTokens()
                .multiply(new BigDecimal(inputTokens))
                .divide(MILLION, 8, RoundingMode.HALF_UP);
        BigDecimal outputCost = properties.getCost().getTranslationOutputUsdPerMillionTokens()
                .multiply(new BigDecimal(outputTokens))
                .divide(MILLION, 8, RoundingMode.HALF_UP);
        return inputCost.add(outputCost);
    }

    private List<String> safetyNotes(MediaUploadValidationVo validation, NormalizedOptions options) {
        List<String> notes = new ArrayList<>();
        notes.add("Only safe media metadata is returned; source file paths and secrets are not exposed.");
        if (validation.durationSeconds() != null && validation.durationSeconds() >= validation.maxDurationSeconds() * 0.8) {
            notes.add("Video duration is close to the configured upload limit.");
        }
        if (options.subtitlePolishingMode() != SubtitlePolishingMode.OFF) {
            notes.add("Subtitle polishing may add a second text-model pass.");
        }
        return notes;
    }

    private String normalizeTargetLanguage(String targetLanguage) {
        return StringUtils.hasText(targetLanguage) ? targetLanguage.trim() : DEFAULT_TARGET_LANGUAGE;
    }

    private String normalizeTtsVoice(String ttsVoice) {
        if (StringUtils.hasText(ttsVoice)) {
            return ttsVoice.trim();
        }
        String configuredVoice = properties.getTts().getOpenai().getVoice();
        return StringUtils.hasText(configuredVoice) ? configuredVoice.trim() : null;
    }

    private String normalizeDemoProfileId(String demoProfileId) {
        if (!StringUtils.hasText(demoProfileId)) {
            return null;
        }
        return demoRunProfileService.normalizeProfileId(demoProfileId.trim().toLowerCase(Locale.ROOT));
    }

    private String providerStatus(boolean enabled) {
        return enabled ? "ESTIMATED" : "DISABLED";
    }

    private String display(String value) {
        return StringUtils.hasText(value) ? value.trim() : "not-configured";
    }

    private String displayVoice(String value) {
        return StringUtils.hasText(value) ? value : "default";
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(8) : value.setScale(8, RoundingMode.HALF_UP);
    }

    private record NormalizedOptions(
            String targetLanguage,
            String ttsVoice,
            TranslationStyle translationStyle,
            SubtitleStylePreset subtitleStylePreset,
            TranslationGlossaryBo glossary,
            SubtitlePolishingMode subtitlePolishingMode,
            String demoProfileId
    ) {
    }

    private record UsageProxy(
            int durationSeconds,
            int translationInputTokens,
            int translationOutputTokens,
            int polishingInputTokens,
            int polishingOutputTokens,
            int evaluationInputTokens,
            int evaluationOutputTokens,
            int ttsCharacters
    ) {
    }
}
