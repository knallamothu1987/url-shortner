package com.shortener.orchestration.agents;

import java.util.Optional;

/**
 * A specialized SDLC worker agent. The Orchestrator (supervisor) owns sequencing, state,
 * retries, and gates; agents only transform inputs from the shared context into artifacts.
 */
public interface Agent {

    /** Registry key referenced from the DAG definition ("requirement", "design", ...). */
    String name();

    AgentResult execute(AgentContext context) throws AgentExecutionException;

    /** Degraded-mode output when all retries are exhausted. Empty = no fallback, roll back. */
    default Optional<AgentResult> fallback(AgentContext context) {
        return Optional.empty();
    }

    /** Undo side effects (e.g. delete generated files). Called on rollback. */
    default void rollback(AgentContext context) {
    }
}
