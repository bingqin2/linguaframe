package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.TtsRequestBo;
import com.linguaframe.job.domain.bo.TtsResultBo;

public interface TtsProvider {

    TtsResultBo synthesize(TtsRequestBo request);
}
