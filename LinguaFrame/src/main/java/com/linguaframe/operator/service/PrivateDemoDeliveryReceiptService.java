package com.linguaframe.operator.service;

import com.linguaframe.operator.domain.bo.DemoSessionEvidencePackageBo;
import com.linguaframe.operator.domain.vo.PrivateDemoDeliveryReceiptVo;

public interface PrivateDemoDeliveryReceiptService {

    PrivateDemoDeliveryReceiptVo receipt(String jobId);

    String receiptMarkdown(String jobId);

    DemoSessionEvidencePackageBo openPackage(String jobId);
}
