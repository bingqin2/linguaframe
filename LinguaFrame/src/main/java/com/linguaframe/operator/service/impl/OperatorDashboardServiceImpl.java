package com.linguaframe.operator.service.impl;

import com.linguaframe.operator.domain.vo.OperatorDashboardVo;
import com.linguaframe.operator.repository.OperatorDashboardRepository;
import com.linguaframe.operator.service.OperatorDashboardService;
import org.springframework.stereotype.Service;

@Service
public class OperatorDashboardServiceImpl implements OperatorDashboardService {

    private final OperatorDashboardRepository dashboardRepository;

    public OperatorDashboardServiceImpl(OperatorDashboardRepository dashboardRepository) {
        this.dashboardRepository = dashboardRepository;
    }

    @Override
    public OperatorDashboardVo dashboard() {
        return dashboardRepository.fetchDashboard();
    }
}
