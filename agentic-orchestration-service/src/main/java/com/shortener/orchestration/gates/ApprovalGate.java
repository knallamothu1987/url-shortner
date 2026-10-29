package com.shortener.orchestration.gates;

import com.fasterxml.jackson.databind.JsonNode;
import com.shortener.orchestration.dag.GateBinding;
import com.shortener.orchestration.domain.ApprovalRequest;
import com.shortener.orchestration.domain.ApprovalStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Conditional human-in-the-loop checkpoint. The gate first decides whether the condition
 * that warrants human oversight is present; if not it passes silently (no approval
 * fatigue). If oversight is required, a PENDING {@link ApprovalRequest} is created and the
 * pipeline pauses until a human decides.
 *
 * Conditions:
 * - clarification: requirement-spec contains BLOCKER ambiguities
 * - schema-migration: design-spec declares schemaChanges=true
 * - major-findings: review-report contains unresolved MAJOR/BLOCKER findings
 */
@Component
public class ApprovalGate implements GateEvaluator {

    @Override
    public String type() {
        return "approval";
    }

    @Override
    public GateResult evaluate(GateBinding binding, GateContext context) {
        String condition = binding.param();
        Optional<String> reason = approvalReason(condition, context);
        if (reason.isEmpty()) {
            return GateResult.pass("Approval '%s' not required — condition absent".formatted(condition));
        }

        Optional<ApprovalRequest> existing = context.store().findApprovalForGate(
                context.run().getId(), context.stageDefinition().name(), condition);
        if (existing.isPresent()) {
            ApprovalStatus status = existing.get().getStatus();
            if (status == ApprovalStatus.APPROVED) {
                return GateResult.pass("Approval '%s' granted by %s"
                        .formatted(condition, existing.get().getDecidedBy()));
            }
            if (status == ApprovalStatus.REJECTED) {
                return GateResult.fail("Approval '%s' rejected: %s"
                        .formatted(condition, existing.get().getNotes()));
            }
            return GateResult.pendingApproval("Approval '%s' awaiting human decision".formatted(condition));
        }

        context.store().saveApproval(new ApprovalRequest(
                context.run().getId(), context.stageDefinition().name(), condition, reason.get()));
        return GateResult.pendingApproval("Approval '%s' requested: %s".formatted(condition, reason.get()));
    }

    private Optional<String> approvalReason(String condition, GateContext context) {
        var runId = context.run().getId();
        return switch (condition) {
            case "clarification" -> context.context().latestJson(runId, "requirement-spec")
                    .flatMap(spec -> {
                        long blockers = count(spec.path("ambiguities"),
                                ambiguity -> "BLOCKER".equals(ambiguity.path("severity").asText()));
                        return blockers > 0
                                ? Optional.of(blockers + " BLOCKER ambiguity(ies) need human clarification")
                                : Optional.empty();
                    });
            case "schema-migration" -> context.context().latestJson(runId, "design-spec")
                    .flatMap(spec -> spec.path("schemaChanges").asBoolean(false)
                            ? Optional.of("Design introduces database schema changes (high-impact)")
                            : Optional.empty());
            case "major-findings" -> context.context().latestJson(runId, "review-report")
                    .flatMap(report -> {
                        long unresolved = count(report.path("findings"), finding ->
                                !finding.path("resolved").asBoolean(false)
                                        && ("MAJOR".equals(finding.path("severity").asText())
                                        || "BLOCKER".equals(finding.path("severity").asText())));
                        return unresolved > 0
                                ? Optional.of(unresolved + " unresolved MAJOR/BLOCKER review finding(s)")
                                : Optional.empty();
                    });
            default -> Optional.of("Approval condition '%s' requires explicit human decision"
                    .formatted(condition));
        };
    }

    private long count(JsonNode array, java.util.function.Predicate<JsonNode> predicate) {
        if (!array.isArray()) {
            return 0;
        }
        long matches = 0;
        for (JsonNode element : array) {
            if (predicate.test(element)) {
                matches++;
            }
        }
        return matches;
    }
}
