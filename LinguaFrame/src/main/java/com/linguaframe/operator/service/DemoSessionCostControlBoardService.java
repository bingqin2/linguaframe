package com.linguaframe.operator.service;

import com.linguaframe.operator.domain.vo.DemoSessionCostControlBoardVo;

public interface DemoSessionCostControlBoardService {

    DemoSessionCostControlBoardVo board(Integer limit);

    String boardMarkdown(Integer limit);
}
