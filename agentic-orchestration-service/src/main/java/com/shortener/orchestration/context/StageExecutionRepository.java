package com.shortener.orchestration.context;

import com.shortener.orchestration.domain.StageExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StageExecutionRepository extends JpaRepository<StageExecution, UUID> {

    List<StageExecution> findByRunId(UUID runId);
}
