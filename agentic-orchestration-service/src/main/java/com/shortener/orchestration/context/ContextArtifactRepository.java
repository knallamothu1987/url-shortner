package com.shortener.orchestration.context;

import com.shortener.orchestration.domain.ContextArtifact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContextArtifactRepository extends JpaRepository<ContextArtifact, UUID> {

    List<ContextArtifact> findByRunIdOrderByCreatedAtAsc(UUID runId);

    Optional<ContextArtifact> findFirstByRunIdAndNameOrderByVersionDesc(UUID runId, String name);
}
