package com.linguaframe.job.service.impl;

import com.linguaframe.demo.domain.vo.NarrationDemoPresetSegmentVo;
import com.linguaframe.demo.domain.vo.NarrationDemoPresetVo;
import com.linguaframe.demo.service.NarrationDemoPresetService;
import com.linguaframe.job.domain.dto.ApplyNarrationDemoPresetDto;
import com.linguaframe.job.domain.dto.ImportNarrationScriptPackageDto;
import com.linguaframe.job.domain.dto.ImportNarrationScriptPackageSegmentDto;
import com.linguaframe.job.domain.dto.UpdateNarrationMixSettingsDto;
import com.linguaframe.job.domain.vo.NarrationDemoPresetApplyVo;
import com.linguaframe.job.domain.vo.NarrationEvidenceVo;
import com.linguaframe.job.domain.vo.NarrationScriptPackageImportVo;
import com.linguaframe.job.domain.vo.NarrationScriptPackageVo;
import com.linguaframe.job.domain.vo.NarrationWorkspaceVo;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.job.service.NarrationDemoPresetApplyService;
import com.linguaframe.job.service.NarrationEvidenceService;
import com.linguaframe.job.service.NarrationScriptPackageService;
import com.linguaframe.job.service.NarrationWorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class NarrationDemoPresetApplyServiceImpl implements NarrationDemoPresetApplyService {

    private final NarrationDemoPresetService presetService;
    private final NarrationScriptPackageService scriptPackageService;
    private final NarrationWorkspaceService workspaceService;
    private final NarrationEvidenceService narrationEvidenceService;
    private final LocalizationJobQueryService queryService;

    public NarrationDemoPresetApplyServiceImpl(
            NarrationDemoPresetService presetService,
            NarrationScriptPackageService scriptPackageService,
            NarrationWorkspaceService workspaceService,
            NarrationEvidenceService narrationEvidenceService,
            LocalizationJobQueryService queryService
    ) {
        this.presetService = presetService;
        this.scriptPackageService = scriptPackageService;
        this.workspaceService = workspaceService;
        this.narrationEvidenceService = narrationEvidenceService;
        this.queryService = queryService;
    }

    @Override
    public NarrationDemoPresetApplyVo apply(String jobId, ApplyNarrationDemoPresetDto request) {
        if (request == null || !Boolean.TRUE.equals(request.replaceExisting())) {
            throw new IllegalArgumentException("Narration demo preset apply requires replaceExisting=true.");
        }
        if (!StringUtils.hasText(request.presetId())) {
            throw new IllegalArgumentException("Narration demo preset id is required.");
        }
        queryService.getJob(jobId);
        NarrationDemoPresetVo preset = presetService.findById(request.presetId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown narration demo preset: " + request.presetId()));

        NarrationScriptPackageImportVo imported = scriptPackageService.importPackage(jobId, toImportRequest(preset));
        NarrationWorkspaceVo workspace = workspaceService.getWorkspace(jobId);
        NarrationScriptPackageVo scriptPackage = scriptPackageService.getPackage(jobId);
        NarrationEvidenceVo evidence = narrationEvidenceService.getEvidence(jobId);

        return new NarrationDemoPresetApplyVo(
                jobId,
                preset.id(),
                preset.profileId(),
                imported.importedSegmentCount(),
                imported.totalCharacterCount(),
                imported.voiceSummary(),
                imported.replacedExisting(),
                false,
                workspace,
                scriptPackage,
                evidence.status()
        );
    }

    private ImportNarrationScriptPackageDto toImportRequest(NarrationDemoPresetVo preset) {
        return new ImportNarrationScriptPackageDto(
                true,
                toImportSegments(preset.segments()),
                new UpdateNarrationMixSettingsDto(
                        preset.mixSettings().duckingVolume(),
                        preset.mixSettings().narrationVolume(),
                        preset.mixSettings().fadeDurationMs()
                )
        );
    }

    private List<ImportNarrationScriptPackageSegmentDto> toImportSegments(List<NarrationDemoPresetSegmentVo> segments) {
        return segments.stream()
                .map(segment -> new ImportNarrationScriptPackageSegmentDto(
                        segment.index(),
                        segment.startSeconds(),
                        segment.endSeconds(),
                        segment.text(),
                        segment.voice()
                ))
                .toList();
    }
}
