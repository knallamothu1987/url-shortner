package com.shortener.orchestration.agents;

/** Failure during agent execution. Recoverable failures are retried with backoff. */
public class AgentExecutionException extends Exception {

    private final boolean recoverable;

    public AgentExecutionException(String message, boolean recoverable) {
        super(message);
        this.recoverable = recoverable;
    }

    public AgentExecutionException(String message, boolean recoverable, Throwable cause) {
        super(message, cause);
        this.recoverable = recoverable;
    }

    public boolean isRecoverable() {
        return recoverable;
    }
}
