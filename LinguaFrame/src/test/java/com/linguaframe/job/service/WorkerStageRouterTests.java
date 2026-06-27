package com.linguaframe.job.service;

import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.WorkerRole;
import com.linguaframe.job.service.impl.WorkerStageRouterImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerStageRouterTests {

    private final WorkerStageRouter router = new WorkerStageRouterImpl();

    @Test
    void combinedWorkerExecutesEveryStageFromStart() {
        var plan = router.plan(WorkerRole.COMBINED, LocalizationJobStage.WORKER_SMOKE, stages(
                LocalizationJobStage.WORKER_SMOKE,
                LocalizationJobStage.AUDIO_EXTRACTION,
                LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT,
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                LocalizationJobStage.TRANSLATION_QUALITY_EVALUATION,
                LocalizationJobStage.DUBBING_AUDIO_GENERATION,
                LocalizationJobStage.SUBTITLE_BURN_IN,
                LocalizationJobStage.ARTIFACT_SUMMARY
        ));

        assertThat(plan.executableStages()).extracting(LocalizationPipelineStage::stage).containsExactly(
                LocalizationJobStage.WORKER_SMOKE,
                LocalizationJobStage.AUDIO_EXTRACTION,
                LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT,
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                LocalizationJobStage.TRANSLATION_QUALITY_EVALUATION,
                LocalizationJobStage.DUBBING_AUDIO_GENERATION,
                LocalizationJobStage.SUBTITLE_BURN_IN,
                LocalizationJobStage.ARTIFACT_SUMMARY
        );
        assertThat(plan.nextStage()).isNull();
        assertThat(plan.nextRole()).isNull();
        assertThat(plan.finalSegment()).isTrue();
    }

    @Test
    void ffmpegWorkerExecutesInitialMediaStagesThenHandsOffToOpenAi() {
        var plan = router.plan(WorkerRole.FFMPEG, LocalizationJobStage.WORKER_SMOKE, stages(
                LocalizationJobStage.WORKER_SMOKE,
                LocalizationJobStage.AUDIO_EXTRACTION,
                LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT,
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT
        ));

        assertThat(plan.executableStages()).extracting(LocalizationPipelineStage::stage).containsExactly(
                LocalizationJobStage.WORKER_SMOKE,
                LocalizationJobStage.AUDIO_EXTRACTION
        );
        assertThat(plan.nextStage()).isEqualTo(LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT);
        assertThat(plan.nextRole()).isEqualTo(WorkerRole.OPENAI);
        assertThat(plan.finalSegment()).isFalse();
    }

    @Test
    void openAiWorkerExecutesModelStagesThenHandsOffToFfmpeg() {
        var plan = router.plan(WorkerRole.OPENAI, LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT, stages(
                LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT,
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                LocalizationJobStage.TRANSLATION_QUALITY_EVALUATION,
                LocalizationJobStage.DUBBING_AUDIO_GENERATION,
                LocalizationJobStage.SUBTITLE_BURN_IN
        ));

        assertThat(plan.executableStages()).extracting(LocalizationPipelineStage::stage).containsExactly(
                LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT,
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                LocalizationJobStage.TRANSLATION_QUALITY_EVALUATION,
                LocalizationJobStage.DUBBING_AUDIO_GENERATION
        );
        assertThat(plan.nextStage()).isEqualTo(LocalizationJobStage.SUBTITLE_BURN_IN);
        assertThat(plan.nextRole()).isEqualTo(WorkerRole.FFMPEG);
        assertThat(plan.finalSegment()).isFalse();
    }

    @Test
    void ffmpegWorkerExecutesFinalMediaStages() {
        var plan = router.plan(WorkerRole.FFMPEG, LocalizationJobStage.SUBTITLE_BURN_IN, stages(
                LocalizationJobStage.SUBTITLE_BURN_IN,
                LocalizationJobStage.ARTIFACT_SUMMARY
        ));

        assertThat(plan.executableStages()).extracting(LocalizationPipelineStage::stage).containsExactly(
                LocalizationJobStage.SUBTITLE_BURN_IN,
                LocalizationJobStage.ARTIFACT_SUMMARY
        );
        assertThat(plan.nextStage()).isNull();
        assertThat(plan.nextRole()).isNull();
        assertThat(plan.finalSegment()).isTrue();
    }

    private static List<LocalizationPipelineStage> stages(LocalizationJobStage... stages) {
        return List.of(stages).stream()
                .map(WorkerStageRouterTests::stage)
                .toList();
    }

    private static LocalizationPipelineStage stage(LocalizationJobStage stage) {
        return new LocalizationPipelineStage() {
            @Override
            public LocalizationJobStage stage() {
                return stage;
            }

            @Override
            public void execute(com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo context) {
            }
        };
    }
}
