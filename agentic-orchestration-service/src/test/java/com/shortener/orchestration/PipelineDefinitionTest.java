package com.shortener.orchestration;

import com.shortener.orchestration.dag.GateBinding;
import com.shortener.orchestration.dag.PipelineDefinition;
import com.shortener.orchestration.dag.PipelineDefinitionLoader;
import com.shortener.orchestration.dag.StageDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineDefinitionTest {

    @Test
    void bundledDefinitionLoadsWithSevenStages() {
        PipelineDefinition definition = PipelineDefinitionLoader.load("pipeline-definition.yml");
        assertEquals("sdlc-pipeline", definition.name());
        assertEquals(7, definition.stages().size());
        // Parallel branch + sync point are declared in the graph.
        assertEquals(List.of("review", "design-docs"),
                definition.stage("release-readiness").dependsOn());
    }

    @Test
    void transitiveDependentsSupportReplanning() {
        PipelineDefinition definition = PipelineDefinitionLoader.load("pipeline-definition.yml");
        Set<String> downstream = definition.transitiveDependents("design");
        assertEquals(Set.of("design-docs", "implementation", "testing", "review",
                "release-readiness"), downstream);
    }

    @Test
    void cyclesAreRejected() {
        List<StageDefinition> cyclic = List.of(
                new StageDefinition("a", "agent", List.of("b"), List.of()),
                new StageDefinition("b", "agent", List.of("a"), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new PipelineDefinition("bad", cyclic));
    }

    @Test
    void unknownDependenciesAreRejected() {
        List<StageDefinition> dangling = List.of(
                new StageDefinition("a", "agent", List.of("ghost"), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new PipelineDefinition("bad", dangling));
    }

    @Test
    void gateBindingsParseTypeAndParam() {
        assertEquals(new GateBinding("schema", "design-spec"),
                GateBinding.parse("schema:design-spec"));
        assertEquals(new GateBinding("policy", null), GateBinding.parse("policy"));
        assertTrue(GateBinding.parse("approval:major-findings").displayName()
                .contains("major-findings"));
    }
}
