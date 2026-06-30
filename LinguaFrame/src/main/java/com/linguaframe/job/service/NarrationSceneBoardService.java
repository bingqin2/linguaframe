package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.NarrationSceneBoardVo;

public interface NarrationSceneBoardService {

    NarrationSceneBoardVo getSceneBoard(String jobId);

    String renderMarkdown(String jobId);
}
