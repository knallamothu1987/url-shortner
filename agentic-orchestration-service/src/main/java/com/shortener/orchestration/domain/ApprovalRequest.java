package com.shortener.orchestration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A human-in-the-loop approval checkpoint raised by an {@code ApprovalGate}. */
@Entity
@Table(name = "approval_requests")
public class ApprovalRequest {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "stage_name", nullable = false, length = 100)
    private String stageName;

    @Column(name = "gate_name", nullable = false, length = 100)
    private String gateName;

    @Column(nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStatus status;

    @Column
    private String notes;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected ApprovalRequest() {
    }

    public ApprovalRequest(UUID runId, String stageName, String gateName, String reason) {
        this.id = UUID.randomUUID();
        this.runId = runId;
        this.stageName = stageName;
        this.gateName = gateName;
        this.reason = reason;
        this.status = ApprovalStatus.PENDING;
        this.requestedAt = Instant.now();
    }

    public void decide(boolean approved, String notes, String decidedBy) {
        this.status = approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED;
        this.notes = notes;
        this.decidedBy = decidedBy;
        this.decidedAt = Instant.now();
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

    public String getGateName() {
        return gateName;
    }

    public String getReason() {
        return reason;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
