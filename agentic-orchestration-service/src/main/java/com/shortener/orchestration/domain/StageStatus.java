package com.shortener.orchestration.domain;

public enum StageStatus {
    PENDING,
    RUNNING,
    AWAITING_APPROVAL,
    COMPLETED,
    FAILED,
    ROLLED_BACK
}
