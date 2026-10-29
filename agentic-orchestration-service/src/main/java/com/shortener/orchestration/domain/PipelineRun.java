package com.shortener.orchestration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pipeline_runs")
public class PipelineRun {

    @Id
    private UUID id;

    @Column(name = "requirement_text", nullable = false)
    private String requirementText;

    @Enumerated(EnumType.STRING)
    @Column(name = "scenario_type", nullable = false, length = 20)
    private ScenarioType scenarioType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PipelineStatus status;

    @Column(name = "safe_stop_requested", nullable = false)
    private boolean safeStopRequested;

    @Column(name = "replan_count", nullable = false)
    private int replanCount;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected PipelineRun() {
    }

    public PipelineRun(String requirementText, ScenarioType scenarioType) {
        this.id = UUID.randomUUID();
        this.requirementText = requirementText;
        this.scenarioType = scenarioType;
        this.status = PipelineStatus.CREATED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getRequirementText() {
        return requirementText;
    }

    public ScenarioType getScenarioType() {
        return scenarioType;
    }

    public PipelineStatus getStatus() {
        return status;
    }

    public void setStatus(PipelineStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
        if (status.isTerminal()) {
            this.completedAt = this.updatedAt;
        }
    }

    public boolean isSafeStopRequested() {
        return safeStopRequested;
    }

    public void requestSafeStop() {
        this.safeStopRequested = true;
        this.updatedAt = Instant.now();
    }

    public int getReplanCount() {
        return replanCount;
    }

    public void incrementReplanCount() {
        this.replanCount++;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
