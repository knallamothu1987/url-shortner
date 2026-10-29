package com.shortener.orchestration.api;

import java.util.UUID;

public class PipelineNotFoundException extends RuntimeException {

    public PipelineNotFoundException(UUID runId) {
        super("Pipeline run not found: " + runId);
    }
}
