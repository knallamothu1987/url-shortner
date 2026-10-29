package com.shortener.orchestration.dag;

import java.util.List;

public record StageDefinition(
        String name,
        String agent,
        List<String> dependsOn,
        List<GateBinding> exitGates) {
}
