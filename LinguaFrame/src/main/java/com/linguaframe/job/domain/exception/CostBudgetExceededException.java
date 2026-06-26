package com.linguaframe.job.domain.exception;

public class CostBudgetExceededException extends RuntimeException {

    public CostBudgetExceededException(String message) {
        super(message);
    }
}
