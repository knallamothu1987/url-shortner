package com.shortener.orchestration.domain;

public enum PipelineStatus {
    CREATED,
    RUNNING,
    PAUSED_FOR_APPROVAL,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
