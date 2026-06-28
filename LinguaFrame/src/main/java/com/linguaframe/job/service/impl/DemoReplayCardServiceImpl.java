package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.DemoPresenterPackDownloadVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackVo;
import com.linguaframe.job.domain.vo.DemoReplayCardCommandVo;
import com.linguaframe.job.domain.vo.DemoReplayCardLinkVo;
import com.linguaframe.job.domain.vo.DemoReplayCardSettingVo;
import com.linguaframe.job.domain.vo.DemoReplayCardVo;
import com.linguaframe.job.domain.vo.DemoRunMatrixJobVo;
import com.linguaframe.job.domain.vo.DemoRunMatrixVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.service.DemoPresenterPackService;
import com.linguaframe.job.service.DemoReplayCardService;
import com.linguaframe.job.service.DemoRunMatrixService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class DemoReplayCardServiceImpl implements DemoReplayCardService {

    private static final int MATRIX_LIMIT = 8;

    private final LocalizationJobQueryService queryService;
    private final DemoRunMatrixService matrixService;
    private final DemoPresenterPackService presenterPackService;
    private final Clock clock;

    public DemoReplayCardServiceImpl(
            LocalizationJobQueryService queryService,
            DemoRunMatrixService matrixService,
            DemoPresenterPackService presenterPackService,
            Clock clock
    ) {
        this.queryService = queryService;
        this.matrixService = matrixService;
        this.presenterPackService = presenterPackService;
        this.clock = clock;
    }

    @Override
    public DemoReplayCardVo buildReplayCard(String jobId) {
        LocalizationJobVo job = queryService.getJob(jobId);
        DemoRunMatrixVo matrix = matrixService.buildMatrix(jobId, MATRIX_LIMIT);
        DemoPresenterPackVo presenterPack = presenterPackService.buildPresenterPack(jobId);
        DemoRunMatrixJobVo matrixJob = matrix.jobs().stream()
                .filter(candidate -> candidate.jobId().equals(jobId))
                .findFirst()
                .orElse(null);
        JobUsageSummaryVo usage = job.usageSummary();
        String readiness = job.status() == LocalizationJobStatus.COMPLETED
                && "READY".equals(presenterPack.readinessStatus())
                ? "READY"
                : "NEEDS_ATTENTION";

        return new DemoReplayCardVo(
                job.jobId(),
                job.videoId(),
                Instant.now(clock),
                "%s replay card to %s".formatted(display(job.demoProfileId()), job.targetLanguage()),
                readiness,
                job.status(),
                job.targetLanguage(),
                job.demoProfileId(),
                job.qualityEvaluation() == null ? null : job.qualityEvaluation().score(),
                job.qualityEvaluation() == null ? null : job.qualityEvaluation().verdict(),
                usage == null ? 0 : usage.modelCallCount(),
                job.cacheSummary() == null ? 0 : job.cacheSummary().providerCacheHitCount(),
                job.cacheSummary() == null ? 0 : job.cacheSummary().cacheHitCount(),
                usage == null ? BigDecimal.ZERO : usage.estimatedCostUsd(),
                matrix.recommendedBaselineJobId(),
                matrix.bestQualityJobId(),
                matrix.lowestCostJobId(),
                settings(job, matrixJob),
                commands(job, matrix),
                links(job.jobId(), presenterPack),
                safetyNotes(job)
        );
    }

    private List<DemoReplayCardSettingVo> settings(LocalizationJobVo job, DemoRunMatrixJobVo matrixJob) {
        List<DemoReplayCardSettingVo> settings = new ArrayList<>();
        settings.add(setting("targetLanguage", "Target language", job.targetLanguage()));
        settings.add(setting("demoProfileId", "Demo profile", display(job.demoProfileId())));
        settings.add(setting("ttsVoice", "TTS voice", display(job.ttsVoice())));
        settings.add(setting("translationStyle", "Translation style", display(job.translationStyle())));
        settings.add(setting("subtitleStylePreset", "Subtitle style", display(job.subtitleStylePreset())));
        settings.add(setting("subtitlePolishingMode", "Subtitle polishing", display(job.subtitlePolishingMode())));
        settings.add(setting(
                "translationGlossary",
                "Glossary",
                "%d entries / %s".formatted(job.translationGlossaryEntryCount(), displayBlank(job.translationGlossaryHash(), "none"))
        ));
        if (matrixJob != null) {
            settings.add(setting("sameSourceHandoff", "Same-source handoff", matrixJob.handoffReady() ? "Ready" : "Attention"));
        }
        return List.copyOf(settings);
    }

    private List<DemoReplayCardCommandVo> commands(LocalizationJobVo job, DemoRunMatrixVo matrix) {
        List<DemoReplayCardCommandVo> commands = new ArrayList<>();
        commands.add(command(
                "BROWSER_REPLAY",
                "Browser replay",
                "Open the browser upload workflow and reuse the settings listed in this card.",
                "Use this when the original local media path is unavailable."
        ));
        commands.add(command(
                "LAUNCHER_CHECK",
                "Check launch defaults",
                "scripts/demo/demo-run-launcher.sh",
                "Confirms current sample/profile defaults before starting a repeat run."
        ));
        if ("tears-showcase".equals(job.demoProfileId())) {
            commands.add(command(
                    "TEARS_FULL_REPLAY",
                    "Full Tears of Steel replay",
                    "LINGUAFRAME_DEMO_PROFILE_ID=tears-showcase scripts/demo/docker-e2e-tears-of-steel-full.sh",
                    "Set LINGUAFRAME_TEARS_SAMPLE_PATH if the sample is not in the default location."
            ));
        }
        if (matrix.recommendedBaselineJobId() != null && !matrix.recommendedBaselineJobId().isBlank()) {
            commands.add(command(
                    "COMPARE_WITH_BASELINE",
                    "Compare against recommended baseline",
                    "LINGUAFRAME_COMPARISON_BASELINE_JOB_ID=%s scripts/demo/docker-e2e-tears-of-steel-full.sh"
                            .formatted(matrix.recommendedBaselineJobId()),
                    "Runs a new full-video demo and downloads comparison evidence against the baseline job."
            ));
        }
        commands.add(command(
                "EXPORT_REPLAY_CARD",
                "Export this replay card",
                "LINGUAFRAME_DEMO_JOB_ID=%s scripts/demo/demo-replay-card.sh".formatted(job.jobId()),
                "Writes the metadata-only replay card JSON under /tmp/linguaframe-demo/demo-replay-card."
        ));
        return List.copyOf(commands);
    }

    private List<DemoReplayCardLinkVo> links(String jobId, DemoPresenterPackVo presenterPack) {
        List<DemoReplayCardLinkVo> links = new ArrayList<>();
        links.add(link("REPLAY_CARD_JSON", "Replay card JSON", "/api/jobs/%s/demo-replay-card".formatted(jobId)));
        links.add(link("RUN_MATRIX_JSON", "Same-source run matrix", "/api/jobs/%s/demo-run-matrix".formatted(jobId)));
        links.add(link("PRESENTER_PACK_JSON", "Demo presenter pack", "/api/jobs/%s/demo-presenter-pack".formatted(jobId)));
        links.add(link("SNAPSHOT_ZIP", "Static snapshot ZIP", "/api/jobs/%s/demo-run-snapshot/download".formatted(jobId)));
        for (DemoPresenterPackDownloadVo download : presenterPack.downloads()) {
            links.add(link(download.kind(), download.label(), download.url()));
        }
        return List.copyOf(links);
    }

    private List<String> safetyNotes(LocalizationJobVo job) {
        List<String> notes = new ArrayList<>();
        notes.add("Metadata only: no API keys, object storage credentials, raw prompts, or media bytes are included.");
        notes.add("Local source paths are intentionally omitted; choose the source file again before replaying.");
        if (job.status() != LocalizationJobStatus.COMPLETED) {
            notes.add("The selected job is not completed, so replay guidance may be incomplete.");
        }
        return List.copyOf(notes);
    }

    private static DemoReplayCardSettingVo setting(String key, String label, String value) {
        return new DemoReplayCardSettingVo(key, label, value);
    }

    private static DemoReplayCardCommandVo command(String kind, String label, String command, String note) {
        return new DemoReplayCardCommandVo(kind, label, command, note);
    }

    private static DemoReplayCardLinkVo link(String kind, String label, String url) {
        return new DemoReplayCardLinkVo(kind, label, url);
    }

    private static String display(Object value) {
        return value == null ? "N/A" : String.valueOf(value);
    }

    private static String displayBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
