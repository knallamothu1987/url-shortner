package com.shortener.orchestration.context;

import com.shortener.orchestration.domain.ApprovalRequest;
import com.shortener.orchestration.domain.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

    List<ApprovalRequest> findByRunId(UUID runId);

    Optional<ApprovalRequest> findFirstByRunIdAndStageNameAndGateNameOrderByRequestedAtDesc(
            UUID runId, String stageName, String gateName);

    List<ApprovalRequest> findByStatus(ApprovalStatus status);
}
