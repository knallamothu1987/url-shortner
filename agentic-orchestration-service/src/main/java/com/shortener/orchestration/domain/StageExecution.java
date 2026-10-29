package com.shortener.orchestration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stage_executions")
public class StageExecution {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "stage_name", nullable = false, length = 100)
    private String stageName;

    @Column(name = "agent_name", nullable = false, length = 100)
    private String agentName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StageStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "fallback_used", nullable = false)
    private boolean fallbackUsed;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected StageExecution() {
    }

    public StageExecution(UUID runId, String stageName, String agentName) {
        this.id = UUID.randomUUID();
        this.runId = runId;
        this.stageName = stageName;
        this.agentName = agentName;
        this.status = StageStatus.PENDING;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRunId() {
        return runId;
    }

    public String getStageName() {
        return stageName;
    }

    public String getAgentName() {
        return agentName;
    }

    public StageStatus getStatus() {
        return status;
    }

    public void setStatus(StageStatus status) {
        this.status = status;
        if (status == StageStatus.RUNNING && startedAt == null) {
            startedAt = Instant.now();
        }
        if (status == StageStatus.COMPLETED || status == StageStatus.FAILED
                || status == StageStatus.ROLLED_BACK) {
            finishedAt = Instant.now();
        }
    }

    /** Reset for dynamic re-planning: the stage will execute again. */
    public void resetForReplan() {
        this.status = StageStatus.PENDING;
        this.attempts = 0;
        this.fallbackUsed = false;
        this.lastError = null;
        this.startedAt = null;
        this.finishedAt = null;
    }

    public int getAttempts() {
        return attempts;
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public void markFallbackUsed() {
        this.fallbackUsed = true;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
