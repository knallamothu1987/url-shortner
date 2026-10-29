package com.shortener.orchestration.api;

import com.shortener.orchestration.context.PipelineStore;
import com.shortener.orchestration.domain.ApprovalRequest;
import com.shortener.orchestration.domain.ApprovalStatus;
import com.shortener.orchestration.orchestrator.OrchestratorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class ApprovalController {

    private final PipelineStore store;
    private final OrchestratorService orchestrator;

    public ApprovalController(PipelineStore store, OrchestratorService orchestrator) {
        this.store = store;
        this.orchestrator = orchestrator;
    }

    @GetMapping("/api/v1/approvals")
    public List<Map<String, Object>> pending(@RequestParam(required = false) UUID runId) {
        List<ApprovalRequest> approvals = runId != null
                ? store.findApprovals(runId)
                : store.findApprovalsByStatus(ApprovalStatus.PENDING);
        return approvals.stream().map(this::view).toList();
    }

    @PostMapping("/api/v1/approvals/{approvalId}/decision")
    public Map<String, Object> decide(@PathVariable UUID approvalId,
                                      @Valid @RequestBody ApprovalDecisionRequest request) {
        orchestrator.approve(approvalId, request.approved(),
                request.notes(),
                request.decidedBy() == null ? "operator" : request.decidedBy());
        ApprovalRequest approval = store.findApproval(approvalId).orElseThrow();
        return view(approval);
    }

    private Map<String, Object> view(ApprovalRequest approval) {
        java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", approval.getId());
        map.put("runId", approval.getRunId());
        map.put("stage", approval.getStageName());
        map.put("gate", approval.getGateName());
        map.put("reason", approval.getReason());
        map.put("status", approval.getStatus().name());
        map.put("notes", approval.getNotes());
        map.put("decidedBy", approval.getDecidedBy());
        map.put("requestedAt", approval.getRequestedAt());
        Instant decidedAt = approval.getDecidedAt();
        map.put("decidedAt", decidedAt);
        return map;
    }
}
