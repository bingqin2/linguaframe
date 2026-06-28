package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.WorkerRole;
import com.linguaframe.job.domain.vo.WorkerStagePlanVo;
import com.linguaframe.job.service.LocalizationPipelineStage;
import com.linguaframe.job.service.WorkerStageRouter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class WorkerStageRouterImpl implements WorkerStageRouter {

    private static final Map<WorkerRole, Set<LocalizationJobStage>> OWNED_STAGES = new EnumMap<>(WorkerRole.class);

    static {
        OWNED_STAGES.put(WorkerRole.COMBINED, EnumSet.allOf(LocalizationJobStage.class));
        OWNED_STAGES.put(WorkerRole.FFMPEG, EnumSet.of(
                LocalizationJobStage.WORKER_SMOKE,
                LocalizationJobStage.AUDIO_EXTRACTION,
                LocalizationJobStage.SUBTITLE_BURN_IN,
                LocalizationJobStage.DUBBED_VIDEO_DELIVERY,
                LocalizationJobStage.ARTIFACT_SUMMARY
        ));
        OWNED_STAGES.put(WorkerRole.OPENAI, EnumSet.of(
                LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT,
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                LocalizationJobStage.SUBTITLE_POLISHING,
                LocalizationJobStage.TRANSLATION_QUALITY_EVALUATION,
                LocalizationJobStage.DUBBING_AUDIO_GENERATION
        ));
    }

    @Override
    public WorkerStagePlanVo plan(
            WorkerRole role,
            LocalizationJobStage startStage,
            List<LocalizationPipelineStage> orderedStages
    ) {
        Set<LocalizationJobStage> ownedStages = OWNED_STAGES.get(role);
        if (ownedStages == null || !ownedStages.contains(startStage)) {
            throw new IllegalStateException("Worker role " + role + " cannot execute start stage " + startStage + ".");
        }

        List<LocalizationPipelineStage> sortedStages = orderedStages.stream()
                .sorted(Comparator.comparing(stage -> stage.stage().ordinal()))
                .toList();
        if (role == WorkerRole.COMBINED && sortedStages.stream().noneMatch(stage -> stage.stage() == startStage)) {
            return new WorkerStagePlanVo(sortedStages, null, null, true);
        }
        List<LocalizationPipelineStage> executableStages = new ArrayList<>();
        boolean inSegment = false;

        for (LocalizationPipelineStage pipelineStage : sortedStages) {
            LocalizationJobStage stage = pipelineStage.stage();
            if (!inSegment && stage != startStage) {
                continue;
            }
            inSegment = true;
            if (!ownedStages.contains(stage)) {
                return new WorkerStagePlanVo(
                        List.copyOf(executableStages),
                        stage,
                        roleFor(stage),
                        false
                );
            }
            executableStages.add(pipelineStage);
        }

        return new WorkerStagePlanVo(List.copyOf(executableStages), null, null, true);
    }

    private static WorkerRole roleFor(LocalizationJobStage stage) {
        if (OWNED_STAGES.get(WorkerRole.FFMPEG).contains(stage)) {
            return WorkerRole.FFMPEG;
        }
        if (OWNED_STAGES.get(WorkerRole.OPENAI).contains(stage)) {
            return WorkerRole.OPENAI;
        }
        return WorkerRole.COMBINED;
    }
}
