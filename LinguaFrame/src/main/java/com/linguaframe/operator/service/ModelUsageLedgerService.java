package com.linguaframe.operator.service;

import com.linguaframe.operator.domain.vo.ModelUsageLedgerVo;

public interface ModelUsageLedgerService {

    ModelUsageLedgerVo ledger(Integer limit);

    String ledgerMarkdown(Integer limit);
}
