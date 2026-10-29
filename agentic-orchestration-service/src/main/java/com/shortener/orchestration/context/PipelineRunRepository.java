package com.shortener.orchestration.context;

import com.shortener.orchestration.domain.PipelineRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PipelineRunRepository extends JpaRepository<PipelineRun, UUID> {
}
