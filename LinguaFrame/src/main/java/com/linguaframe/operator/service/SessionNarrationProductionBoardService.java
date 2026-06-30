package com.linguaframe.operator.service;

import com.linguaframe.operator.domain.vo.SessionNarrationProductionBoardVo;

public interface SessionNarrationProductionBoardService {

    SessionNarrationProductionBoardVo board(Integer limit);

    String boardMarkdown(Integer limit);
}
