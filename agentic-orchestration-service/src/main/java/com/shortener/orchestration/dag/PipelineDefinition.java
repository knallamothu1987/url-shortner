package com.shortener.orchestration.dag;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The explicit SDLC dependency graph. Validated to be acyclic with resolvable dependencies.
 */
public record PipelineDefinition(String name, List<StageDefinition> stages) {

    public PipelineDefinition {
        Map<String, StageDefinition> byName = stages.stream()
                .collect(Collectors.toMap(StageDefinition::name, Function.identity(), (a, b) -> {
                    throw new IllegalArgumentException("Duplicate stage name: " + a.name());
                }));
        for (StageDefinition stage : stages) {
            for (String dep : stage.dependsOn()) {
                if (!byName.containsKey(dep)) {
                    throw new IllegalArgumentException(
                            "Stage '%s' depends on unknown stage '%s'".formatted(stage.name(), dep));
                }
            }
        }
        assertAcyclic(byName);
    }

    public StageDefinition stage(String name) {
        return stages.stream()
                .filter(stage -> stage.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown stage: " + name));
    }

    /** All stages that (transitively) depend on the given stage — used for re-planning. */
    public Set<String> transitiveDependents(String stageName) {
        Set<String> dependents = new LinkedHashSet<>();
        Deque<String> frontier = new ArrayDeque<>();
        frontier.add(stageName);
        while (!frontier.isEmpty()) {
            String current = frontier.poll();
            for (StageDefinition stage : stages) {
                if (stage.dependsOn().contains(current) && dependents.add(stage.name())) {
                    frontier.add(stage.name());
                }
            }
        }
        return dependents;
    }

    private static void assertAcyclic(Map<String, StageDefinition> byName) {
        Set<String> visited = new HashSet<>();
        Set<String> inProgress = new HashSet<>();
        for (String stage : byName.keySet()) {
            visit(stage, byName, visited, inProgress);
        }
    }

    private static void visit(String stage, Map<String, StageDefinition> byName,
                              Set<String> visited, Set<String> inProgress) {
        if (visited.contains(stage)) {
            return;
        }
        if (!inProgress.add(stage)) {
            throw new IllegalArgumentException("Dependency cycle involving stage: " + stage);
        }
        for (String dep : byName.get(stage).dependsOn()) {
            visit(dep, byName, visited, inProgress);
        }
        inProgress.remove(stage);
        visited.add(stage);
    }
}
