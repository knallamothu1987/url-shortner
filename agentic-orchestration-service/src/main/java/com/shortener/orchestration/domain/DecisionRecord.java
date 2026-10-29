package com.shortener.orchestration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Append-only audit record of every significant orchestration decision. */
@Entity
@Table(name = "decision_records")
public class DecisionRecord {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "stage_name", length = 100)
    private String stageName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DecisionActor actor;

    @Column(name = "decision_type", nullable = false, length = 50)
    private String decisionType;

    @Column(nullable = false)
    private String rationale;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DecisionRecord() {
    }

    public DecisionRecord(UUID runId, String stageName, DecisionActor actor,
                          String decisionType, String rationale) {
        this.id = UUID.randomUUID();
        this.runId = runId;
        this.stageName = stageName;
        this.actor = actor;
        this.decisionType = decisionType;
        this.rationale = rationale;
        this.createdAt = Instant.now();
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

    public DecisionActor getActor() {
        return actor;
    }

    public String getDecisionType() {
        return decisionType;
    }

    public String getRationale() {
        return rationale;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
