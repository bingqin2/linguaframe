package com.linguaframe.operator.service.impl;

import com.linguaframe.common.security.DemoOwnerIdentityService;
import com.linguaframe.operator.domain.vo.OperatorDashboardVo;
import com.linguaframe.operator.repository.OperatorDashboardRepository;
import com.linguaframe.operator.service.OperatorDashboardService;
import org.springframework.stereotype.Service;

@Service
public class OperatorDashboardServiceImpl implements OperatorDashboardService {

    private final OperatorDashboardRepository dashboardRepository;
    private final DemoOwnerIdentityService ownerIdentityService;

    public OperatorDashboardServiceImpl(
            OperatorDashboardRepository dashboardRepository,
            DemoOwnerIdentityService ownerIdentityService
    ) {
        this.dashboardRepository = dashboardRepository;
        this.ownerIdentityService = ownerIdentityService;
    }

    @Override
    public OperatorDashboardVo dashboard() {
        return dashboardRepository.fetchDashboard(
                ownerIdentityService.currentOwnerId(),
                ownerIdentityService.ownershipScope()
        );
    }
}
