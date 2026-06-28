package com.linguaframe.common.quota;

import java.util.List;

public class OwnerQuotaExceededException extends RuntimeException {

    private final OwnerQuotaPreflightVo preflight;

    public OwnerQuotaExceededException(OwnerQuotaPreflightVo preflight) {
        super(String.join(" ", preflight.blockingReasons()));
        this.preflight = preflight;
    }

    public OwnerQuotaPreflightVo preflight() {
        return preflight;
    }

    public List<String> blockingReasons() {
        return preflight.blockingReasons();
    }
}
