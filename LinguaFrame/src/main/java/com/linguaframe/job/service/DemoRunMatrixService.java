package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.DemoRunMatrixVo;

public interface DemoRunMatrixService {

    DemoRunMatrixVo buildMatrix(String anchorJobId, Integer limit);
}
