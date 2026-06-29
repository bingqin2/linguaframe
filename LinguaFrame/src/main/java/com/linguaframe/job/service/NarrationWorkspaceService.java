package com.linguaframe.job.service;

import com.linguaframe.job.domain.dto.SaveNarrationSegmentsRequest;
import com.linguaframe.job.domain.dto.UpdateNarrationMixSettingsDto;
import com.linguaframe.job.domain.vo.NarrationWorkspaceVo;

public interface NarrationWorkspaceService {

    NarrationWorkspaceVo getWorkspace(String jobId);

    NarrationWorkspaceVo saveWorkspace(String jobId, SaveNarrationSegmentsRequest request);

    NarrationWorkspaceVo updateMixSettings(String jobId, UpdateNarrationMixSettingsDto request);

    NarrationWorkspaceVo clearWorkspace(String jobId);
}
