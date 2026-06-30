package com.linguaframe.operator.service;

import com.linguaframe.operator.domain.vo.DemoSessionRecoveryBoardVo;

public interface DemoSessionRecoveryBoardService {

    DemoSessionRecoveryBoardVo board(Integer limit);

    String boardMarkdown(Integer limit);
}
