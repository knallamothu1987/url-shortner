package com.shortener.orchestration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable, versioned pipeline artifact. New versions are appended, never overwritten,
 * so the full decision lineage can be replayed.
 */
@Entity
@Table(name = "context_artifacts")
public class ContextArtifact {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "stage_name", nullable = false, length = 100)
    private String stageName;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private String content;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ContextArtifact() {
    }

    public ContextArtifact(UUID runId, String stageName, String name, int version,
                           String content, String createdBy) {
        this.id = UUID.randomUUID();
        this.runId = runId;
        this.stageName = stageName;
        this.name = name;
        this.version = version;
        this.content = content;
        this.createdBy = createdBy;
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

    public String getName() {
        return name;
    }

    public int getVersion() {
        return version;
    }

    public String getContent() {
        return content;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
