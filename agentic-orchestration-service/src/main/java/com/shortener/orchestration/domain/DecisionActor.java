package com.shortener.orchestration.domain;

/** Who made a recorded decision — used for audit lineage. */
public enum DecisionActor {
    AGENT,
    ORCHESTRATOR,
    GATE,
    HUMAN
}
