package com.linguaframe.media.service;

import com.linguaframe.media.domain.vo.UploadSourceReuseDecisionVo;
import com.linguaframe.media.domain.vo.UploadSourceReuseVo;

public interface UploadSourceReuseDecisionService {

    UploadSourceReuseDecisionVo decide(UploadSourceReuseVo sourceReuse);
}
