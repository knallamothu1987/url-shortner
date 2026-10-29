package com.shortener.orchestration.context;

import com.shortener.orchestration.domain.DecisionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DecisionRecordRepository extends JpaRepository<DecisionRecord, UUID> {

    List<DecisionRecord> findByRunIdOrderByCreatedAtAsc(UUID runId);
}
